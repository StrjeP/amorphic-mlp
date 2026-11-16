package dev.amorphic.governor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.amorphic.inventory.Catalogue;
import dev.amorphic.inventory.EvidenceView;
import dev.amorphic.inventory.HealthCheck;
import dev.amorphic.inventory.ObservedInventory;
import dev.amorphic.inventory.RoutingView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Drives the governor through the demo with the real inventory, catalogue and rule table. At each
 * stage the proposed table must be exactly the next stage's file.
 */
class RuleGovernorTest {

    static final Path ENV = Path.of("../../amorphic-demo-env");
    static final String PY = "pkg:amorphic/pricing-calc@1.0?lang=python";
    static final String J20 = "pkg:amorphic/pricing-calc@2.0?lang=java";
    static final String J21 = "pkg:amorphic/pricing-calc@2.1?lang=java";
    static final Duration T = Duration.ofSeconds(2);

    final AtomicReference<RoutingView> table = new AtomicReference<>();
    final AtomicReference<EvidenceView> evidence = new AtomicReference<>();
    final List<String> applied = new ArrayList<>();
    TableSink sink = (yaml, source) -> {
        applied.add(yaml);
        return new TableSink.Accepted(table.get().generation() + 1);
    };

    Catalogue catalogue = Catalogue.load(ENV.resolve("catalogue.yaml"));

    RuleGovernor governor() {
        var inventory = new ObservedInventory(table::get, evidence::get,
                (url, timeout) -> new HealthCheck(HealthCheck.Status.UP, url + "/health", 1L, "200"),
                () -> catalogue, 100, Clock.systemUTC());
        return new RuleGovernor(inventory, () -> catalogue, () -> RuleTable.load(ENV.resolve("governor-rules.yaml")),
                (yaml, source) -> sink.apply(yaml, source), Clock.systemUTC());
    }

    static String stageFile(String id) throws Exception {
        return Files.readString(ENV.resolve("addresses/stage-" + id + ".yaml"));
    }

    /** Loads a stage file as the live table at {@code generation}. */
    @SuppressWarnings("unchecked")
    static RoutingView stage(String yamlText, long generation) throws Exception {
        Map<String, Object> root = new ObjectMapper(new YAMLFactory()).readValue(yamlText, Map.class);
        List<RoutingView.Role> roles = new ArrayList<>();
        ((Map<String, Map<String, Map<String, Object>>>) root.get("capabilities")).forEach((cap, rs) ->
                rs.forEach((role, r) -> {
                    Map<String, Object> b = (Map<String, Object>) r.get("binding");
                    roles.add(new RoutingView.Role(cap, role, (String) r.get("address"), (String) b.get("type"),
                            (String) b.get("url"), (Integer) r.get("weight")));
                }));
        return new RoutingView(generation, "test", roles);
    }

    static EvidenceView compared(long generation, String served, String shadow, long consecutive, long mismatches) {
        long total = consecutive + mismatches;
        return new EvidenceView(generation, List.of(new EvidenceView.Served(served, "remote", Math.max(total, 200))),
                List.of(new EvidenceView.Comparison(served, shadow, total, consecutive, mismatches, consecutive)));
    }

    static EvidenceView servedOnly(long generation, String address, long total) {
        return new EvidenceView(generation, List.of(new EvidenceView.Served(address, "remote", total)), List.of());
    }

    Proposal proposed(Governor.ProposeResult r) {
        assertThat(r).as(r.toString()).isInstanceOf(Governor.Proposed.class);
        return ((Governor.Proposed) r).proposal();
    }

    @Test
    void stage0ProposesShadowingTheNewestVersionWhichIsStage1b() throws Exception {
        table.set(stage(stageFile("0"), 1));
        evidence.set(servedOnly(1, PY, 200));
        Proposal p = proposed(governor().propose(T));
        assertThat(p.rule()).isEqualTo("start-shadow");
        assertThat(p.toTable()).isEqualTo(stageFile("1b"));
        assertThat(p.fromTable()).isEqualTo(stageFile("0"));
        assertThat(p.needsRebuild()).isFalse();
    }

    @Test
    void stage1ReplacesTheWithdrawnShadowBeforeHoldingOnItsMismatches() throws Exception {
        table.set(stage(stageFile("1"), 2));
        evidence.set(compared(2, PY, J20, 1, 340));
        Proposal p = proposed(governor().propose(T));
        assertThat(p.rule()).isEqualTo("replace-withdrawn-shadow");
        assertThat(p.toTable()).isEqualTo(stageFile("1b"));
    }

