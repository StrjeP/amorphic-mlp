package dev.amorphic.governor.service;

import dev.amorphic.governor.Governor;
import dev.amorphic.governor.GovernorHttp;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The governor's endpoints, in the same shapes as the demo app's /governor/..., and GET /health. */
@RestController
class GovernorController {

    static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final Governor governor;

    GovernorController(Governor governor) {
        this.governor = governor;
    }

    /** Asks the governor to look at the system now and propose the next table, if any. */
    @PostMapping("/governor/proposals")
    ResponseEntity<Map<String, Object>> propose(@RequestParam(required = false) Long timeoutMs) {
        return reply(GovernorHttp.propose(governor.propose(timeout(timeoutMs))));
    }

    @GetMapping("/governor/proposals/{id}")
    ResponseEntity<?> proposal(@PathVariable String id) {
        return governor.proposal(id).<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> reply(GovernorHttp.notFound(id)));
    }

    @PostMapping("/governor/proposals/{id}/approve")
    ResponseEntity<Map<String, Object>> approve(@PathVariable String id,
            @RequestParam(required = false) Long timeoutMs) {
        return reply(GovernorHttp.approve(governor.approve(id, timeout(timeoutMs))));
    }

    @GetMapping("/health")
    Map<String, String> health() {
        return Map.of("status", "ok");
    }

    private static Duration timeout(Long ms) {
        return ms == null || ms <= 0 || ms > TIMEOUT.toMillis() ? TIMEOUT : Duration.ofMillis(ms);
    }

    private static ResponseEntity<Map<String, Object>> reply(GovernorHttp.Reply r) {
        return ResponseEntity.status(r.status()).body(r.body());
    }
}
