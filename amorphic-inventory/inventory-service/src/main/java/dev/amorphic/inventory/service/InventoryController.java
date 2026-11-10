package dev.amorphic.inventory.service;

import dev.amorphic.inventory.Inventory;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /inventory and GET /health, in the same shape as the demo app's. */
@RestController
class InventoryController {

    static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final Inventory inventory;

    InventoryController(Inventory inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/inventory")
    ResponseEntity<?> observe(@RequestParam(required = false) Long timeoutMs) {
        Duration t = timeoutMs == null || timeoutMs <= 0 || timeoutMs > TIMEOUT.toMillis() ? TIMEOUT
                : Duration.ofMillis(timeoutMs);
        return switch (inventory.observe(t)) {
            case Inventory.Observed o -> ResponseEntity.ok(o.snapshot());
            case Inventory.Unavailable u -> ResponseEntity.status(503).body(Map.of("error", u.reason()));
        };
    }

    @GetMapping("/health")
    Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
