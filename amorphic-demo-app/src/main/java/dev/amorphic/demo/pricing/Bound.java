package dev.amorphic.demo.pricing;

import dev.amorphic.access.Invoker;
import dev.amorphic.envelope.InstanceRef;

/**
 * A role made reachable: how to invoke it, and who the handshake found there (spec 03, section 6). {@code handle} is
 * the binding URL, or null for a local binding. {@code instance} is null only where nothing was checked (tests).
 */
public record Bound(Invoker invoker, String handle, InstanceRef instance, boolean undeclared) {

    /** A binding nobody has checked: for tests that fake the service. */
    public static Bound unchecked(Invoker invoker, String handle) {
        return new Bound(invoker, handle, null, false);
    }
}
