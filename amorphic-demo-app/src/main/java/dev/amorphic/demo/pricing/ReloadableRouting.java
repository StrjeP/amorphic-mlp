package dev.amorphic.demo.pricing;

import dev.amorphic.addressing.Address;
import dev.amorphic.composer.CompositionException;
import dev.amorphic.composer.Recipe;
import dev.amorphic.composer.Router;
import dev.amorphic.demo.evidence.EvidenceRecorder;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The routing the composer plans with (spec 04, section 5). pricing-calc is routed by the current address table,
 * which is swapped when a new table is applied, with no restart. The app's own in-process capabilities (positions,
 * sum) are routed locally and never change.
 *
 * <p>A new table is fully resolved before the swap, so a table that can't be honoured is rejected and the current
 * routing keeps serving. Resolving includes the handshake with every remote role, so a table is also refused when
 * what answers isn't what it routes to. Each applied table is a new generation, and evidence restarts with it. A
 * plan takes one snapshot when it starts, so all of it runs on one generation.
 */
public class ReloadableRouting implements Router {

    private static final Logger LOG = LoggerFactory.getLogger(ReloadableRouting.class);
    public static final String CAPABILITY = "pricing-calc";

    /** What is currently applied. */
    public record Applied(long generation, Instant appliedAt, String source, String routing, AddressTable table) {}

    private record State(Applied applied, PricingRoutes pricing) {}

    private final BiFunction<Address, AddressTable.Binding, Bound> bindings;
    private final DoubleSupplier random;
    private final EvidenceRecorder evidence;
    private final Clock clock;
    private final Function<Recipe.Step, Optional<Route>> local;
    private final AtomicReference<State> state = new AtomicReference<>();

    /**
     * @param local the app's in-process capabilities: a route for a step they serve, or empty
     */
    public ReloadableRouting(AddressTable initial, String source,
            BiFunction<Address, AddressTable.Binding, Bound> bindings, DoubleSupplier random,
            EvidenceRecorder evidence, Clock clock, Function<Recipe.Step, Optional<Route>> local) {
        this.bindings = bindings;
        this.random = random;
        this.evidence = evidence;
        this.clock = clock;
        this.local = local;
        apply(initial, source);
    }

    /**
     * Resolves {@code table} and, if it can be honoured, makes it the live routing.
     *
     * @throws AddressTableException if the table can't be honoured; nothing changes
     */
    public synchronized Applied apply(AddressTable table, String source) {
        State current = state.get();
        long next = current == null ? 1 : current.applied().generation() + 1;
        table.checkTooling();
        PricingRoutes pricing = PricingRoutes.resolve(table.capability(CAPABILITY), bindings, random);
        pricing.logResolved(next);
        Applied applied = new Applied(next, clock.instant(), source, pricing.describe(), table);
        // Reset first: plans still finishing on the old routing carry the old generation and are dropped.
        evidence.reset(next);
        state.set(new State(applied, pricing));
        if (current != null) {
            LOG.info("address table applied without restart (generation {}, from {}): {}", next, source,
                    applied.routing());
        }
        return applied;
    }

    public Applied current() {
        return state.get().applied();
    }

    @Override
    public Routing begin() {
        State s = state.get();
        return new Routing() {
            @Override
            public long generation() {
                return s.applied().generation();
            }

            @Override
            public Route route(Recipe.Step step) {
                if (CAPABILITY.equals(step.capability().name())) {
                    return s.pricing().route();
                }
                return local.apply(step).orElseThrow(() -> new CompositionException(
                        "nothing routes " + step.capability() + " (step '" + step.name() + "')"));
            }
        };
    }
}
