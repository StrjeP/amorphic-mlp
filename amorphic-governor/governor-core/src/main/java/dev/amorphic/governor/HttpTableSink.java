package dev.amorphic.governor;

import com.fasterxml.jackson.databind.JsonNode;
import dev.amorphic.inventory.Http;
import dev.amorphic.inventory.Json;
import java.time.Duration;

/** Where an approved table goes when the governor runs as its own service: the app's /admin/address-table. */
public final class HttpTableSink implements TableSink {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final String target;

    public HttpTableSink(String appUrl) {
        this.target = Http.base(appUrl) + "/admin/address-table";
    }

    @Override
    public Result apply(String table, String source) {
        Http.Response r;
        try {
            r = Http.post(target, table, "application/yaml", TIMEOUT, "X-Table-Source", source);
        } catch (Exception e) {
            return new Refused(target + ": " + Http.why(e, TIMEOUT));
        }
        try {
            JsonNode body = Json.MAPPER.readTree(r.body());
            if (r.status() == 200) {
                return new Accepted(body.get("generation").asLong());
            }
            return new Refused(body.hasNonNull("error") ? body.get("error").asText() : "HTTP " + r.status());
        } catch (Exception e) {
            return new Refused(target + " answered HTTP " + r.status());
        }
    }
}
