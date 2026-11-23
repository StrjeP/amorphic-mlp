package dev.amorphic.access;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.amorphic.addressing.Address;
import dev.amorphic.envelope.Aspect;
import dev.amorphic.envelope.Envelope;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * The adapter binding: reaches a service that doesn't speak the envelope (spec 02, section 7). It maps the payload
 * onto the service's API and the service's response back into a reply, so the service joins unchanged.
 *
 * <p>A 4xx response becomes an error reply, using the response's {@code error} field as the reason if it has one.
 * Anything else that isn't a 2xx is an {@link AccessException}. The adapter times the service call, so its own
 * cost can be seen ({@link Timing#adapterTime()}).
 */
public final class AdapterBinding implements Invoker {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String base;
    private final AdapterSpec spec;

    public AdapterBinding(String url, AdapterSpec spec) {
        this.base = Http.trimSlash(url);
        this.spec = spec;
    }

    @Override
    public Reply invoke(Address address, Envelope request) {
        long start = System.nanoTime();
        URI uri = URI.create(base + spec.path() + query(request.payload().body()));

        long serviceStart = System.nanoTime();
        HttpResponse<String> response = Http.send(HttpRequest.newBuilder().GET(), uri);
        Duration service = Duration.ofNanos(System.nanoTime() - serviceStart);

        Envelope reply = toReply(request, response, uri);
        return new Reply(reply, new Timing(Duration.ofNanos(System.nanoTime() - start), service));
    }

    private String query(JsonNode payload) {
        StringJoiner q = new StringJoiner("&", "?", "").setEmptyValue("");
        // Sorted, so the same request always makes the same URL.
        new TreeMap<>(spec.query()).forEach((field, param) -> {
            JsonNode value = payload.get(field);
            if (value == null || value.isNull()) {
                throw new AccessException("the payload has no '" + field + "', which the adapter sends as '" + param + "'");
            }
            q.add(encode(param) + "=" + encode(value.asText()));
        });
        return q.toString();
    }

    private Envelope toReply(Envelope request, HttpResponse<String> response, URI uri) {
        int status = response.statusCode();
        JsonNode body = parse(response.body());
        if (status >= 400 && status < 500) {
            String reason = body != null && body.hasNonNull("error") ? body.get("error").asText() : "status " + status;
            return Envelope.error(request, reason);
        }
        if (status < 200 || status >= 300) {
            throw new AccessException(uri + " answered " + status + ": " + response.body());
        }
        if (body == null || !body.isObject()) {
            throw new AccessException(uri + " did not answer with a JSON object: " + response.body());
        }
        ObjectNode payload = JSON.createObjectNode();
        for (Map.Entry<String, String> e : spec.reply().entrySet()) {
            JsonNode value = body.get(e.getKey());
            if (value == null) {
                throw new AccessException(uri + " answered without '" + e.getKey() + "': " + response.body());
            }
            payload.set(e.getValue(), value);
        }
        return Envelope.reply(request, new Aspect(spec.replySchema(), payload));
    }

    private static JsonNode parse(String text) {
        try {
            return text == null || text.isBlank() ? null : JSON.readTree(text);
        } catch (IOException e) {
            return null;
        }
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "adapter " + spec.method() + " " + base + spec.path();
    }
}
