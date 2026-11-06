package dev.amorphic.demo.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.amorphic.demo.evidence.EvidenceRecorder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

class RoutingPricingCalcTest {

    static final String PY = "pkg:amorphic/pricing-calc@1.0?lang=python";
    static final String JAVA = "pkg:amorphic/pricing-calc@2.0?lang=java";

    /** Fake endpoints keyed by url: "even" rounds HALF_EVEN, "up" rounds HALF_UP, "down" always fails. */
    static final BiFunction<Address, AddressTable.Binding, PricingEndpoint> FAKES = (address, binding) -> switch (binding.url()) {
        case "even" -> (a, f, t) -> a.multiply(new BigDecimal("1.25")).setScale(2, RoundingMode.HALF_EVEN);
        case "up" -> (a, f, t) -> a.multiply(new BigDecimal("1.25")).setScale(2, RoundingMode.HALF_UP);
        case "down" -> (a, f, t) -> { throw new IllegalStateException("connection refused"); };
        default -> throw new IllegalArgumentException(binding.url());
    };

    final EvidenceRecorder evidence = new EvidenceRecorder(new ObjectMapper());

    static AddressTable.Role role(String address, String url) {
        return new AddressTable.Role(address, new AddressTable.Binding("remote", url), null);
    }

    static AddressTable.Role canary(String address, String url, int weight) {
        return new AddressTable.Role(address, new AddressTable.Binding("remote", url), weight);
    }

    RoutingPricingCalc calc(AddressTable.Capability c, double random) {
        // Runnable::run makes the background comparison synchronous for the test.
        return RoutingPricingCalc.resolve(c, FAKES, Runnable::run, () -> random, evidence, Clock.systemUTC());
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> comparisons() {
        return (List<Map<String, Object>>) evidence.summary().get("comparisons");
    }

    @Test
    void championOnlyServesAndRecordsNoComparison() {
        var calc = calc(new AddressTable.Capability(role(PY, "even"), null, null), 0.5);
        var r = calc.convert("r1", new BigDecimal("10.02"), "GBP", "USD");
        assertThat(r.amount()).isEqualByComparingTo("12.52");
        assertThat(r.servedBy()).isEqualTo(PY);
        assertThat(comparisons()).isEmpty();
    }

    @Test
    void shadowIsComparedButNeverServed() {
        var calc = calc(new AddressTable.Capability(role(PY, "even"), role(JAVA, "up"), null), 0.5);
        var r = calc.convert("r1", new BigDecimal("10.02"), "GBP", "USD");   // 12.525: rules disagree
        calc.convert("r2", new BigDecimal("10.06"), "GBP", "USD");           // 12.575: rules agree
        assertThat(r.amount().toPlainString()).isEqualTo("12.52");
        assertThat(r.servedBy()).isEqualTo(PY);

        Map<String, Object> pair = comparisons().get(0);
        assertThat(pair).containsEntry("served", PY).containsEntry("shadow", JAVA)
                .containsEntry("total", 2L).containsEntry("matches", 1L).containsEntry("mismatches", 1L)
                .containsEntry("consecutiveMatches", 1L);
        @SuppressWarnings("unchecked")
        Map<String, Object> last = (Map<String, Object>) pair.get("lastMismatch");
        assertThat(last).containsEntry("requestId", "r1").containsEntry("servedValue", "12.52")
                .containsEntry("shadowValue", "12.53");
    }

    @Test
    void failingShadowCountsAsMismatchAndDoesNotAffectTheCaller() {
        var calc = calc(new AddressTable.Capability(role(PY, "even"), role(JAVA, "down"), null), 0.5);
        var r = calc.convert("r1", new BigDecimal("1.00"), "GBP", "USD");
        assertThat(r.amount().toPlainString()).isEqualTo("1.25");
        assertThat(comparisons().get(0)).containsEntry("mismatches", 1L);
    }

    @Test
    void canaryServesItsShareAndIsComparedWithTheChampion() {
        var cap = new AddressTable.Capability(role(PY, "even"), null, canary(JAVA, "up", 10));

        var canaryCall = calc(cap, 0.05).convert("r1", new BigDecimal("10.02"), "GBP", "USD");
        assertThat(canaryCall.servedBy()).isEqualTo(JAVA);
        assertThat(canaryCall.amount().toPlainString()).isEqualTo("12.53");
        assertThat(comparisons().get(0)).containsEntry("served", JAVA).containsEntry("shadow", PY)
                .containsEntry("mismatches", 1L);

        var championCall = calc(cap, 0.50).convert("r2", new BigDecimal("10.02"), "GBP", "USD");
        assertThat(championCall.servedBy()).isEqualTo(PY);
    }

    @Test
    void rejectsShadowAndCanaryTogether() {
        var cap = new AddressTable.Capability(role(PY, "even"), role(JAVA, "up"), canary(JAVA, "up", 10));
        assertThatThrownBy(() -> calc(cap, 0.5)).isInstanceOf(AddressTableException.class).hasMessageContaining("not both");
    }

    @Test
    void rejectsBadCanaryWeight() {
        var cap = new AddressTable.Capability(role(PY, "even"), null, canary(JAVA, "up", 150));
        assertThatThrownBy(() -> calc(cap, 0.5)).isInstanceOf(AddressTableException.class).hasMessageContaining("0-100");
    }
}
