package dev.amorphic.demo.pricing;

import dev.amorphic.demo.evidence.EvidenceRecorder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.DoubleSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link PricingCalc} the app calls. It delegates to the routing built from the current
 * address table, and swaps in a new routing when a new table is applied, with no restart.
 *
 * <p>A new table is fully resolved before the swap, so a table that can't be honoured is
 * rejected and the current routing keeps serving. Each applied table is a new generation, and
 * evidence restarts with it.
 */
public class ReloadablePricingCalc implements PricingCalc {

    private static final Logger LOG = LoggerFactory.getLogger(ReloadablePricingCalc.class);
    static final String CAPABILITY = "pricing-calc";

    /** What is currently applied. */
    public record Applied(long generation, Instant appliedAt, String source, String routing, AddressTable table) {}

    private record State(Applied applied, RoutingPricingCalc routing) {}

    private final BiFunction<Address, AddressTable.Binding, PricingEndpoint> bindings;
    private final Executor background;
    private final DoubleSupplier random;
    private final EvidenceRecorder evidence;
    private final Clock clock;
    private final AtomicReference<State> state = new AtomicReference<>();

    public ReloadablePricingCalc(AddressTable initial, String source,
            BiFunction<Address, AddressTable.Binding, PricingEndpoint> bindings, Executor background,
            DoubleSupplier random, EvidenceRecorder evidence, Clock clock) {
        this.bindings = bindings;
        this.background = background;
        this.random = random;
        this.evidence = evidence;
        this.clock = clock;
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
        RoutingPricingCalc routing = RoutingPricingCalc.resolve(table.capability(CAPABILITY), bindings, background,
                random, evidence, clock, next);
        Applied applied = new Applied(next, clock.instant(), source, routing.describe(), table);
        // Reset first: calls still finishing on the old routing carry the old generation and are dropped.
        evidence.reset(next);
        state.set(new State(applied, routing));
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
    public Result convert(String requestId, BigDecimal amount, String from, String to) {
        return state.get().routing().convert(requestId, amount, from, to);
    }
}
