package dev.amorphic.topology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.amorphic.addressing.Address;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Topologies and the calls that ran on them, appended and never changed (spec 03, section 7). One file of JSON
 * lines: each topology is written once, the first time it is seen ({@code {"topology": ...}}), and each call every
 * time ({@code {"call": ...}}). On start it reads the file back, so a topology's address still resolves after a
 * restart. With no file it keeps everything in memory only.
 */
public final class TopologyLog {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path file;
    private final Map<Address, Topology> topologies = new LinkedHashMap<>();
    private final Map<Address, List<CallRecord>> calls = new LinkedHashMap<>();

    public TopologyLog(Path file) {
        this.file = file;
        if (file != null && Files.exists(file)) {
            readBack();
        }
    }

    public static TopologyLog inMemory() {
        return new TopologyLog(null);
    }

    /**
     * Records one call on {@code topology}, writing the topology first if it is new.
     *
     * @return the topology's address, which the call record names
     */
    public synchronized Address record(Topology topology, String envelope, List<String> causedBy, long generation,
            Instant at) {
        Address address = topology.address();
        StringBuilder lines = new StringBuilder();
        if (!topologies.containsKey(address)) {
            ObjectNode t = topology.json();
            t.put("address", address.toString());
            lines.append(line("topology", t)).append('\n');
        }
        CallRecord call = new CallRecord(envelope, causedBy, generation, address, at);
        lines.append(line("call", callJson(call))).append('\n');
        append(lines.toString());
        topologies.putIfAbsent(address, topology);
        calls.computeIfAbsent(address, a -> new ArrayList<>()).add(call);
        return address;
    }

    /** The topology at exactly {@code address}. */
    public synchronized Optional<Topology> topology(Address address) {
        return Optional.ofNullable(topologies.get(address));
    }

    /** Every topology within {@code address}: {@code pkg:topology/pricing-calc} lists all of pricing-calc's. */
    public synchronized List<Topology> topologies(Address address) {
        return topologies.entrySet().stream().filter(e -> e.getKey().within(address)).map(Map.Entry::getValue).toList();
    }

    /** Every call that ran on the topology at {@code address}, oldest first. */
    public synchronized List<CallRecord> calls(Address address) {
        return List.copyOf(calls.getOrDefault(address, List.of()));
    }

    private static String line(String field, JsonNode value) {
        ObjectNode o = JSON.createObjectNode();
        o.set(field, value);
        return o.toString();
    }

    static ObjectNode callJson(CallRecord c) {
        ObjectNode o = JSON.createObjectNode();
        o.put("envelope", c.envelope());
        var causedBy = o.putArray("causedBy");
        c.causedBy().forEach(causedBy::add);
        o.put("generation", c.generation());
        o.put("topology", c.topology().toString());
        o.put("at", c.at().toString());
        return o;
    }

    private void append(String lines) {
        if (file == null) {
            return;
        }
        try {
            Files.writeString(file, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot append to topology log " + file, e);
        }
    }

    private void readBack() {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read topology log " + file, e);
        }
        int n = 0;
        for (String line : lines) {
            n++;
            if (line.isBlank()) {
                continue;
            }
            try {
                JsonNode node = JSON.readTree(line);
                if (node.has("topology")) {
                    Topology t = Topology.read(node.get("topology"));
                    topologies.putIfAbsent(t.address(), t);
                } else if (node.has("call")) {
                    JsonNode c = node.get("call");
                    List<String> causedBy = new ArrayList<>();
                    c.path("causedBy").forEach(id -> causedBy.add(id.asText()));
                    CallRecord call = new CallRecord(c.path("envelope").asText(), causedBy, c.path("generation").asLong(),
                            Address.parse(c.path("topology").asText()), Instant.parse(c.path("at").asText()));
                    calls.computeIfAbsent(call.topology(), a -> new ArrayList<>()).add(call);
                }
            } catch (IOException | RuntimeException e) {
                throw new IllegalStateException("topology log " + file + ", line " + n + " can't be read: " + e.getMessage(), e);
            }
        }
    }
}
