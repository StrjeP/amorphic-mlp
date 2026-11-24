package dev.amorphic.topology;

import static org.assertj.core.api.Assertions.assertThat;

import dev.amorphic.addressing.Address;
import dev.amorphic.envelope.InstanceRef;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TopologyTest {

    static final Address V1 = Address.parse("pkg:amorphic/greeter@1.0?lang=java");
    static final Address V2 = Address.parse("pkg:amorphic/greeter@2.0?lang=python");
    static final InstanceRef HOST = new InstanceRef("host-1", Address.parse("pkg:amorphic/greeter-host@0.1?lang=java"), null);
    static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");

    static Topology.Role role(String role, Address address, String identity) {
        return new Topology.Role("greet", role, address, address, "remote", "http://greeter:8080",
                new InstanceRef(identity, address, null), false);
    }

    static Topology shape(String championIdentity) {
        return new Topology("greeter", HOST, List.of(role("champion", V1, championIdentity), role("shadow", V2, "greeter-py-1")));
    }

    @Test
    void theSameShapeHasTheSameAddress() {
        assertThat(shape("greeter-1").address()).isEqualTo(shape("greeter-1").address());
        assertThat(shape("greeter-1").address().toString()).matches("pkg:topology/greeter\\?hash=[0-9a-f]{64}");
    }

    @Test
    void aDifferentInstanceIsADifferentTopology() {
        assertThat(shape("greeter-1").id()).isNotEqualTo(shape("greeter-1b").id());
    }

    @Test
    void readsBackWhatItWrites() {
        Topology t = new Topology("greeter", HOST, List.of(
                new Topology.Role("greet", "champion", V1, V1, "local", null, InstanceRef.of(
                        dev.amorphic.addressing.Instance.part(
                                dev.amorphic.addressing.Instance.deployed(HOST.realises(), "host-1", null), "greeter", V1)), false),
                new Topology.Role("greet", "shadow", V2, V2, "remote", "http://py:8000", null, false)));
        assertThat(Topology.read(t.json())).isEqualTo(t);
        assertThat(Topology.read(t.json()).address()).isEqualTo(t.address());
    }

    @Test
    void theLogWritesEachTopologyOnceAndEveryCall(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("topology.jsonl");
        TopologyLog log = new TopologyLog(file);
        Address a = log.record(shape("greeter-1"), "e-1", List.of("r-1"), 3, NOW);
        log.record(shape("greeter-1"), "e-2", List.of("r-2"), 3, NOW.plusSeconds(1));
        Address b = log.record(shape("greeter-1b"), "e-3", List.of("r-3"), 4, NOW.plusSeconds(2));

        List<String> lines = Files.readAllLines(file);
        assertThat(lines).hasSize(5);
        assertThat(lines.stream().filter(l -> l.startsWith("{\"topology\"")).count()).isEqualTo(2);
        assertThat(log.calls(a)).extracting(CallRecord::envelope).containsExactly("e-1", "e-2");
        assertThat(log.calls(a).get(0).causedBy()).containsExactly("r-1");
        assertThat(log.calls(b).get(0).generation()).isEqualTo(4);
    }

    @Test
    void aRestartReadsTheLogBack(@TempDir Path dir) {
        Path file = dir.resolve("topology.jsonl");
        Address a = new TopologyLog(file).record(shape("greeter-1"), "e-1", List.of("r-1"), 3, NOW);

        TopologyLog again = new TopologyLog(file);
        assertThat(again.topology(a)).hasValue(shape("greeter-1"));
        assertThat(again.calls(a)).hasSize(1);
        again.record(shape("greeter-1"), "e-2", List.of(), 5, NOW);
        assertThat(new TopologyLog(file).calls(a)).extracting(CallRecord::envelope).containsExactly("e-1", "e-2");
    }

    @Test
    void aBroaderAddressListsEveryTopologyWithinIt() {
        TopologyLog log = TopologyLog.inMemory();
        log.record(shape("greeter-1"), "e-1", List.of(), 1, NOW);
        log.record(shape("greeter-1b"), "e-2", List.of(), 1, NOW);
        log.record(new Topology("other", HOST, List.of(role("champion", V1, "greeter-1"))), "e-3", List.of(), 1, NOW);
        assertThat(log.topologies(Address.parse("pkg:topology/greeter"))).hasSize(2);
        assertThat(log.topologies(shape("greeter-1").address())).containsExactly(shape("greeter-1"));
    }
}
