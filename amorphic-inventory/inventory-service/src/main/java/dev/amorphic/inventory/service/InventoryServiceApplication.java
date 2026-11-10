package dev.amorphic.inventory.service;

import dev.amorphic.inventory.Catalogue;
import dev.amorphic.inventory.HttpHealthProbe;
import dev.amorphic.inventory.HttpSources;
import dev.amorphic.inventory.Inventory;
import dev.amorphic.inventory.ObservedInventory;
import dev.amorphic.inventory.Tooling;
import java.nio.file.Path;
import java.security.Security;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * The inventory as its own service, for a {@code remote} binding of the address table's
 * {@code inventory} entry. It observes exactly what the in-process inventory does, reading the demo
 * app's live table and evidence over HTTP. It stores nothing.
 */
@SpringBootApplication
public class InventoryServiceApplication {

    public static void main(String[] args) {
        // Short DNS caching, so a restarted container is seen quickly (as in the demo app).
        Security.setProperty("networkaddress.cache.ttl", "5");
        Security.setProperty("networkaddress.cache.negative.ttl", "1");
        SpringApplication.run(InventoryServiceApplication.class, args);
    }

    @Bean
    Inventory inventory(@Value("${amorphic.app.url}") String appUrl, @Value("${amorphic.catalogue}") String catalogue,
            @Value("${amorphic.inventory.min-evidence}") int minEvidence) {
        Path cataloguePath = Path.of(catalogue);
        return new ObservedInventory(HttpSources.routing(appUrl), HttpSources.evidence(appUrl), new HttpHealthProbe(),
                () -> Catalogue.load(cataloguePath), minEvidence, Clock.systemUTC(),
                Tooling.defaultPlacements(cataloguePath.toString()));
    }
}
