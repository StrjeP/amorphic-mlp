package dev.amorphic.access;

import dev.amorphic.envelope.Envelope;

/** Something in this process that answers request envelopes: what a {@link LocalBinding} hands them to. */
@FunctionalInterface
public interface Handler {
    Envelope handle(Envelope request);
}
