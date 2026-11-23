package dev.amorphic.envelope;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.amorphic.addressing.Address;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A message (spec 02): a header, the {@code id} and the {@code purpose}, plus named aspects. Nothing else sits at
 * the top level.
 *
 * <p>The purpose is the address of the envelope's own specification, which says which aspects must be present.
 * There are two in pass 1, {@link #REQUEST} and {@link #REPLY}; both need a {@value #PAYLOAD} and a
 * {@value #CAUSATION} aspect. Any other aspects are kept and can be ignored.
 */
public record Envelope(String id, Address purpose, Map<String, Aspect> aspects) {

    public static final Address REQUEST = Address.parse("pkg:envelope/request@1.0");
    public static final Address REPLY = Address.parse("pkg:envelope/reply@1.0");
    public static final Address CAUSATION_SCHEMA = Address.parse("pkg:schema/causation@1.0");
    public static final Address ERROR_SCHEMA = Address.parse("pkg:schema/error@1.0");

    public static final String PAYLOAD = "payload";
    public static final String CAUSATION = "causation";

    private static final Address ANY_ERROR = Address.of("schema", "error").at("*");

    public Envelope {
        if (id == null || id.isBlank()) {
            throw new EnvelopeException("an envelope needs an id");
        }
        if (purpose == null) {
            throw new EnvelopeException("envelope " + id + " has no purpose");
        }
        aspects = Collections.unmodifiableMap(new LinkedHashMap<>(aspects == null ? Map.of() : aspects));
        if (purpose.equals(REQUEST) || purpose.equals(REPLY)) {
            for (String required : List.of(PAYLOAD, CAUSATION)) {
                if (!aspects.containsKey(required)) {
                    throw new EnvelopeException("envelope " + id + " is a " + purpose + ", which needs a '"
                            + required + "' aspect; it has " + (aspects.isEmpty() ? "none" : aspects.keySet()));
                }
            }
        }
    }

    /** A request carrying {@code payload}, caused by {@code causedBy} (for example the id of an incoming request). */
    public static Envelope request(Aspect payload, String... causedBy) {
        return new Envelope(newId(), REQUEST, standard(payload, List.of(causedBy)));
    }

    /** The reply to {@code request}: its causation names the request. */
    public static Envelope reply(Envelope request, Aspect payload) {
        return new Envelope(newId(), REPLY, standard(payload, List.of(request.id())));
    }

    /** A reply saying the request could not be done, and why. */
    public static Envelope error(Envelope request, String reason) {
        return reply(request, errorAspect(reason));
    }

    /** An error payload under the framework's own error schema. */
    public static Aspect errorAspect(String reason) {
        ObjectNode body = JsonNodeFactory.instance.objectNode().put("error", reason == null ? "unknown error" : reason);
        return new Aspect(ERROR_SCHEMA, body);
    }

    public static String newId() {
        return "e-" + UUID.randomUUID();
    }

    public Optional<Aspect> aspect(String name) {
        return Optional.ofNullable(aspects.get(name));
    }

    /** The payload aspect. Every request and reply has one. */
    public Aspect payload() {
        return aspect(PAYLOAD).orElseThrow(() -> new EnvelopeException("envelope " + id + " has no payload"));
    }

    /** The ids this envelope follows from, from its causation aspect; empty if it has none. */
    public List<String> causedBy() {
        List<String> ids = new ArrayList<>();
        aspect(CAUSATION).map(a -> a.body().path("causedBy")).ifPresent(n -> n.forEach(id -> ids.add(id.asText())));
        return List.copyOf(ids);
    }

    /** True if the payload is an error, under any version of the error schema. */
    public boolean isError() {
        return aspect(PAYLOAD).map(a -> ANY_ERROR.matches(a.schema())).orElse(false);
    }

    /** The reason, if the payload is an error. */
    public Optional<String> errorReason() {
        return isError() ? Optional.of(payload().body().path("error").asText()) : Optional.empty();
    }

    private static Map<String, Aspect> standard(Aspect payload, List<String> causedBy) {
        Map<String, Aspect> aspects = new LinkedHashMap<>();
        aspects.put(PAYLOAD, payload);
        aspects.put(CAUSATION, causation(causedBy));
        return aspects;
    }

    private static Aspect causation(List<String> ids) {
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        var array = body.putArray("causedBy");
        ids.forEach(array::add);
        return new Aspect(CAUSATION_SCHEMA, body);
    }

    /** Convenience for building a payload body. */
    public static ObjectNode body() {
        return JsonNodeFactory.instance.objectNode();
    }
}
