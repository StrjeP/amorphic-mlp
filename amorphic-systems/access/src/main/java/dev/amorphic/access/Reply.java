package dev.amorphic.access;

import dev.amorphic.envelope.Envelope;

/** What an invoke gives back: the reply envelope, and how long it took. */
public record Reply(Envelope envelope, Timing timing) {}
