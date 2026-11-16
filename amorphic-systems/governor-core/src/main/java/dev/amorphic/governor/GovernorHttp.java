package dev.amorphic.governor;

import com.fasterxml.jackson.databind.JsonNode;
import dev.amorphic.inventory.Json;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The governor's HTTP shape, defined once: the demo app, the governor service and
 * {@link RemoteGovernor} all use it, so a result means the same thing local or remote.
 */
public final class GovernorHttp {

    private GovernorHttp() {}

    /** An HTTP status and a JSON body. */
    public record Reply(int status, Map<String, Object> body) {}

    public static Reply propose(Governor.ProposeResult result) {
        return switch (result) {
            case Governor.Proposed p -> new Reply(200, body("proposed", "proposal", p.proposal()));
            case Governor.Waiting w -> new Reply(200, body("waiting", "reasons", w.reasons()));
            case Governor.Holding h -> new Reply(200, body("holding", "rule", h.rule(), "why", h.why(),
                    "details", h.details()));
            case Governor.NoChange n -> new Reply(200, body("no-change", "reason", n.reason()));
            case Governor.Unavailable u -> new Reply(503, body("unavailable", "reason", u.reason()));
        };
    }

    public static Reply approve(Governor.ApproveResult result) {
        return switch (result) {
            case Governor.Applied a -> new Reply(200, body("applied", "generation", a.generation(),
                    "proposal", a.proposal()));
            case Governor.NeedsRebuild r -> new Reply(409, body("needs-rebuild", "how", r.how(),
                    "proposal", r.proposal()));
            case Governor.Rejected r -> new Reply(409, body("rejected", "reason", r.reason()));
        };
    }

    public static Reply notFound(String id) {
        return new Reply(404, body("not-found", "reason", "no pending proposal '" + id + "'"));
    }

    static Governor.ProposeResult readPropose(String json) throws Exception {
        JsonNode n = Json.MAPPER.readTree(json);
        return switch (n.path("status").asText()) {
            case "proposed" -> new Governor.Proposed(proposal(n.get("proposal")));
            case "waiting" -> new Governor.Waiting(strings(n.get("reasons")));
            case "holding" -> new Governor.Holding(n.path("rule").asText(), n.path("why").asText(),
                    strings(n.get("details")));
            case "no-change" -> new Governor.NoChange(n.path("reason").asText());
            case "unavailable" -> new Governor.Unavailable(n.path("reason").asText());
            default -> throw new IllegalArgumentException("unknown status '" + n.path("status").asText() + "'");
        };
    }

    static Governor.ApproveResult readApprove(String json) throws Exception {
        JsonNode n = Json.MAPPER.readTree(json);
        return switch (n.path("status").asText()) {
            case "applied" -> new Governor.Applied(n.get("generation").asLong(), proposal(n.get("proposal")));
            case "needs-rebuild" -> new Governor.NeedsRebuild(proposal(n.get("proposal")), n.path("how").asText());
            case "rejected", "not-found" -> new Governor.Rejected(n.path("reason").asText());
            default -> throw new IllegalArgumentException("unknown status '" + n.path("status").asText() + "'");
        };
    }

    static Proposal proposal(JsonNode n) throws Exception {
        return Json.MAPPER.treeToValue(n, Proposal.class);
    }

    private static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        if (array != null) {
            array.forEach(s -> out.add(s.asText()));
        }
        return out;
    }

    private static Map<String, Object> body(String status, Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status);
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
