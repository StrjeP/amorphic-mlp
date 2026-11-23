package dev.amorphic.envelope;

import com.fasterxml.jackson.databind.JsonNode;
import dev.amorphic.addressing.Address;
import java.util.Objects;

/**
 * One self-describing part of an envelope (spec 02, section 3): it names the schema its body follows, so a
 * consumer can open only the aspects it understands.
 */
public record Aspect(Address schema, JsonNode body) {
    public Aspect {
        Objects.requireNonNull(schema, "an aspect needs a schema");
        Objects.requireNonNull(body, "an aspect needs a body");
    }
}
