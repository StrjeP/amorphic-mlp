package dev.amorphic.demo;

import dev.amorphic.governor.Governor;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The governor's endpoints. Proposing never changes anything; only an explicit approval does,
 * and that is a human's call (./demo approve). Out of scope for the MLP: authentication.
 */
@RestController
class GovernorController {

    static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final Governor governor;

    GovernorController(Governor governor) {
        this.governor = governor;
    }

    /** Asks the governor to look at the system now and propose the next table, if any. */
    @PostMapping("/governor/proposals")
    ResponseEntity<Map<String, Object>> propose() {
        return switch (governor.propose(TIMEOUT)) {
            case Governor.Proposed p -> ResponseEntity.ok(body("proposed", "proposal", p.proposal()));
            case Governor.Waiting w -> ResponseEntity.ok(body("waiting", "reasons", w.reasons()));
            case Governor.Holding h -> ResponseEntity.ok(body("holding", "rule", h.rule(), "why", h.why(),
                    "details", h.details()));
            case Governor.NoChange n -> ResponseEntity.ok(body("no-change", "reason", n.reason()));
            case Governor.Unavailable u -> ResponseEntity.status(503).body(body("unavailable", "reason", u.reason()));
        };
    }

    @GetMapping("/governor/proposals/{id}")
    ResponseEntity<?> proposal(@PathVariable String id) {
        return governor.proposal(id).<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(body("not-found", "reason",
                        "no pending proposal '" + id + "'")));
    }

    /** A human approves a proposal. Applied live, unless it needs a rebuild or the system has moved on. */
    @PostMapping("/governor/proposals/{id}/approve")
    ResponseEntity<Map<String, Object>> approve(@PathVariable String id) {
        return switch (governor.approve(id, TIMEOUT)) {
            case Governor.Applied a -> ResponseEntity.ok(body("applied", "generation", a.generation(),
                    "proposal", a.proposal()));
            case Governor.NeedsRebuild r -> ResponseEntity.status(409).body(body("needs-rebuild", "how", r.how(),
                    "proposal", r.proposal()));
            case Governor.Rejected r -> ResponseEntity.status(409).body(body("rejected", "reason", r.reason()));
        };
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
