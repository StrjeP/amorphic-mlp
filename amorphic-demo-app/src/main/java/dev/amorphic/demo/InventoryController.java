package dev.amorphic.demo;

import dev.amorphic.inventory.Inventory;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /inventory: observes the system now and returns what it found. */
@RestController
class InventoryController {

    static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final Inventory inventory;

    InventoryController(Inventory inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/inventory")
    ResponseEntity<?> inventory() {
        return switch (inventory.observe(TIMEOUT)) {
            case Inventory.Observed o -> ResponseEntity.ok(o.snapshot());
            case Inventory.Unavailable u -> ResponseEntity.status(503).body(Map.of("error", u.reason()));
        };
    }
}