    @Test
    void stage1bWithEnoughMatchesProposesTheTenPercentCanaryWhichIsStage2() throws Exception {
        table.set(stage(stageFile("1b"), 3));
        evidence.set(compared(3, PY, J21, 1200, 0));
        Proposal p = proposed(governor().propose(T));
        assertThat(p.rule()).isEqualTo("shadow-to-canary");
        assertThat(p.toTable()).isEqualTo(stageFile("2"));
        assertThat(p.gate()).isEqualTo("1200 consecutive matches, 0 mismatches (rule needs 1000)");
    }

    @Test
    void stage1bWaitsUntilTheGateIsMet() throws Exception {
        table.set(stage(stageFile("1b"), 3));
        evidence.set(compared(3, PY, J21, 500, 0));
        assertThat(governor().propose(T)).isEqualTo(new Governor.Waiting(
                List.of("rule shadow-to-canary needs 1000 consecutive matches; 500 so far")));
    }

    @Test
    void holdsWhenAVersionThatIsNotWithdrawnDisagrees() throws Exception {
        table.set(stage(stageFile("1b"), 3));
        evidence.set(compared(3, PY, J21, 4, 12));
        assertThat(governor().propose(T)).isInstanceOfSatisfying(Governor.Holding.class, h -> {
            assertThat(h.rule()).isEqualTo("hold-on-mismatch");
            assertThat(h.details()).singleElement().asString().contains("12 mismatches");
        });
    }

    @Test
    void stage2AtTenPercentRaisesTheCanaryToFifty() throws Exception {
        table.set(stage(stageFile("2"), 4));
        evidence.set(compared(4, J21, PY, 150, 0));
        Proposal p = proposed(governor().propose(T));
        assertThat(p.rule()).isEqualTo("raise-canary");
        assertThat(p.toTable()).isEqualTo(stageFile("2").replace("weight: 10", "weight: 50"));
    }

    @Test
    void stage2AtFiftyPercentPromotesWhichIsStage3() throws Exception {
        table.set(stage(stageFile("2").replace("weight: 10", "weight: 50"), 5));
        evidence.set(compared(5, J21, PY, 1238, 0));
        Proposal p = proposed(governor().propose(T));
        assertThat(p.rule()).isEqualTo("promote");
        assertThat(p.toTable()).isEqualTo(stageFile("3"));
    }

    @Test
    void stage3DeprecatesTheOldVersionWhichIsStage3b() throws Exception {
        table.set(stage(stageFile("3"), 6));
        evidence.set(compared(6, J21, PY, 1200, 0));
        Proposal p = proposed(governor().propose(T));
        assertThat(p.rule()).isEqualTo("deprecate");
        assertThat(p.toTable()).isEqualTo(stageFile("3b"));
    }

    @Test
    void stage3bProposesTheFoldInWhichIsStage4AndNeedsARebuild() throws Exception {
        table.set(stage(stageFile("3b"), 7));
        evidence.set(servedOnly(7, J21, 200));
        Proposal p = proposed(governor().propose(T));
        assertThat(p.rule()).isEqualTo("fold-in");
        assertThat(p.toTable()).isEqualTo(stageFile("4"));
        assertThat(p.needsRebuild()).isTrue();
    }

    @Test
    void stage4HasNothingFurtherToPropose() throws Exception {
        table.set(stage(stageFile("4"), 1));
        evidence.set(new EvidenceView(1, List.of(new EvidenceView.Served(J21, "local", 200)), List.of()));
        assertThat(governor().propose(T)).isInstanceOfSatisfying(Governor.NoChange.class,
                n -> assertThat(n.reason()).contains("folded in"));
    }

    @Test
    void waitsWhileTheInventoryIsNotReady() throws Exception {
        table.set(stage(stageFile("1b"), 3));
        evidence.set(servedOnly(3, PY, 10));
        assertThat(governor().propose(T)).isInstanceOfSatisfying(Governor.Waiting.class,
                w -> assertThat(w.reasons()).singleElement().asString().contains("evidence records"));
    }

