package dev.amorphic.demo.pricing;

import dev.amorphic.access.Invoker;
import dev.amorphic.access.LocalBinding;
import dev.amorphic.addressing.Address;
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

    /** Fake bindings keyed by url: "even" rounds HALF_EVEN, "up" rounds HALF_UP, "down" always fails. */
    static final BiFunction<Address, AddressTable.Binding, Bound> FAKES = (address, binding) -> Bound.unchecked(
            switch (binding.url()) {
                case "even" -> rounding(RoundingMode.HALF_EVEN);
                case "up" -> rounding(RoundingMode.HALF_UP);
                case "down" -> (a, request) -> { throw new IllegalStateException("connection refused"); };
                default -> throw new IllegalArgumentException(binding.url());
            }, binding.url());

    /** An in-process binding that converts at 1.25 with the given rounding, answering envelopes. */
    static Invoker rounding(RoundingMode mode) {
        return new LocalBinding(request -> {
            var body = request.payload().body();
            BigDecimal amount = new BigDecimal(body.path("amount").asText());
            return PricingEnvelopes.reply(request, amount.multiply(new BigDecimal("1.25")).setScale(2, mode),
                    body.path("to").asText());
        });
    }

    final EvidenceRecorder evidence = new EvidenceRecorder(new ObjectMapper());

    static AddressTable.Role role(String address, String url) {
        return new AddressTable.Role(address, new AddressTable.Binding("remote", url), null);
    }

    static AddressTable.Role canary(String address, String url, int weight) {
        return new AddressTable.Role(address, new AddressTable.Binding("remote", url), weight);
    }

    RoutingPricingCalc calc(AddressTable.Capability c, double random) {
        // Runnable::run makes the background comparison synchronous for the test.
        return RoutingPricingCalc.resolve(c, FAKES, Runnable::run, () -> random, evidence, Clock.systemUTC(), 0, null, null);
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

    // Spec 03: every call records the topology it ran on.

    static final dev.amorphic.envelope.InstanceRef APP = new dev.amorphic.envelope.InstanceRef("demo-app-dev",
            Address.parse("pkg:amorphic/demo-app@0.1?lang=java"), null);

    static dev.amorphic.envelope.InstanceRef instance(String identity, String address) {
        return new dev.amorphic.envelope.InstanceRef(identity, Address.parse(address), null);
    }

    /** Bindings the handshake has checked: "py" and "java" are who they say; "swapped" answers as someone else. */
    static final BiFunction<Address, AddressTable.Binding, Bound> CHECKED = (address, binding) -> switch (binding.url()) {
        case "py" -> new Bound(rounding(RoundingMode.HALF_EVEN), "py", instance("py-1", PY), false);
        case "java" -> new Bound(signed(rounding(RoundingMode.HALF_UP), instance("java-1", JAVA)), "java",
                instance("java-1", JAVA), false);
        case "swapped" -> new Bound(signed(rounding(RoundingMode.HALF_UP), instance("java-old", JAVA)), "swapped",
                instance("java-1", JAVA), false);
        default -> throw new IllegalArgumentException(binding.url());
    };

    /** Adds the instance aspect to every reply, as a service speaking the envelope does. */
    static Invoker signed(Invoker inner, dev.amorphic.envelope.InstanceRef who) {
        return (a, request) -> {
            var r = inner.invoke(a, request);
            return new dev.amorphic.access.Reply(r.envelope().with(dev.amorphic.envelope.InstanceRef.ASPECT, who.aspect()), r.timing());
        };
    }

    RoutingPricingCalc recording(AddressTable.Capability c, dev.amorphic.topology.TopologyLog log) {
        if (evidence.generation() != 7) evidence.reset(7);
        return RoutingPricingCalc.resolve(c, CHECKED, Runnable::run, () -> 0.5, evidence, Clock.systemUTC(), 7, log, APP);
    }

    @Test
    void eachCallRecordsWhoAnsweredInEachRole() {
        var log = dev.amorphic.topology.TopologyLog.inMemory();
        var calc = recording(new AddressTable.Capability(role(PY, "py"), role(JAVA, "java"), null), log);
        calc.convert("r1", new BigDecimal("10.02"), "GBP", "USD");
        calc.convert("r2", new BigDecimal("10.06"), "GBP", "USD");

        var all = log.topologies(Address.parse("pkg:topology/pricing-calc"));
        assertThat(all).hasSize(1);
        var t = all.get(0);
        assertThat(t.caller()).isEqualTo(APP);
        assertThat(t.roles()).extracting(r -> r.role() + " " + r.instance().identity() + " " + r.handle())
                .containsExactly("champion py-1 py", "shadow java-1 java");
        var calls = log.calls(t.address());
        assertThat(calls).hasSize(2);
        assertThat(calls.get(0).causedBy()).containsExactly("r1");
        assertThat(calls.get(0).generation()).isEqualTo(7);

        // Evidence counts the comparison under that topology.
        assertThat(comparisons().get(0)).containsEntry("topology", t.address().toString()).containsEntry("total", 2L);
        @SuppressWarnings("unchecked")
        Map<String, Object> last = (Map<String, Object>) comparisons().get(0).get("lastMismatch");
        assertThat(last).containsEntry("topology", t.address().toString());
    }

    @Test
    void aServiceSwappedBetweenCallsShowsUpAsADifferentTopology() {
        var log = dev.amorphic.topology.TopologyLog.inMemory();
        recording(new AddressTable.Capability(role(PY, "py"), role(JAVA, "java"), null), log)
                .convert("r1", new BigDecimal("1.00"), "GBP", "USD");
        recording(new AddressTable.Capability(role(PY, "py"), role(JAVA, "swapped"), null), log)
                .convert("r2", new BigDecimal("1.00"), "GBP", "USD");

        var all = log.topologies(Address.parse("pkg:topology/pricing-calc"));
        assertThat(all).hasSize(2);
        assertThat(all.get(1).roles().get(1).instance().identity()).as("who actually answered").isEqualTo("java-old");
        assertThat(comparisons()).hasSize(2);
    }

    @Test
    void aShadowThatFailsIsRecordedAsWhoTheHandshakeFound() {
        BiFunction<Address, AddressTable.Binding, Bound> failing = (address, binding) -> "java".equals(binding.url())
                ? new Bound((a, request) -> { throw new IllegalStateException("connection refused"); }, "java",
                        instance("java-1", JAVA), false)
                : CHECKED.apply(address, binding);
        var log = dev.amorphic.topology.TopologyLog.inMemory();
        RoutingPricingCalc.resolve(new AddressTable.Capability(role(PY, "py"), role(JAVA, "java"), null), failing,
                Runnable::run, () -> 0.5, evidence, Clock.systemUTC(), 1, log, APP)
                .convert("r1", new BigDecimal("1.00"), "GBP", "USD");
        var t = log.topologies(Address.parse("pkg:topology/pricing-calc")).get(0);
        assertThat(t.roles().get(1).instance().identity()).isEqualTo("java-1");
    }
}

