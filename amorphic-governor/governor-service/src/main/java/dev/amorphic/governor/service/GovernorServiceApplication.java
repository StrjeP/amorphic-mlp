package dev.amorphic.governor.service;

import dev.amorphic.governor.Governor;
import dev.amorphic.governor.HttpTableSink;
import dev.amorphic.governor.RuleGovernor;
import dev.amorphic.governor.RuleTable;
import dev.amorphic.inventory.Catalogue;
import dev.amorphic.inventory.RemoteInventory;
import java.nio.file.Path;
import java.security.Security;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * The governor as its own service, for a {@code remote} binding of the address table's
 * {@code governor} entry. It asks the demo app's /inventory, which the app routes to wherever the
 * table puts the inventory, and sends approved tables to the app's /admin/address-table.
 * Proposals are held in memory, as in-process.
 */
@SpringBootApplication
public class GovernorServiceApplication {

    public static void main(String[] args) {
        // Short DNS caching, so a restarted container is seen quickly (as in the demo app).
        Security.setProperty("networkaddress.cache.ttl", "5");
        Security.setProperty("networkaddress.cache.negative.ttl", "1");
        SpringApplication.run(GovernorServiceApplication.class, args);
    }

    @Bean
    Governor governor(@Value("${amorphic.app.url}") String appUrl, @Value("${amorphic.catalogue}") String catalogue,
            @Value("${amorphic.governor.rules}") String rules) {
        Path cataloguePath = Path.of(catalogue);
        Path rulesPath = Path.of(rules);
        return new RuleGovernor(new RemoteInventory(appUrl), () -> Catalogue.load(cataloguePath),
                () -> RuleTable.load(rulesPath), new HttpTableSink(appUrl), Clock.systemUTC());
    }
}
