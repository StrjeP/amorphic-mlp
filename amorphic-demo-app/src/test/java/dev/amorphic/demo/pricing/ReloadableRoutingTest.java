package dev.amorphic.demo.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.amorphic.composer.Composer;
import dev.amorphic.demo.compose.LocalCapabilities;
import dev.amorphic.demo.compose.Outcomes;
import dev.amorphic.demo.evidence.EvidenceRecorder;
import dev.amorphic.envelope.InstanceRef;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReloadableRoutingTest {

    static final String PY = "pkg:amorphic/pricing-calc@1.0?lang=python";
    static final String JAVA = "pkg:amorphic/pricing-calc@2.1?lang=java";

    static AddressTable table(String yaml) {
        return AddressTable.parse(yaml, "test");
    }

    static final AddressTable PYTHON_ONLY = table("""
            capabilities:
              pricing-calc:
                champion:
                  address: pkg:amorphic/pricing-calc@1.0?lang=python
                  binding: { type: remote, url: even }
            """);

    static final AddressTable JAVA_SHADOW = table("""
            capabilities:
              pricing-calc:
                champion:
                  address: pkg:amorphic/pricing-calc@1.0?lang=python
                  binding: { type: remote, url: even }
                shadow:
                  address: pkg:amorphic/pricing-calc@2.1?lang=java
                  binding: { type: remote, url: even }
            """);

    final EvidenceRecorder evidence = new EvidenceRecorder(new ObjectMapper());
    final LocalCapabilities local = new LocalCapabilities(StageAddressTablesTest.SELF,
            Path.of("../amorphic-demo-env/accounts.yaml"));

    /** Background comparisons are queued so a test can let them finish after a switch. */
    final List<Runnable> queued = new ArrayList<>();

    ReloadableRouting routing() {
        return new ReloadableRouting(PYTHON_ONLY, "startup", PricingRoutesTest.FAKES, () -> 0.5, evidence,
                Clock.systemUTC(), local::route);
    }

    Outcomes outcomes(ReloadableRouting routing) {
        return new Outcomes(new Composer(StageAddressTablesTest.CATALOGUE::outcomes, routing, queued::add, null,
                InstanceRef.of(StageAddressTablesTest.SELF), Clock.systemUTC()), evidence, Clock.systemUTC());
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> comparisons() {
        return (List<Map<String, Object>>) evidence.summary().get("comparisons");
    }

    @Test
    void appliesANewTableWithoutBeingRecreated() {
        var routing = routing();
        assertThat(routing.current().generation()).isEqualTo(1);

        var applied = routing.apply(JAVA_SHADOW, "stage-1b.yaml");
        assertThat(applied.generation()).isEqualTo(2);
        assertThat(applied.routing()).contains("shadow=" + JAVA);
        assertThat(evidence.generation()).isEqualTo(2);

        outcomes(routing).quote("r1", new BigDecimal("10.02"), "GBP", "USD");
        queued.forEach(Runnable::run);
        assertThat(comparisons()).hasSize(1);
        assertThat(comparisons().get(0)).containsEntry("shadow", JAVA).containsEntry("matches", 1L);
    }

    @Test
    void rejectedTableLeavesTheCurrentRoutingLive() {
        var routing = routing();
        AddressTable unreachable = table("""
                capabilities:
                  pricing-calc:
                    champion:
                      address: pkg:amorphic/pricing-calc@9.9?lang=java
                      binding: { type: local }
                """);
        assertThatThrownBy(() -> new ReloadableRouting(unreachable, "x", BindingFactory.reading(() -> StageAddressTablesTest.CATALOGUE,
                () -> StageAddressTablesTest.INSTANCES, StageAddressTablesTest.SELF, StageAddressTablesTest.DECLARED_META),
                () -> 0.5, evidence, Clock.systemUTC(), local::route)).isInstanceOf(AddressTableException.class);

        // Through apply on a live instance: the fake factory rejects an unknown url.
        AddressTable bad = table("""
                capabilities:
                  pricing-calc:
                    champion:
                      address: pkg:amorphic/pricing-calc@1.0?lang=python
                      binding: { type: remote, url: nowhere }
                """);
        assertThatThrownBy(() -> routing.apply(bad, "bad.yaml")).isInstanceOf(IllegalArgumentException.class);
        assertThat(routing.current().generation()).isEqualTo(1);
        var run = outcomes(routing).quote("r1", new BigDecimal("1.00"), "GBP", "USD");
        assertThat(run.step("convert").route().served().resolved().toString()).isEqualTo(PY);
    }

    @Test
    void comparisonsStillRunningFromTheOldTableAreDropped() {
        var routing = routing();
        routing.apply(JAVA_SHADOW, "stage-1b.yaml");
        outcomes(routing).quote("old", new BigDecimal("10.02"), "GBP", "USD");   // comparison queued under generation 2

        routing.apply(JAVA_SHADOW, "stage-1b.yaml again");                       // generation 3
        queued.forEach(Runnable::run);                                           // the generation-2 comparison finishes late

        assertThat(comparisons()).isEmpty();
        assertThat(evidence.summary()).containsEntry("generation", 3L);
    }

    @Test
    void parseRejectsEmptyAndMalformedTables() {
        assertThatThrownBy(() -> AddressTable.parse("  ", "empty")).hasMessageContaining("empty");
        assertThatThrownBy(() -> AddressTable.parse("capabilities: [", "broken")).isInstanceOf(AddressTableException.class);
        assertThatThrownBy(() -> AddressTable.parse("other: 1", "wrong")).isInstanceOf(AddressTableException.class);
    }

    @Test
    void appliedRecordsWhenAndFromWhere() {
        var applied = routing().apply(JAVA_SHADOW, "stage-1b.yaml");
        assertThat(applied.source()).isEqualTo("stage-1b.yaml");
        assertThat(applied.appliedAt()).isBeforeOrEqualTo(Instant.now());
    }
}
