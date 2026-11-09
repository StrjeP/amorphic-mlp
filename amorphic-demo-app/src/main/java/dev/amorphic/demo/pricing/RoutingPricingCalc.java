package dev.amorphic.demo.pricing;

import dev.amorphic.demo.evidence.EvidenceRecorder;
import java.math.BigDecimal;
import java.time.Clock;
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
 */
public class RoutingPricingCalc implements PricingCalc {

    private static final Logger LOG = LoggerFactory.getLogger(RoutingPricingCalc.class);

    record Target(Address address, String binding, PricingEndpoint endpoint) {
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

    RoutingPricingCalc(Target champion, Target shadow, Target canary, int canaryWeight, Executor background,
            DoubleSupplier random, EvidenceRecorder evidence, Clock clock, long generation) {
        this.champion = champion;
        this.shadow = shadow;
        this.canary = canary;
        this.canaryWeight = canaryWeight;
        this.background = background;
        this.random = random;
        this.evidence = evidence;
        this.clock = clock;
        this.generation = generation;
    }

    /** Resolves the capability's roles to endpoints. Throws {@link AddressTableException} if the table can't be honoured. */
    public static RoutingPricingCalc resolve(AddressTable.Capability capability,
            BiFunction<Address, AddressTable.Binding, PricingEndpoint> bindings, Executor background,
            DoubleSupplier random, EvidenceRecorder evidence, Clock clock, long generation) {
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
                evidence, clock, generation);
        LOG.info("pricing-calc resolved (generation {}): {}", generation, calc.describe());
        return calc;
    }

    private static Target target(AddressTable.Role role, BiFunction<Address, AddressTable.Binding, PricingEndpoint> bindings) {
        if (role.address() == null) {
            throw new AddressTableException("a role in the address table has no address");
        }
        Address address = Address.parse(role.address());
        return new Target(address, role.binding() == null ? null : role.binding().type(),
                bindings.apply(address, role.binding()));
    }

    String describe() {
        StringBuilder sb = new StringBuilder("champion=").append(champion.describe());
        if (shadow != null) sb.append(", shadow=").append(shadow.describe());
        if (canary != null) sb.append(", canary=").append(canary.describe()).append(" weight=").append(canaryWeight).append('%');
        return sb.toString();
    }

    @Override
    public Result convert(String requestId, BigDecimal amount, String from, String to) {
        boolean useCanary = canary != null && random.getAsDouble() * 100 < canaryWeight;
        Target served = useCanary ? canary : champion;
        Target compareWith = useCanary ? champion : shadow;
        String role = useCanary ? "canary" : "champion";

        BigDecimal value = served.endpoint().convert(amount, from, to);

        if (compareWith == null) {
            evidence.record(call(requestId, amount, from, to, served, role, value, null, null, null));
        } else {
            background.execute(() -> {
                BigDecimal other = null;
                String error = null;
                try {
                    other = compareWith.endpoint().convert(amount, from, to);
                } catch (RuntimeException e) {
                    error = e.getClass().getSimpleName() + ": " + e.getMessage();
                }
                evidence.record(call(requestId, amount, from, to, served, role, value, compareWith, other, error));
            });
        }
        return new Result(value, to, served.address().raw(), served.binding());
    }

    private EvidenceRecorder.Call call(String requestId, BigDecimal amount, String from, String to, Target served,
            String role, BigDecimal value, Target other, BigDecimal otherValue, String error) {
        return new EvidenceRecorder.Call(generation, clock.instant(), requestId, amount, from, to, served.address().raw(),
                served.binding(), role, value, other == null ? null : other.address().raw(), otherValue, error);
    }
}
