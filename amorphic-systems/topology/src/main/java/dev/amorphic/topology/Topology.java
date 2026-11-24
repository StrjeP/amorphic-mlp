package dev.amorphic.topology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.amorphic.addressing.Address;
import dev.amorphic.envelope.InstanceRef;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * The resolved shape of one activity (spec 03, section 3): who called, and each role with what was asked for, what
 * it resolved to, how it was reached and the instance that answered. Nothing that changes from call to call is in
 * it, not even the routing generation, so calls that ran on the same shape share one topology.
 *
 * @param name   what it served: the outcome, for example {@code quote} (spec 04, section 7)
 * @param caller the instance that made the call
 */
public record Topology(String name, InstanceRef caller, List<Role> roles) {

    /**
     * One part an instance played in one step of a plan. {@code step} is the recipe step's name (spec 04).
     * {@code handle} is the binding URL, or null for a local binding. {@code instance} is who answered, or null if
     * nothing could say; {@code undeclared} is true when it answered without being declared first (only where the
     * catalogue accepts that).
     */
    public record Role(String step, String role, Address requested, Address resolved, String binding, String handle,
            InstanceRef instance, boolean undeclared) {}

    public static final String KIND = "topology";

    private static final ObjectMapper CANONICAL = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    public Topology {
        Address.of(KIND, name); // a bad name fails here
        if (caller == null) {
            throw new IllegalArgumentException("a topology needs its caller");
        }
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("a topology needs at least one role");
        }
        roles = List.copyOf(roles);
    }

    /** A hash (SHA-256, hex) of the canonical form, so the same topology always has the same identity. */
    public String id() {
        try {
            byte[] canonical = CANONICAL.writeValueAsBytes(CANONICAL.treeToValue(json(), Object.class));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("cannot hash topology", e);
        }
    }

    /** {@code pkg:topology/<name>?hash=<id>}: addressed like anything else, with the hash as a qualifier. */
    public Address address() {
        return Address.parse("pkg:" + KIND + "/" + name + "?hash=" + id());
    }

    /** The topology's content. Its address is not part of it. */
    public ObjectNode json() {
        ObjectNode o = JsonNodeFactory.instance.objectNode();
        o.put("name", name);
        o.set("caller", caller.json());
        ArrayNode rs = o.putArray("roles");
        for (Role r : roles) {
            ObjectNode n = rs.addObject();
            n.put("step", r.step());
            n.put("role", r.role());
            n.put("requested", r.requested().toString());
            n.put("resolved", r.resolved().toString());
            n.put("binding", r.binding());
            n.put("handle", r.handle());
            if (r.instance() == null) {
                n.putNull("instance");
            } else {
                n.set("instance", r.instance().json());
            }
            n.put("undeclared", r.undeclared());
        }
        return o;
    }

    public static Topology read(JsonNode n) {
        List<Role> roles = new ArrayList<>();
        for (JsonNode r : n.path("roles")) {
            JsonNode instance = r.get("instance");
            roles.add(new Role(text(r, "step"), r.path("role").asText(), Address.parse(r.path("requested").asText()),
                    Address.parse(r.path("resolved").asText()), text(r, "binding"), text(r, "handle"),
                    instance == null || instance.isNull() ? null : InstanceRef.read(instance),
                    r.path("undeclared").asBoolean(false)));
        }
        return new Topology(n.path("name").asText(), InstanceRef.read(n.get("caller")), roles);
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
