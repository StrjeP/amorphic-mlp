package dev.amorphic.topology;

import dev.amorphic.addressing.Address;
import java.time.Instant;
import java.util.List;

/**
 * One call (spec 03, section 4): its request envelope's id, what caused it, the routing generation in force, the
 * topology it ran on, and when. This is how causation links to the topology: the record names the envelope.
 */
public record CallRecord(String envelope, List<String> causedBy, long generation, Address topology, Instant at) {

    public CallRecord {
        causedBy = causedBy == null ? List.of() : List.copyOf(causedBy);
    }
}
