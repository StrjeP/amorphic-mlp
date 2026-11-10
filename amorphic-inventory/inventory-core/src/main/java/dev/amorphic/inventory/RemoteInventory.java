package dev.amorphic.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;

/**
 * The inventory as a client of a separate inventory service ({@code GET {url}/inventory}). The
 * deadline is handed on, so the service observes within the caller's time. Failures are results.
 */
public final class RemoteInventory implements Inventory {

    private final String url;

    public RemoteInventory(String url) {
        this.url = Http.base(url);
    }

    public String url() {
        return url;
    }

    @Override
    public Result observe(Duration timeout) {
        Http.Response r;
        try {
            r = Http.get(url + "/inventory?timeoutMs=" + Http.budgetMillis(timeout), timeout);
        } catch (Exception e) {
            return new Unavailable("inventory at " + url + ": " + Http.why(e, timeout));
        }
        try {
            if (r.status() == 200) {
                return new Observed(Json.MAPPER.readValue(r.body(), InventorySnapshot.class));
            }
            JsonNode body = Json.MAPPER.readTree(r.body());
            String error = body.hasNonNull("error") ? body.get("error").asText() : "HTTP " + r.status();
            return new Unavailable("inventory at " + url + ": " + error);
        } catch (Exception e) {
            return new Unavailable("inventory at " + url + " answered HTTP " + r.status()
                    + " with something that isn't an inventory: " + e.getMessage());
        }
    }
}
