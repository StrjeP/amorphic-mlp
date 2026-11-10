package dev.amorphic.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RemoteInventoryTest {

    static final Duration T = Duration.ofSeconds(2);

    static InventorySnapshot snapshot() {
        return new InventorySnapshot(Instant.parse("2026-11-10T12:00:00Z"), 3, "stage-2.yaml", false,
                List.of("only 5 evidence records"), 100,
                List.of(new InventorySnapshot.Placement("pricing-calc", "champion", "pkg:a@1.0?lang=python", "remote",
                        "http://py:8000", null, new HealthCheck(HealthCheck.Status.UP, "http://py:8000/health", 2L, "200"))),
                new EvidenceView(3, List.of(new EvidenceView.Served("pkg:a@1.0?lang=python", "remote", 5)),
                        List.of(new EvidenceView.Comparison("pkg:a", "pkg:b", 5, 5, 0, 5))),
                List.of(new InventorySnapshot.CatalogueEntry("pricing-calc", "capability", "pkg:a@1.0?lang=python",
                        List.of("remote"), null, null, List.of("champion"), null)),
                List.of());
    }

    @Test
    void readsTheSnapshotTheServiceSendsAndHandsOnTheDeadline() throws Exception {
        String json = Json.MAPPER.writeValueAsString(snapshot());
        try (Stub s = new Stub("/inventory", p -> new Stub.Reply(200, json))) {
            Inventory.Result r = new RemoteInventory(s.url + "/").observe(T);
            assertThat(r).isEqualTo(new Inventory.Observed(snapshot()));
            assertThat(s.lastQuery).isEqualTo("timeoutMs=1500");
        }
    }

    @Test
    void anUnavailableServiceSaysWhy() throws Exception {
        try (Stub s = new Stub("/inventory", p -> new Stub.Reply(503, "{\"error\":\"cannot read evidence\"}"))) {
            assertThat(new RemoteInventory(s.url).observe(T))
                    .isEqualTo(new Inventory.Unavailable("inventory at " + s.url + ": cannot read evidence"));
        }
    }

    @Test
    void anUnreachableServiceIsAResultNotAnException() throws Exception {
        String url;
        try (Stub s = new Stub("/inventory", p -> new Stub.Reply(200, "{}"))) {
            url = s.url;
        }
        assertThat(new RemoteInventory(url).observe(T)).isInstanceOfSatisfying(Inventory.Unavailable.class,
                u -> assertThat(u.reason()).startsWith("inventory at " + url + ": unreachable"));
    }
}
