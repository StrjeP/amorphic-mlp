package dev.amorphic.access;

import dev.amorphic.addressing.Address;
import dev.amorphic.envelope.Envelope;

/**
 * The one operation of uniform access (spec 02, section 6): send a request envelope to an address and get a reply
 * envelope back. The caller says what it wants; the binding behind the invoker decides how it is reached.
 *
 * <p>A request that can't be done comes back as an error reply ({@link Envelope#isError()}). A binding that can't
 * reach what it binds to throws {@link AccessException}.
 */
@FunctionalInterface
public interface Invoker {
    Reply invoke(Address address, Envelope request);
}
