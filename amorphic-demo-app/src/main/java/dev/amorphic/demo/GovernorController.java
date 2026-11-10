package dev.amorphic.demo;

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

/**
 * The governor's endpoints. Proposing never changes anything; only an explicit approval does,
 * and that is a human's call (./demo approve). The governor runs wherever the address table says;
 * this call site doesn't change. Out of scope for the MLP: authentication.
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
    ResponseEntity<Map<String, Object>> propose(@RequestParam(required = false) Long timeoutMs) {
        return reply(GovernorHttp.propose(governor.propose(InventoryController.timeout(timeoutMs, TIMEOUT))));
    }

    @GetMapping("/governor/proposals/{id}")
    ResponseEntity<?> proposal(@PathVariable String id) {
        return governor.proposal(id).<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> reply(GovernorHttp.notFound(id)));
    }

    /** A human approves a proposal. Applied live, unless it needs a rebuild or the system has moved on. */
    @PostMapping("/governor/proposals/{id}/approve")
    ResponseEntity<Map<String, Object>> approve(@PathVariable String id,
            @RequestParam(required = false) Long timeoutMs) {
        return reply(GovernorHttp.approve(governor.approve(id, InventoryController.timeout(timeoutMs, TIMEOUT))));
    }

    private static ResponseEntity<Map<String, Object>> reply(GovernorHttp.Reply r) {
        return ResponseEntity.status(r.status()).body(r.body());
    }
}
