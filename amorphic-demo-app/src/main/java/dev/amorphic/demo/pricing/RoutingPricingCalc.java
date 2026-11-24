package dev.amorphic.demo.pricing;

import dev.amorphic.access.Invoker;
import dev.amorphic.access.Reply;
import dev.amorphic.access.Timing;
import dev.amorphic.addressing.Address;
import dev.amorphic.addressing.AddressException;
import dev.amorphic.demo.evidence.EvidenceRecorder;
import dev.amorphic.envelope.Envelope;
import dev.amorphic.envelope.InstanceRef;
import dev.amorphic.topology.Topology;
import dev.amorphic.topology.TopologyLog;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.DoubleSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side resolver and router for one capability.
 *
 * <ul>
 *   <li>champion: its answer is served.</li>
 *   <li>shadow: called in the background on every call; compared, never returned.</li>
 *   <li>canary: serves {@code weight}% of calls; on those calls the champion is called in the
 *       background as the comparison, so Java-served answers are still checked.</li>
 * </ul>
 *
 * <p>Every call records the topology it ran on (spec 03): each role with the instance that actually answered,
 * taken from the reply's {@code instance} aspect where the service speaks the envelope, and from the handshake
 * otherwise. A service swapped between calls shows up as a new topology.
 */
public class RoutingPricingCalc implements PricingCalc {

    private static final Logger LOG = LoggerFactory.getLogger(RoutingPricingCalc.class);

    record Target(Address address, String binding, Bound bound) {
        Invoker invoker() {
            return bound.invoker();
        }

        String describe() {
            return address + " (" + binding + ")";
        }
    }

    private final Target champion;
    private final Target shadow;
    private final Target canary;
    private final int canaryWeight;
    private final Executor background;
    private final DoubleSupplier random;
    private final EvidenceRecorder evidence;
    private final Clock clock;
    private final long generation;
    private final TopologyLog topologies;
    private final InstanceRef caller;

    RoutingPricingCalc(Target champion, Target shadow, Target canary, int canaryWeight, Executor background,
            DoubleSupplier random, EvidenceRecorder evidence, Clock clock, long generation, TopologyLog topologies,
            InstanceRef caller) {
        this.champion = champion;
        this.shadow = shadow;
        this.canary = canary;
        this.canaryWeight = canaryWeight;
        this.background = background;
        this.random = random;
        this.evidence = evidence;
        this.clock = clock;
        this.generation = generation;
        this.topologies = topologies;
        this.caller = caller;
    }

    /** Resolves the capability's roles to endpoints. Throws {@link AddressTableException} if the table can't be honoured. */
    public static RoutingPricingCalc resolve(AddressTable.Capability capability,
            BiFunction<Address, AddressTable.Binding, Bound> bindings, Executor background,
            DoubleSupplier random, EvidenceRecorder evidence, Clock clock, long generation, TopologyLog topologies,
            InstanceRef caller) {
        if (capability.shadow() != null && capability.canary() != null) {
            throw new AddressTableException("a capability can have a shadow or a canary, not both");
        }
        Target champion = target(capability.champion(), bindings);
        Target shadow = capability.shadow() == null ? null : target(capability.shadow(), bindings);
        Target canary = null;
        int weight = 0;
        if (capability.canary() != null) {
            Integer w = capability.canary().weight();
            if (w == null || w < 0 || w > 100) {
                throw new AddressTableException("canary weight must be 0-100, got " + w);
            }
            canary = target(capability.canary(), bindings);
            weight = w;
        }
        RoutingPricingCalc calc = new RoutingPricingCalc(champion, shadow, canary, weight, background, random,
                evidence, clock, generation, topologies, caller);
        LOG.info("pricing-calc resolved (generation {}): {}", generation, calc.describe());
        return calc;
    }

    private static Target target(AddressTable.Role role, BiFunction<Address, AddressTable.Binding, Bound> bindings) {
        if (role.address() == null) {
            throw new AddressTableException("a role in the address table has no address");
        }
        Address address;
        try {
            address = Address.parse(role.address());
        } catch (AddressException e) {
            throw new AddressTableException(e.getMessage());
        }
        return new Target(address, role.binding() == null ? null : role.binding().type(),
                bindings.apply(address, role.binding()));
    }

    String describe() {
        StringBuilder sb = new StringBuilder("champion=").append(champion.describe());
        if (shadow != null) sb.append(", shadow=").append(shadow.describe());
        if (canary != null) sb.append(", canary=").append(canary.describe()).append(" weight=").append(canaryWeight).append('%');
        return sb.toString();
    }

