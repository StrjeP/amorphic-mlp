package dev.amorphic.envelope;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.amorphic.addressing.Address;
import dev.amorphic.addressing.AddressException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The JSON form of an envelope (spec 02, section 5). Addresses travel as canonical text and are parsed here, at
 * the edge.
 */
public final class EnvelopeJson {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> TOP_LEVEL = Set.of("id", "purpose", "aspects");

    private EnvelopeJson() {}

    public static String write(Envelope e) {
        try {
            return JSON.writeValueAsString(tree(e));
        } catch (JsonProcessingException ex) {
            throw new EnvelopeException("cannot write envelope " + e.id() + ": " + ex.getOriginalMessage(), ex);
        }
    }

    public static ObjectNode tree(Envelope e) {
        ObjectNode root = JSON.createObjectNode();
        root.put("id", e.id());
        root.put("purpose", e.purpose().toString());
        ObjectNode aspects = root.putObject("aspects");
        e.aspects().forEach((name, a) -> {
            ObjectNode node = aspects.putObject(name);
            node.put("schema", a.schema().toString());
            node.set("body", a.body());
        });
        return root;
    }

    public static Envelope read(String text) {
        JsonNode root;
        try {
            root = JSON.readTree(text);
        } catch (JsonProcessingException ex) {
            throw new EnvelopeException("not a JSON envelope: " + ex.getOriginalMessage(), ex);
        }
        return read(root);
    }

    public static Envelope read(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new EnvelopeException("an envelope is a JSON object with id, purpose and aspects");
        }
        for (Map.Entry<String, JsonNode> top : root.properties()) {
            String field = top.getKey();
            if (!TOP_LEVEL.contains(field)) {
                throw new EnvelopeException("an envelope has only id, purpose and aspects at the top level; found '"
                        + field + "' (put it in an aspect)");
            }
        }
        String id = root.path("id").asText(null);
        Address purpose = address(root.path("purpose"), "purpose of envelope " + id);
        Map<String, Aspect> aspects = new LinkedHashMap<>();
        JsonNode node = root.path("aspects");
        if (!node.isMissingNode() && !node.isObject()) {
            throw new EnvelopeException("the aspects of envelope " + id + " must be an object, keyed by name");
        }
        node.properties().forEach(f -> {
            JsonNode a = f.getValue();
            JsonNode body = a.get("body");
            if (body == null) {
                throw new EnvelopeException("aspect '" + f.getKey() + "' of envelope " + id + " has no body");
            }
            aspects.put(f.getKey(), new Aspect(address(a.path("schema"), "schema of aspect '" + f.getKey() + "'"), body));
        });
        return new Envelope(id, purpose, aspects);
    }

    private static Address address(JsonNode n, String what) {
        if (!n.isTextual()) {
            throw new EnvelopeException("the " + what + " is missing (expected an address)");
        }
        try {
            return Address.parse(n.asText());
        } catch (AddressException e) {
            throw new EnvelopeException("the " + what + " is not readable: " + e.getMessage(), e);
        }
    }
}
