package dev.amorphic.demo.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.amorphic.access.Invoker;
import dev.amorphic.access.LocalBinding;
import dev.amorphic.access.Reply;
import dev.amorphic.addressing.Address;
import dev.amorphic.composer.Composer;
import dev.amorphic.composer.Run;
import dev.amorphic.demo.compose.LocalCapabilities;
import dev.amorphic.demo.compose.Outcomes;
import dev.amorphic.demo.evidence.EvidenceRecorder;
import dev.amorphic.envelope.InstanceRef;
import dev.amorphic.topology.TopologyLog;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

/**
 * pricing-calc's routes, through the composer: what serves, what is compared, what evidence and the topology log
 * record. Quotes are plans of one step; the account report fans out over the sample account's positions.
 */
class PricingRoutesTest {

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

    static final InstanceRef APP = InstanceRef.of(StageAddressTablesTest.SELF);

    final EvidenceRecorder evidence = new EvidenceRecorder(new ObjectMapper());
    final TopologyLog log = TopologyLog.inMemory();

    static AddressTable.Role role(String address, String url) {
        return new AddressTable.Role(address, new AddressTable.Binding("remote", url), null);
    }

    static AddressTable.Role canary(String address, String url, int weight) {
        return new AddressTable.Role(address, new AddressTable.Binding("remote", url), weight);
    }

    static AddressTable table(AddressTable.Capability c) {
        return new AddressTable(Map.of(ReloadableRouting.CAPABILITY, c));
    }

    /** The app's outcomes on {@code c}, with comparisons run straight away so a test can look at them. */
    Outcomes outcomes(AddressTable.Capability c, BiFunction<Address, AddressTable.Binding, Bound> bindings,
            double random) {
        LocalCapabilities local = new LocalCapabilities(StageAddressTablesTest.SELF,
                Path.of("../amorphic-demo-env/accounts.yaml"));
        ReloadableRouting routing = new ReloadableRouting(table(c), "test", bindings, () -> random, evidence,
                Clock.systemUTC(), local::route);
        Composer composer = new Composer(StageAddressTablesTest.CATALOGUE::outcomes, routing, Runnable::run, log, APP,
                Clock.systemUTC());
        return new Outcomes(composer, evidence, Clock.systemUTC());
    }

    Outcomes outcomes(AddressTable.Capability c, double random) {
        return outcomes(c, FAKES, random);
    }

    static Run quote(Outcomes o, String id, String amount) {
        return o.quote(id, new BigDecimal(amount), "GBP", "USD");
    }

