package dev.amorphic.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The inventory service reads the app's live table and evidence in the shapes the app serves them. */
class HttpSourcesTest {

    static final String APPLIED = """
            {"generation": 4, "appliedAt": "2026-11-10T12:00:00Z", "source": "stage-2.yaml", "routing": "...",
             "table": {"capabilities": {
               "pricing-calc": {
                 "champion": {"address": "pkg:amorphic/pricing-calc@1.0?lang=python",
                              "binding": {"type": "remote", "url": "http://py-pricing:8000"}, "weight": null},
                 "shadow": null,
                 "canary": {"address": "pkg:amorphic/pricing-calc@2.1?lang=java",
                            "binding": {"type": "remote", "url": "http://java-pricing:8080"}, "weight": 10}},
               "governor": {
                 "champion": {"address": "pkg:amorphic/governor@0.1?lang=java", "binding": {"type": "local", "url": null}}}}}}
            """;

    static final String EVIDENCE = """
            {"generation": 4,
             "served": [{"address": "pkg:amorphic/pricing-calc@1.0?lang=python", "binding": "remote", "total": 120}],
             "comparisons": [{"served": "pkg:amorphic/pricing-calc@2.1?lang=java",
                              "shadow": "pkg:amorphic/pricing-calc@1.0?lang=python",
                              "total": 12, "matches": 12, "mismatches": 0, "consecutiveMatches": 12}]}
            """;

    @Test
    void readsTheLiveTableAndEvidence() throws Exception {
        try (Stub s = new Stub("/", p -> new Stub.Reply(200, p.equals("/evidence") ? EVIDENCE : APPLIED))) {
            RoutingView table = HttpSources.routing(s.url).current();
            assertThat(table).isEqualTo(new RoutingView(4, "stage-2.yaml", List.of(
                    new RoutingView.Role("pricing-calc", "champion", "pkg:amorphic/pricing-calc@1.0?lang=python",
                            "remote", "http://py-pricing:8000", null),
                    new RoutingView.Role("pricing-calc", "canary", "pkg:amorphic/pricing-calc@2.1?lang=java",
                            "remote", "http://java-pricing:8080", 10),
                    new RoutingView.Role("governor", "champion", "pkg:amorphic/governor@0.1?lang=java", "local",
                            null, null))));
            EvidenceView ev = HttpSources.evidence(s.url).current();
            assertThat(ev.generation()).isEqualTo(4);
            assertThat(ev.servedTotal()).isEqualTo(120);
            assertThat(ev.comparisons().get(0).consecutiveMatches()).isEqualTo(12);
        }
    }

    @Test
    void aFailingAppIsAnErrorTheInventoryReports() throws Exception {
        try (Stub s = new Stub("/", p -> new Stub.Reply(500, "boom"))) {
            assertThatThrownBy(() -> HttpSources.routing(s.url).current())
                    .hasMessage(s.url + "/admin/address-table answered HTTP 500");
        }
    }
}
