package dev.amorphic.demo.evidence;

import com.fasterxml.jackson.core.JsonProcessingException;
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
 * Evidence: one JSON log line per call, plus in-memory counters per served address and per comparison. A
 * comparison is counted per topology (spec 03): the exact instances compared, not just their addresses, so a
 * service swapped behind the same address is counted apart. Counters live only as long as the process; the
 * topologies themselves are kept in the topology log.
 */
public class EvidenceRecorder {

    private static final Logger LOG = LoggerFactory.getLogger("evidence");

    /**
     * What happened on one call. shadow* fields are null when nothing was compared. {@code topology} is the address
     * of the topology it ran on, or null when none is recorded.
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
            String topology) {

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

    private record PairKey(String served, String shadow, String topology) {}

    private static final class PairStats {
        long total;
        long matches;
        long mismatches;
        long consecutiveMatches;
        Map<String, Object> lastMismatch;
    }

    private final ObjectMapper json;
    /** The address table generation whose evidence is being counted. Older records are dropped. */
    private long generation;
    private final Map<ServedKey, Long> served = new LinkedHashMap<>();
    private final Map<PairKey, PairStats> pairs = new LinkedHashMap<>();

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
                PairStats s = pairs.computeIfAbsent(new PairKey(call.servedAddress(), call.shadowAddress(), call.topology()),
                        k -> new PairStats());
                s.total++;
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

    public synchronized Map<String, Object> summary() {
        List<Map<String, Object>> servedOut = new ArrayList<>();
        served.forEach((k, total) -> servedOut.add(ordered(
                "address", k.address(), "binding", k.binding(), "total", total)));
        List<Map<String, Object>> comparisons = new ArrayList<>();
        pairs.forEach((k, s) -> comparisons.add(ordered(
                "served", k.served(), "shadow", k.shadow(), "topology", k.topology(), "total", s.total, "matches", s.matches,
                "mismatches", s.mismatches, "consecutiveMatches", s.consecutiveMatches,
                "lastMismatch", s.lastMismatch)));
        return ordered("generation", generation, "served", servedOut, "comparisons", comparisons);
    }

    /** Clears the counters and keeps counting the current generation. */
    public synchronized void reset() {
        served.clear();
        pairs.clear();
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
