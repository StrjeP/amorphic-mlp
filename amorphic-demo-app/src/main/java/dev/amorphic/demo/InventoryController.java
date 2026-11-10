package dev.amorphic.demo;

import dev.amorphic.inventory.Inventory;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /inventory: observes the system now and returns what it found. The inventory runs wherever
 * the address table says; this call site doesn't change.
 */
@RestController
class InventoryController {

    static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final Inventory inventory;

    InventoryController(Inventory inventory) {
        this.inventory = inventory;
    }

    /** {@code timeoutMs} lets a remote caller hand on its own, shorter deadline. */
    @GetMapping("/inventory")
    ResponseEntity<?> inventory(@RequestParam(required = false) Long timeoutMs) {
        return switch (inventory.observe(timeout(timeoutMs, TIMEOUT))) {
            case Inventory.Observed o -> ResponseEntity.ok(o.snapshot());
            case Inventory.Unavailable u -> ResponseEntity.status(503).body(Map.of("error", u.reason()));
        };
    }

    static Duration timeout(Long requestedMs, Duration max) {
        if (requestedMs == null || requestedMs <= 0) {
            return max;
        }
        Duration d = Duration.ofMillis(requestedMs);
        return d.compareTo(max) < 0 ? d : max;
    }
}
