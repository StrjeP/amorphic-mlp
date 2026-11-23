package dev.amorphic.access;

import java.time.Duration;
import java.util.Optional;

/**
 * How long one invoke took. {@code service} is set only by the adapter: the time spent in the wrapped service, so
 * the adapter's own cost is {@code total - service}. Timings stay with the binding and go to evidence; they are not
 * part of the envelope (spec 02, section 7).
 */
public record Timing(Duration total, Duration service) {

    public static Timing of(long startNanos) {
        return new Timing(Duration.ofNanos(System.nanoTime() - startNanos), null);
    }

    public Optional<Duration> serviceTime() {
        return Optional.ofNullable(service);
    }

    /** The adapter's own cost: total minus the service's time. Empty unless the binding is an adapter. */
    public Optional<Duration> adapterTime() {
        return serviceTime().map(total::minus);
    }
}
