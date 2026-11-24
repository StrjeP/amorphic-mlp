package dev.amorphic.demo.evidence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.amorphic.access.Timing;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evidence: one JSON log line per pricing call, plus in-memory counters per served address, per comparison and per
 * outcome. A comparison is counted per pair of instances (spec 03): the exact instances compared, not just their
 * addresses, so a service swapped behind the same address is counted apart. The same two instances compared in a
 * quote and in an account report count together: the evidence is about them, whichever plan they served. Each
 * comparison names the latest topology it was seen on. An outcome's served and shadow results are counted too (spec
 * 04, section 8). Counters live only as long as the process; the topologies themselves are kept in the topology log.
 */
public class EvidenceRecorder {

    private static final Logger LOG = LoggerFactory.getLogger("evidence");

    /**
     * What happened on one call. shadow* fields are null when nothing was compared. {@code topology} is the address
     * of the topology it ran on, or null when none is recorded. {@code servedInstance} and {@code shadowInstance} are
     * the identities that answered, or null where nothing could say.
     */
    public record Call(
            long generation,
            Instant timestamp,
            String requestId,
            BigDecimal amount,
            String from,
            String to,
            String servedAddress,
            String servedBinding,
            String servedRole,
            BigDecimal servedValue,
            String shadowAddress,
            BigDecimal shadowValue,
            String shadowError,
            Timings servedTimings,
            Timings shadowTimings,
            String topology,
            String servedInstance,
            String shadowInstance) {

        public boolean compared() {
            return shadowAddress != null;
        }

        /** Strict: same digits and same scale ("12.5" does not match "12.50"). An error never matches. */
        public boolean match() {
            return shadowError == null && shadowValue != null
                    && servedValue.toPlainString().equals(shadowValue.toPlainString());
        }
    }

    /**
     * How long a call to one target took, in milliseconds. {@code serviceMs} is set only on the adapter path: the
     * time in the wrapped service, so the adapter's own cost is {@code ms - serviceMs}. The data for "the adapter
     * costs, so go native" (a future story in the walking-skeleton plan).
     */
    public record Timings(double ms, Double serviceMs) {
        public static Timings of(Timing t) {
            return t == null ? null : new Timings(millis(t.total()), t.serviceTime().map(EvidenceRecorder::millis).orElse(null));
        }
    }

    private static double millis(Duration d) {
        return Math.round(d.toNanos() / 1_000.0) / 1_000.0;
    }

    private record ServedKey(String address, String binding) {}

    private record PairKey(String served, String shadow, String servedInstance, String shadowInstance) {}

    private static final class PairStats {
        long total;
        long matches;
        long mismatches;
        long consecutiveMatches;
        String topology;
        Map<String, Object> lastMismatch;
    }

    /**
     * One outcome whose plan had a comparison: the result the caller got, and what it would have been on the
     * compared answers ({@code shadowResult}, or {@code shadowError} saying why there is none).
     */
    public record OutcomeCall(long generation, Instant timestamp, String requestId, String outcome, JsonNode served,
            JsonNode shadowResult, String shadowError, String topology) {

        public boolean match() {
            return shadowError == null && served.equals(shadowResult);
        }
    }

    private static final class OutcomeStats {
        long total;
        long matches;
        long mismatches;
        Map<String, Object> lastMismatch;
    }

    private final ObjectMapper json;
    /** The address table generation whose evidence is being counted. Older records are dropped. */
    private long generation;
    private final Map<ServedKey, Long> served = new LinkedHashMap<>();
    private final Map<PairKey, PairStats> pairs = new LinkedHashMap<>();
    private final Map<String, OutcomeStats> outcomes = new LinkedHashMap<>();

    public EvidenceRecorder(ObjectMapper json) {
        this.json = json;
    }

    public void record(Call call) {
        synchronized (this) {
            if (call.generation() != generation) {
                // A call that started under a previous address table finished after the switch.
                return;
            }
            served.merge(new ServedKey(call.servedAddress(), call.servedBinding()), 1L, Long::sum);
            if (call.compared()) {
                PairStats s = pairs.computeIfAbsent(new PairKey(call.servedAddress(), call.shadowAddress(),
                        call.servedInstance(), call.shadowInstance()), k -> new PairStats());
                s.total++;
                s.topology = call.topology();
                if (call.match()) {
                    s.matches++;
                    s.consecutiveMatches++;
                } else {
                    s.mismatches++;
                    s.consecutiveMatches = 0;
                    s.lastMismatch = mismatchExample(call);
                }
            }
        }
        log(call);
    }

