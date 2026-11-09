package dev.amorphic.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ObservedInventoryTest {

    static final String PY = "pkg:amorphic/pricing-calc@1.0?lang=python";
    static final String J20 = "pkg:amorphic/pricing-calc@2.0?lang=java";
    static final String J21 = "pkg:amorphic/pricing-calc@2.1?lang=java";
    static final String PY_URL = "http://py-pricing:8000";
    static final String JAVA_URL = "http://java-pricing:8080";

    static final Catalogue CATALOGUE = Catalogue.load(Path.of("../../amorphic-demo-env/catalogue.yaml"));

    static RoutingView.Role role(String role, String address, String type, String url) {
        return new RoutingView.Role("pricing-calc", role, address, type, url, null);
    }

    static EvidenceView evidence(long generation, long served) {
        return new EvidenceView(generation, List.of(new EvidenceView.Served(PY, "remote", served)), List.of());
    }

    /** A health probe that reports the given URLs as up and everything else as down. */
    static Sources.Health up(String... urls) {
        Set<String> healthy = Set.of(urls);
        return (url, timeout) -> healthy.contains(url)
                ? new HealthCheck(HealthCheck.Status.UP, url + "/health", 3L, "200")
                : new HealthCheck(HealthCheck.Status.DOWN, url + "/health", null, "unreachable: refused");
    }

    static InventorySnapshot observe(RoutingView table, EvidenceView ev, Sources.Health health) {
        var inventory = new ObservedInventory(() -> table, () -> ev, health, () -> CATALOGUE, 100, Clock.systemUTC());
        var result = inventory.observe(Duration.ofSeconds(2));
        assertThat(result).isInstanceOf(Inventory.Observed.class);
        return ((Inventory.Observed) result).snapshot();
    }

    @Test
    void readyWhenEveryAddressAnswersAndThereIsEnoughEvidence() {
        var table = new RoutingView(4, "stage-1b.yaml",
                List.of(role("champion", PY, "remote", PY_URL), role("shadow", J21, "remote", JAVA_URL)));
        var s = observe(table, evidence(4, 1200), up(PY_URL, JAVA_URL));

        assertThat(s.ready()).isTrue();
        assertThat(s.notReadyBecause()).isEmpty();
        assertThat(s.problems()).isEmpty();
        assertThat(s.placements()).extracting(p -> p.health().status())
                .containsExactly(HealthCheck.Status.UP, HealthCheck.Status.UP);
        assertThat(s.catalogue()).filteredOn(e -> e.address().equals(J21)).first()
                .extracting(InventorySnapshot.CatalogueEntry::routedAs).isEqualTo(List.of("shadow"));
    }

    @Test
    void notReadyWhenAnAddressDoesNotAnswerHealth() {
        var table = new RoutingView(4, "stage-1b.yaml",
                List.of(role("champion", PY, "remote", PY_URL), role("shadow", J21, "remote", JAVA_URL)));
        var s = observe(table, evidence(4, 1200), up(PY_URL));

        assertThat(s.ready()).isFalse();
        assertThat(s.notReadyBecause()).singleElement().asString()
                .contains(J21).contains("not answering /health").contains("refused");
    }

    @Test
    void notReadyUntilThereIsEnoughEvidence() {
        var table = new RoutingView(2, "stage-0.yaml", List.of(role("champion", PY, "remote", PY_URL)));
        var s = observe(table, evidence(2, 12), up(PY_URL));
        assertThat(s.ready()).isFalse();
        assertThat(s.notReadyBecause()).containsExactly("only 12 evidence records for generation 2 (need 100)");
    }

    @Test
    void notReadyIfTheTableChangedWhileObserving() {
        var table = new RoutingView(5, "stage-2.yaml", List.of(role("champion", PY, "remote", PY_URL)));
        var s = observe(table, evidence(6, 5000), up(PY_URL));
        assertThat(s.ready()).isFalse();
        assertThat(s.notReadyBecause()).singleElement().asString().contains("changed while observing");
    }

    @Test
    void localBindingIsInProcessAndNotProbed() {
        var table = new RoutingView(1, "stage-4.yaml", List.of(role("champion", J21, "local", null)));
        Sources.Health neverCalled = (url, t) -> { throw new AssertionError("probed " + url); };
        var s = observe(table, evidence(1, 200), neverCalled);
        assertThat(s.ready()).isTrue();
        assertThat(s.placements().get(0).health().status()).isEqualTo(HealthCheck.Status.IN_PROCESS);
    }

    @Test
    void reportsCatalogueProblemsWithoutBlockingReadiness() {
        var table = new RoutingView(2, "custom.yaml", List.of(
                role("champion", PY, "local", null),                       // python may not run locally
                role("shadow", J20, "remote", JAVA_URL)));                 // withdrawn
        var s = observe(table, evidence(2, 500), up(JAVA_URL));
        assertThat(s.ready()).isTrue();
        assertThat(s.problems()).hasSize(2)
                .anySatisfy(p -> assertThat(p).contains(PY).contains("allows only [remote]"))
                .anySatisfy(p -> assertThat(p).contains(J20).contains("withdrawn"));

        var unknown = new RoutingView(3, "custom.yaml",
                List.of(role("champion", "pkg:amorphic/pricing-calc@9.9?lang=go", "remote", "http://x")));
        assertThat(observe(unknown, evidence(3, 500), up("http://x")).problems())
                .singleElement().asString().contains("not in the catalogue");
    }

    @Test
    void showsWhereUnroutedToolingRunsAndWhatIsPlanned() {
        var table = new RoutingView(1, "stage-0.yaml", List.of(role("champion", PY, "remote", PY_URL)));
        var inventory = new ObservedInventory(() -> table, () -> evidence(1, 500), up(PY_URL), () -> CATALOGUE, 100,
                Clock.systemUTC(), Map.of("pkg:amorphic/inventory@0.1?lang=java", "in-process (this inventory)"));
        var s = ((Inventory.Observed) inventory.observe(Duration.ofSeconds(1))).snapshot();

        assertThat(s.catalogue()).filteredOn(e -> e.capability().equals("inventory")).singleElement()
                .satisfies(e -> {
                    assertThat(e.kind()).isEqualTo("tooling");
                    assertThat(e.runningAs()).isEqualTo("in-process (this inventory)");
                    assertThat(e.routedAs()).isEmpty();
                });
        assertThat(s.catalogue()).filteredOn(e -> e.capability().equals("governor")).singleElement()
                .satisfies(e -> assertThat(e.planned()).isEqualTo("governor phase 3"));
        assertThat(s.catalogue()).filteredOn(e -> e.address().equals(PY)).singleElement()
                .satisfies(e -> assertThat(e.kind()).isEqualTo("capability"));
        // Tooling that isn't routed is not a problem.
        assertThat(s.problems()).isEmpty();
    }

    @Test
    void unavailableWhenASourceFails() {
        var inventory = new ObservedInventory(() -> { throw new IllegalStateException("app not reachable"); },
                () -> evidence(1, 0), up(), () -> CATALOGUE, 100, Clock.systemUTC());
        assertThat(inventory.observe(Duration.ofSeconds(1))).isInstanceOfSatisfying(Inventory.Unavailable.class,
                u -> assertThat(u.reason()).contains("app not reachable"));
    }

    @Test
    void aHungProbeIsCutOffAtTheDeadline() {
        var table = new RoutingView(1, "stage-0.yaml", List.of(role("champion", PY, "remote", PY_URL)));
        Sources.Health hangs = (url, t) -> {
            try { Thread.sleep(5000); } catch (InterruptedException ignored) { }
            return new HealthCheck(HealthCheck.Status.UP, url, 1L, "late");
        };
        var inventory = new ObservedInventory(() -> table, () -> evidence(1, 500), hangs, () -> CATALOGUE, 100,
                Clock.systemUTC());
        long start = System.nanoTime();
        var s = ((Inventory.Observed) inventory.observe(Duration.ofMillis(300))).snapshot();
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(2000);
        assertThat(s.placements().get(0).health().detail()).contains("deadline");
        assertThat(s.ready()).isFalse();
    }

    @Test
    void snapshotIsAValueNotALiveView() {
        var s = observe(new RoutingView(1, "t", List.of(role("champion", PY, "remote", PY_URL))),
                evidence(1, 500), up(PY_URL));
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> s.placements().add(null));
        assertThat(Map.of("ready", s.ready())).containsEntry("ready", true);
    }
}
