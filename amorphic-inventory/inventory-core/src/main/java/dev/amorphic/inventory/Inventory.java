package dev.amorphic.inventory;

import java.time.Duration;

/**
 * The inventory, seen from its callers. Presumed remote: every call has a deadline, returns a
 * value (never shared mutable state), and reports failure as a result rather than an exception.
 * The same interface is served in-process or by a separate inventory service.
 */
public interface Inventory {

    /** Observes the system now. Nothing is kept between calls. */
    Result observe(Duration timeout);

    sealed interface Result permits Observed, Unavailable {}

    /** A complete observation. */
    record Observed(InventorySnapshot snapshot) implements Result {}

    /** The inventory couldn't observe the system within the deadline, or a source failed. */
    record Unavailable(String reason) implements Result {}
}