    /**
     * One call: a single request envelope, sent to the served target and, if there is one, to the comparison. Each
     * reply's timing goes to evidence with it.
     */
    @Override
    public Result convert(String requestId, BigDecimal amount, String from, String to) {
        boolean useCanary = canary != null && random.getAsDouble() * 100 < canaryWeight;
        Target served = useCanary ? canary : champion;
        Target compareWith = useCanary ? champion : shadow;
        String role = useCanary ? "canary" : "champion";
        String otherRole = useCanary ? "champion" : "shadow";

        Envelope request = PricingEnvelopes.request(requestId, amount, from, to);
        Reply reply = served.invoker().invoke(served.address(), request);
        BigDecimal value = PricingEnvelopes.amount(reply);
        Outcome servedOutcome = new Outcome(value, null, reply.timing(), answered(served, reply));

        if (compareWith == null) {
            String topology = record(request, served, role, servedOutcome, null, null, null);
            evidence.record(call(requestId, amount, from, to, served, role, servedOutcome, null, null, topology));
        } else {
            background.execute(() -> {
                Outcome other;
                try {
                    Reply r = compareWith.invoker().invoke(compareWith.address(), request);
                    other = new Outcome(PricingEnvelopes.amount(r), null, r.timing(), answered(compareWith, r));
                } catch (RuntimeException e) {
                    // Nothing answered, so the topology names who the handshake found there.
                    other = new Outcome(null, e.getClass().getSimpleName() + ": " + e.getMessage(), null,
                            compareWith.bound().instance());
                }
                String topology = record(request, served, role, servedOutcome, compareWith, otherRole, other);
                evidence.record(call(requestId, amount, from, to, served, role, servedOutcome, compareWith, other, topology));
            });
        }
        return new Result(value, to, served.address().toString(), served.binding());
    }

    /**
     * Who answered: what the reply says, where the service speaks the envelope, otherwise who the handshake found. A
     * reply from someone else is kept as it is, so the topology shows it.
     */
    private static InstanceRef answered(Target target, Reply reply) {
        InstanceRef expected = target.bound().instance();
        InstanceRef said = InstanceRef.from(reply.envelope()).orElse(null);
        if (said == null) {
            return expected;
        }
        if (expected != null && !said.equals(expected)) {
            LOG.warn("{} answered at {}, where the handshake found {}", said.identity(), target.bound().handle(),
                    expected.identity());
        }
        return said;
    }

    /** Writes the call and, if it is new, its topology. Returns the topology's address, or null with no log. */
    private String record(Envelope request, Target served, String role, Outcome servedOutcome, Target other,
            String otherRole, Outcome otherOutcome) {
        if (topologies == null) {
            return null;
        }
        List<Topology.Role> roles = new ArrayList<>();
        roles.add(topologyRole(role, served, servedOutcome));
        if (other != null) {
            roles.add(topologyRole(otherRole, other, otherOutcome));
        }
        Topology topology = new Topology(ReloadablePricingCalc.CAPABILITY, caller, roles);
        return topologies.record(topology, request.id(), request.causedBy(), generation, clock.instant()).toString();
    }

    private static Topology.Role topologyRole(String role, Target target, Outcome outcome) {
        Bound b = target.bound();
        // The table names exact addresses, so what was asked for is what it resolved to.
        return new Topology.Role(role, target.address(), target.address(), target.binding(), b.handle(),
                outcome.answered(), b.undeclared());
    }

    /** What one target gave back: a value or an error, and how long it took (when it answered). */
    private record Outcome(BigDecimal value, String error, Timing timing, InstanceRef answered) {}

    private EvidenceRecorder.Call call(String requestId, BigDecimal amount, String from, String to, Target served,
            String role, Outcome servedOutcome, Target other, Outcome otherOutcome, String topology) {
        return new EvidenceRecorder.Call(generation, clock.instant(), requestId, amount, from, to, served.address().toString(),
                served.binding(), role, servedOutcome.value(), other == null ? null : other.address().toString(),
                otherOutcome == null ? null : otherOutcome.value(), otherOutcome == null ? null : otherOutcome.error(),
                EvidenceRecorder.Timings.of(servedOutcome.timing()),
                otherOutcome == null ? null : EvidenceRecorder.Timings.of(otherOutcome.timing()), topology);
    }
}
