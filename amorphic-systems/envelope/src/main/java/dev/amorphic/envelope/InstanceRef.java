package dev.amorphic.envelope;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.amorphic.addressing.Address;
import dev.amorphic.addressing.AddressException;
import dev.amorphic.addressing.Instance;
import java.util.Optional;

/**
 * An instance as written down (spec 03, section 5): its {@code identity}, the address it {@code realises}, and its
 * {@code host}'s identity, if it has one. A small JSON object, not an address syntax:
 *
 * <pre>{ "identity": "greeter-1", "realises": "pkg:amorphic/greeter@1.0?lang=java", "host": null }</pre>
 *
 * <p>It is also the body of the {@value #ASPECT} aspect a reply may carry, saying who answered.
 */
public record InstanceRef(String identity, Address realises, String host) {

    public static final Address SCHEMA = Address.parse("pkg:schema/instance@1.0");
    public static final String ASPECT = "instance";

    public InstanceRef {
        if (identity == null || identity.isBlank()) {
            throw new EnvelopeException("an instance needs an identity");
        }
        if (realises == null) {
            throw new EnvelopeException("instance " + identity + " must say which address it realises");
        }
        if (host != null && host.isBlank()) {
            host = null;
        }
    }

    public static InstanceRef of(Instance instance) {
        return new InstanceRef(instance.identity(), instance.realises(),
                instance.hostInstance().map(Instance::identity).orElse(null));
    }

    public ObjectNode json() {
        ObjectNode o = JsonNodeFactory.instance.objectNode();
        o.put("identity", identity);
        o.put("realises", realises.toString());
        if (host == null) {
            o.putNull("host");
        } else {
            o.put("host", host);
        }
        return o;
    }

    public static InstanceRef read(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new EnvelopeException("an instance is a JSON object with identity and realises");
        }
        JsonNode host = node.get("host");
        try {
            return new InstanceRef(node.path("identity").asText(null), Address.parse(node.path("realises").asText("")),
                    host == null || host.isNull() ? null : host.asText());
        } catch (AddressException e) {
            throw new EnvelopeException("instance " + node.path("identity").asText("?") + " realises an address that can't be read: "
                    + e.getMessage());
        }
    }

    /** The aspect saying this instance answered. */
    public Aspect aspect() {
        return new Aspect(SCHEMA, json());
    }

    /** Who answered, if the envelope says. */
    public static Optional<InstanceRef> from(Envelope envelope) {
        return envelope.aspect(ASPECT).map(a -> read(a.body()));
    }
}