    /** Counts one outcome's served result against its shadow result. */
    public synchronized void outcome(OutcomeCall call) {
        if (call.generation() != generation) {
            return;
        }
        OutcomeStats s = outcomes.computeIfAbsent(call.outcome(), k -> new OutcomeStats());
        s.total++;
        if (call.match()) {
            s.matches++;
        } else {
            s.mismatches++;
            s.lastMismatch = ordered(
                    "requestId", call.requestId(),
                    "timestamp", call.timestamp().toString(),
                    "served", call.served(),
                    "shadow", call.shadowResult(),
                    "shadowError", call.shadowError(),
                    "topology", call.topology());
        }
    }

    public synchronized Map<String, Object> summary() {
        List<Map<String, Object>> servedOut = new ArrayList<>();
        served.forEach((k, total) -> servedOut.add(ordered(
                "address", k.address(), "binding", k.binding(), "total", total)));
        List<Map<String, Object>> comparisons = new ArrayList<>();
        pairs.forEach((k, s) -> comparisons.add(ordered(
                "served", k.served(), "shadow", k.shadow(), "instances",
                ordered("served", k.servedInstance(), "shadow", k.shadowInstance()), "topology", s.topology,
                "total", s.total, "matches", s.matches,
                "mismatches", s.mismatches, "consecutiveMatches", s.consecutiveMatches,
                "lastMismatch", s.lastMismatch)));
        List<Map<String, Object>> outcomeOut = new ArrayList<>();
        outcomes.forEach((outcome, s) -> outcomeOut.add(ordered("outcome", outcome, "total", s.total,
                "matches", s.matches, "mismatches", s.mismatches, "lastMismatch", s.lastMismatch)));
        return ordered("generation", generation, "served", servedOut, "comparisons", comparisons,
                "outcomes", outcomeOut);
    }

    /** Clears the counters and keeps counting the current generation. */
    public synchronized void reset() {
        served.clear();
        pairs.clear();
        outcomes.clear();
    }

    /** Clears the counters and starts counting a new address table generation. */
    public synchronized void reset(long newGeneration) {
        reset();
        generation = newGeneration;
    }

    public synchronized long generation() {
        return generation;
    }

    private static Map<String, Object> mismatchExample(Call call) {
        return ordered(
                "requestId", call.requestId(),
                "timestamp", call.timestamp().toString(),
                "input", ordered("amount", call.amount().toPlainString(), "from", call.from(), "to", call.to()),
                "servedValue", call.servedValue().toPlainString(),
                "shadowValue", call.shadowValue() == null ? null : call.shadowValue().toPlainString(),
                "shadowError", call.shadowError(),
                "topology", call.topology());
    }

    private void log(Call call) {
        Map<String, Object> line = ordered(
                "timestamp", call.timestamp().toString(),
                "generation", call.generation(),
                "requestId", call.requestId(),
                "input", ordered("amount", call.amount().toPlainString(), "from", call.from(), "to", call.to()),
                "address_served", call.servedAddress(),
                "binding_served", call.servedBinding(),
                "role_served", call.servedRole(),
                "address_shadow", call.shadowAddress(),
                "served_value", call.servedValue().toPlainString(),
                "shadow_value", call.shadowValue() == null ? null : call.shadowValue().toPlainString(),
                "match", call.compared() ? call.match() : null,
                "topology", call.topology());
        if (call.shadowError() != null) {
            line.put("shadow_error", call.shadowError());
        }
        timings(line, "served", call.servedTimings());
        timings(line, "shadow", call.shadowTimings());
        try {
            LOG.info(json.writeValueAsString(line));
        } catch (JsonProcessingException e) {
            LOG.warn("could not serialise evidence record for {}", call.requestId(), e);
        }
    }

    private static void timings(Map<String, Object> line, String who, Timings t) {
        if (t != null) {
            line.put(who + "_ms", t.ms());
            if (t.serviceMs() != null) {
                line.put(who + "_service_ms", t.serviceMs());
            }
        }
    }

    /** LinkedHashMap that keeps key order and allows null values (Map.of does neither). */
    private static Map<String, Object> ordered(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
