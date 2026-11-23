package dev.amorphic.access;

import dev.amorphic.addressing.Address;
import java.util.Map;
import java.util.Objects;

/**
 * How an {@link AdapterBinding} maps an envelope onto a plain REST API and back.
 *
 * @param method the HTTP method; pass 1 supports GET
 * @param path the path on the service, e.g. {@code /greet}
 * @param query each payload field to send, and the query parameter it becomes
 * @param reply each response field to keep, and the payload field it becomes
 * @param replySchema the schema of the reply payload built from those fields
 */
public record AdapterSpec(String method, String path, Map<String, String> query, Map<String, String> reply,
        Address replySchema) {

    public AdapterSpec {
        if (!"GET".equals(method)) {
            throw new AccessException("an adapter supports GET only for now, not " + method);
        }
        if (path == null || !path.startsWith("/")) {
            throw new AccessException("an adapter needs a path starting with /, got " + path);
        }
        Objects.requireNonNull(replySchema, "an adapter needs the schema of the reply it builds");
        query = Map.copyOf(query == null ? Map.of() : query);
        if (reply == null || reply.isEmpty()) {
            throw new AccessException("an adapter needs at least one reply field");
        }
        reply = Map.copyOf(reply);
    }
}