    static String servedBy(Run run) {
        return run.step("convert").route().served().resolved().toString();
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> comparisons() {
        return (List<Map<String, Object>>) evidence.summary().get("comparisons");
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> outcomesSeen() {
        return (List<Map<String, Object>>) evidence.summary().get("outcomes");
    }

    @Test
    void championOnlyServesAndRecordsNoComparison() {
        var r = quote(outcomes(new AddressTable.Capability(role(PY, "even"), null, null), 0.5), "r1", "10.02");
        assertThat(r.result().path("amount").asText()).isEqualTo("12.52");
        assertThat(servedBy(r)).isEqualTo(PY);
        assertThat(comparisons()).isEmpty();
        assertThat(outcomesSeen()).isEmpty();
    }

    @Test
    void shadowIsComparedButNeverServed() {
        var o = outcomes(new AddressTable.Capability(role(PY, "even"), role(JAVA, "up"), null), 0.5);
        var r = quote(o, "r1", "10.02");   // 12.525: rules disagree
        quote(o, "r2", "10.06");           // 12.575: rules agree
        assertThat(r.result().path("amount").asText()).isEqualTo("12.52");
        assertThat(servedBy(r)).isEqualTo(PY);

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
        var r = quote(outcomes(new AddressTable.Capability(role(PY, "even"), role(JAVA, "down"), null), 0.5), "r1", "1.00");
        assertThat(r.result().path("amount").asText()).isEqualTo("1.25");
        assertThat(comparisons().get(0)).containsEntry("mismatches", 1L);
    }

    @Test
    void canaryServesItsShareAndIsComparedWithTheChampion() {
        var cap = new AddressTable.Capability(role(PY, "even"), null, canary(JAVA, "up", 10));

        var canaryCall = quote(outcomes(cap, 0.05), "r1", "10.02");
        assertThat(servedBy(canaryCall)).isEqualTo(JAVA);
        assertThat(canaryCall.result().path("amount").asText()).isEqualTo("12.53");
        assertThat(comparisons().get(0)).containsEntry("served", JAVA).containsEntry("shadow", PY)
                .containsEntry("mismatches", 1L);

        var championCall = quote(outcomes(cap, 0.50), "r2", "10.02");
        assertThat(servedBy(championCall)).isEqualTo(PY);
    }

    @Test
    void rejectsShadowAndCanaryTogether() {
        var cap = new AddressTable.Capability(role(PY, "even"), role(JAVA, "up"), canary(JAVA, "up", 10));
        assertThatThrownBy(() -> outcomes(cap, 0.5)).isInstanceOf(AddressTableException.class).hasMessageContaining("not both");
    }

    @Test
    void rejectsBadCanaryWeight() {
        var cap = new AddressTable.Capability(role(PY, "even"), null, canary(JAVA, "up", 150));
        assertThatThrownBy(() -> outcomes(cap, 0.5)).isInstanceOf(AddressTableException.class).hasMessageContaining("0-100");
    }

    // Spec 03: every call records the topology it ran on. Spec 04: a topology covers the whole plan.

    static InstanceRef instance(String identity, String address) {
        return new InstanceRef(identity, Address.parse(address), null);
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
    static Invoker signed(Invoker inner, InstanceRef who) {
        return (a, request) -> {
            var r = inner.invoke(a, request);
            return new Reply(r.envelope().with(InstanceRef.ASPECT, who.aspect()), r.timing());
        };
    }

    static final AddressTable.Capability SHADOWED = new AddressTable.Capability(role(PY, "py"), role(JAVA, "java"), null);

    @Test
    void eachCallRecordsWhoAnsweredInEachRole() {
        var o = outcomes(SHADOWED, CHECKED, 0.5);
        var first = quote(o, "r1", "10.02");
        quote(o, "r2", "10.06");

        var all = log.topologies(Address.parse("pkg:topology/quote"));
        assertThat(all).hasSize(1);
        var t = all.get(0);
        assertThat(t.caller()).isEqualTo(APP);
        assertThat(t.roles()).extracting(r -> r.step() + " " + r.role() + " " + r.instance().identity() + " " + r.handle())
                .containsExactly("convert champion py-1 py", "convert shadow java-1 java");
        assertThat(t.roles().get(0).requested()).isEqualTo(Address.parse("pkg:amorphic/pricing-calc"));
        var calls = log.calls(t.address());
        assertThat(calls).hasSize(4); // each quote's outcome request and its one convert call
        assertThat(calls.get(0).causedBy()).containsExactly("r1");
        assertThat(calls.get(1).causedBy()).containsExactly(first.request().id());
        assertThat(calls.get(0).generation()).isEqualTo(1);

        // Evidence counts the comparison under the instances compared, and names the topology.
        assertThat(comparisons().get(0)).containsEntry("topology", t.address().toString()).containsEntry("total", 2L)
                .containsEntry("instances", Map.of("served", "py-1", "shadow", "java-1"));
        @SuppressWarnings("unchecked")
        Map<String, Object> last = (Map<String, Object>) comparisons().get(0).get("lastMismatch");
        assertThat(last).containsEntry("topology", t.address().toString());
    }

    @Test
    void aServiceSwappedBetweenCallsShowsUpAsADifferentTopology() {
        // The container behind "java" is replaced between calls, with no change to the table.
        InstanceRef[] behind = {instance("java-1", JAVA)};
        Invoker java = (a, request) -> signed(rounding(RoundingMode.HALF_UP), behind[0]).invoke(a, request);
        BiFunction<Address, AddressTable.Binding, Bound> swapping = (address, binding) -> "java".equals(binding.url())
                ? new Bound(java, "java", instance("java-1", JAVA), false) : CHECKED.apply(address, binding);
        var o = outcomes(SHADOWED, swapping, 0.5);
        quote(o, "r1", "1.00");
        behind[0] = instance("java-old", JAVA);
        quote(o, "r2", "1.00");

        var all = log.topologies(Address.parse("pkg:topology/quote"));
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
        quote(outcomes(SHADOWED, failing, 0.5), "r1", "1.00");
        var t = log.topologies(Address.parse("pkg:topology/quote")).get(0);
        assertThat(t.roles().get(1).instance().identity()).isEqualTo("java-1");
    }

    // The account report: load the positions, convert each, add them up.

    @Test
    void theAccountReportConvertsEachPositionAndAddsThemUp() {
        var run = outcomes(new AddressTable.Capability(role(PY, "even"), null, null), 0.5).accountValue("a1", "A-1001", "USD");
        assertThat(run.failed()).isFalse();
        // The fake converts everything at 1.25: 732.98 GBP is 916.225, rounded to 916.22; 1000.00 EUR is 1250.00.
        assertThat(run.result().path("amount").asText()).isEqualTo("2416.22");
        var convert = run.step("convert");
        assertThat(convert.calls()).as("the USD cash needs no conversion").hasSize(2);
        assertThat(convert.callFor(2)).isEmpty();
        assertThat(convert.output().get(2).path("amount").asText()).isEqualTo("250.00");
    }

    @Test
    void theShadowShowsWhatTheAccountWouldHaveBeenWorth() {
        var o = outcomes(SHADOWED, CHECKED, 0.5);
        var run = o.accountValue("a1", "A-1001", "USD");
        assertThat(run.result().path("amount").asText()).as("the champion's answer").isEqualTo("2416.22");

        Map<String, Object> outcome = outcomesSeen().get(0);
        assertThat(outcome).containsEntry("outcome", "pkg:outcome/account-value@1.0").containsEntry("mismatches", 1L);
        @SuppressWarnings("unchecked")
        Map<String, Object> last = (Map<String, Object>) outcome.get("lastMismatch");
        assertThat(last.get("shadow").toString()).contains("2416.23");

        var t = log.topologies(Address.parse("pkg:topology/account-value")).get(0);
        assertThat(t.roles()).extracting(r -> r.step() + " " + r.role() + " " + r.instance().identity())
                .containsExactly("positions champion demo-app-dev/positions", "convert champion py-1",
                        "convert shadow java-1", "total champion demo-app-dev/sum");
    }

    @Test
    void aQuoteAndAReportOnTheSameInstancesCountAsOneComparison() {
        var o = outcomes(SHADOWED, CHECKED, 0.5);
        quote(o, "r1", "10.06");
        o.accountValue("a1", "A-1001", "USD");
        assertThat(comparisons()).hasSize(1);
        assertThat(comparisons().get(0)).containsEntry("total", 3L);
    }

    @Test
    void atTheCanaryEveryConversionInOneReportGoesTheSameWay() {
        var cap = new AddressTable.Capability(role(PY, "even"), null, canary(JAVA, "up", 10));
        double[] draws = {0.05, 0.95}; // a second draw would go the other way, but there is only one per plan
        int[] next = {0};
        LocalCapabilities local = new LocalCapabilities(StageAddressTablesTest.SELF, Path.of("../amorphic-demo-env/accounts.yaml"));
        ReloadableRouting routing = new ReloadableRouting(table(cap), "test", FAKES, () -> draws[next[0]++ % 2], evidence,
                Clock.systemUTC(), local::route);
        var run = new Outcomes(new Composer(StageAddressTablesTest.CATALOGUE::outcomes, routing, Runnable::run, log, APP,
                Clock.systemUTC()), evidence, Clock.systemUTC()).accountValue("a1", "A-1001", "USD");
        assertThat(next[0]).isEqualTo(1);
        assertThat(run.step("convert").route().served().resolved().toString()).isEqualTo(JAVA);
        assertThat(comparisons().get(0)).containsEntry("served", JAVA).containsEntry("total", 2L);
    }

    @Test
    void anUnknownAccountEndsThePlanAtThePositionsStep() {
        var run = outcomes(new AddressTable.Capability(role(PY, "even"), null, null), 0.5).accountValue("a1", "nobody", "USD");
        assertThat(run.failed()).isTrue();
        assertThat(run.failure().step()).isEqualTo("positions");
        assertThat(run.failure().reason()).isEqualTo("no account 'nobody'");
    }
}
