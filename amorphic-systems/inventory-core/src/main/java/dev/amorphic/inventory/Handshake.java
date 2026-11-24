package dev.amorphic.inventory;

import dev.amorphic.addressing.Address;
import dev.amorphic.envelope.InstanceRef;

/**
 * Checks that what answers at a handle is what the routing means (spec 03, section 6). A service reports who it is;
 * the handshake compares that with the routed address and the declarations. A mismatch is refused, naming both
 * sides, so nothing falls back silently.
 */
public final class Handshake {

    /** What answered, and whether it was declared. */
    public sealed interface Outcome {
        InstanceRef instance();
    }

    public record Confirmed(InstanceRef instance) implements Outcome {}

    /** Allowed only where the catalogue accepts undeclared instances; never given a made-up identity. */
    public record Undeclared(InstanceRef instance) implements Outcome {}

    public static final class Refused extends RuntimeException {
        Refused(String message) {
            super(message);
        }
    }

    private Handshake() {}

    /**
     * @param handle   where the routing reaches it (a binding URL)
     * @param routed   the address the routing gives that handle
     * @param reported what the service says it is
     * @throws Refused if it isn't what the routing means, or isn't declared and the catalogue doesn't accept that
     */
    public static Outcome check(String handle, Address routed, InstanceRef reported, Instances instances,
            boolean acceptUndeclared) {
        String h = Instances.trim(handle);
        if (!reported.realises().equals(routed)) {
            throw new Refused("routed to " + routed + ", but " + h + " is " + reported.identity() + ", which realises "
                    + reported.realises());
        }
        var declared = instances.identity(reported.identity());
        if (declared.isEmpty()) {
            if (acceptUndeclared) {
                return new Undeclared(reported);
            }
            String expected = instances.at(h, routed).map(d -> " (expected " + d.identity() + ")").orElse("");
            throw new Refused(h + " is " + reported.identity() + ", which isn't declared" + expected
                    + ", and the catalogue doesn't accept undeclared instances");
        }
        Instances.Declared d = declared.get();
        if (!d.realises().equals(reported.realises())) {
            throw new Refused(h + " is " + reported.identity() + ", which reports it realises " + reported.realises()
                    + ", but it is declared as realising " + d.realises());
        }
        if (d.handle() != null && !d.handle().equals(h)) {
            throw new Refused(reported.identity() + " answered at " + h + ", but it is declared at " + d.handle());
        }
        return new Confirmed(reported);
    }
}
