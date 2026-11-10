package dev.amorphic.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The inventory's sources when it runs as its own service: the demo app's live address table
 * ({@code GET /admin/address-table}) and evidence ({@code GET /evidence}), read over HTTP.
 */
public final class HttpSources {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private HttpSources() {}

    public static Sources.Routing routing(String appUrl) {
        String target = Http.base(appUrl) + "/admin/address-table";
        return () -> {
            JsonNode applied = Json.MAPPER.readTree(read(target));
            return routingView(applied.get("generation").asLong(), applied.path("source").asText(null),
                    applied.path("table").path("capabilities"));
        };
    }

    public static Sources.Evidence evidence(String appUrl) {
        String target = Http.base(appUrl) + "/evidence";
        return () -> Json.MAPPER.readValue(read(target), EvidenceView.class);
    }

    /** Reads an address table's {@code capabilities} (as JSON) into the roles it routes. */
    public static RoutingView routingView(long generation, String source, JsonNode capabilities) {
        List<RoutingView.Role> roles = new ArrayList<>();
        for (Map.Entry<String, JsonNode> c : capabilities.properties()) {
            for (String role : List.of("champion", "shadow", "canary")) {
                JsonNode r = c.getValue().get(role);
                if (r == null || r.isNull()) {
                    continue;
                }
                JsonNode b = r.path("binding");
                roles.add(new RoutingView.Role(c.getKey(), role, r.path("address").asText(null),
                        b.path("type").asText(null), b.hasNonNull("url") ? b.get("url").asText() : null,
                        r.hasNonNull("weight") ? r.get("weight").asInt() : null));
            }
        }
        return new RoutingView(generation, source, roles);
    }

    private static String read(String target) throws Exception {
        Http.Response r;
        try {
            r = Http.get(target, TIMEOUT);
        } catch (Exception e) {
            throw new IllegalStateException(target + ": " + Http.why(e, TIMEOUT));
        }
        if (r.status() != 200) {
            throw new IllegalStateException(target + " answered HTTP " + r.status());
        }
        return r.body();
    }
}