    @Test
    void approvalAppliesTheProposalOnceAndOnlyOnce() throws Exception {
        table.set(stage(stageFile("1b"), 3));
        evidence.set(compared(3, PY, J21, 1200, 0));
        RuleGovernor g = governor();
        Proposal p = proposed(g.propose(T));

        assertThat(g.approve(p.id(), T)).isEqualTo(new Governor.Applied(4, p));
        assertThat(applied).containsExactly(stageFile("2"));
        assertThat(g.approve(p.id(), T)).isInstanceOf(Governor.Rejected.class);
    }

    @Test
    void approvalIsRejectedIfTheTableMovedOnSinceTheProposal() throws Exception {
        table.set(stage(stageFile("1b"), 3));
        evidence.set(compared(3, PY, J21, 1200, 0));
        RuleGovernor g = governor();
        Proposal p = proposed(g.propose(T));

        table.set(stage(stageFile("1b"), 4));  // someone applied another table meanwhile
        assertThat(g.approve(p.id(), T)).isInstanceOfSatisfying(Governor.Rejected.class,
                r -> assertThat(r.reason()).contains("changed since this proposal"));
        assertThat(applied).isEmpty();
    }

    @Test
    void foldInApprovalAsksForARebuildAndAppliesNothing() throws Exception {
        table.set(stage(stageFile("3b"), 7));
        evidence.set(servedOnly(7, J21, 200));
        RuleGovernor g = governor();
        Proposal p = proposed(g.propose(T));
        assertThat(g.approve(p.id(), T)).isInstanceOf(Governor.NeedsRebuild.class);
        assertThat(applied).isEmpty();
    }

    @Test
    void approvalReportsWhenTheAppRefusesTheTable() throws Exception {
        table.set(stage(stageFile("1b"), 3));
        evidence.set(compared(3, PY, J21, 1200, 0));
        sink = (yaml, source) -> new TableSink.Refused("no such binding");
        RuleGovernor g = governor();
        Proposal p = proposed(g.propose(T));
        assertThat(g.approve(p.id(), T)).isInstanceOfSatisfying(Governor.Rejected.class,
                r -> assertThat(r.reason()).contains("no such binding"));
    }

    @Test
    void unknownProposalIsRejected() {
        assertThat(governor().approve("g9-nothing", T)).isInstanceOf(Governor.Rejected.class);
    }

    /** Tooling entries as ./demo tooling writes them: here the inventory is its own service. */
    @Test
    void proposalsLeaveTheGovernorAndInventoryEntriesAlone() throws Exception {
        String withTooling = stageFile("1b") + """
                  inventory:
                    champion:
                      address: pkg:amorphic/inventory@0.1?lang=java
                      binding: { type: remote, url: http://inventory:8080 }
                  governor:
                    champion:
                      address: pkg:amorphic/governor@0.1?lang=java
                      binding: { type: local }
                """;
        table.set(stage(withTooling, 3));
        evidence.set(compared(3, PY, J21, 1200, 0));
        Proposal p = proposed(governor().propose(T));
        assertThat(p.capability()).isEqualTo("pricing-calc");
        assertThat(p.toTable()).isEqualTo(stageFile("2") + withTooling.substring(stageFile("1b").length()));
    }

    @Test
    void theGuardRefusesAnyChangeToHumanOnlyEntries() {
        var inv = new RoutingView.Role("inventory", "champion", "pkg:amorphic/inventory@0.1?lang=java", "local", null, null);
        var moved = new RoutingView.Role("inventory", "champion", "pkg:amorphic/inventory@0.1?lang=java", "remote",
                "http://inventory:8080", null);
        assertThatThrownBy(() -> Tables.guardHumanOnly(List.of(inv), List.of(moved)))
                .hasMessageContaining("only a human");
        assertThatThrownBy(() -> Tables.guardHumanOnly(List.of(inv), List.of())).hasMessageContaining("inventory");
        Tables.guardHumanOnly(List.of(inv), List.of(inv));
    }

    @Test
    void versionsCompareNumerically() {
        assertThat(Tables.compareVersions(J21, PY)).isPositive();
        assertThat(Tables.compareVersions(J20, J21)).isNegative();
        assertThat(Tables.compareVersions("pkg:amorphic/x@2.10?lang=java", "pkg:amorphic/x@2.9?lang=java")).isPositive();
        assertThat(Tables.compareVersions(J21, J21)).isZero();
    }
}
