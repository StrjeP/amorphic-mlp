package dev.amorphic.demo;

import dev.amorphic.demo.pricing.AddressTable;
import dev.amorphic.demo.pricing.AddressTableException;
import dev.amorphic.demo.pricing.ReloadablePricingCalc;
import dev.amorphic.governor.Governor;
import dev.amorphic.governor.RuleGovernor;
import dev.amorphic.governor.RuleTable;
import dev.amorphic.governor.TableSink;
import dev.amorphic.inventory.Catalogue;
import dev.amorphic.inventory.Inventory;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Runs the governor in-process. It reads the inventory, the catalogue and the rule table (both
 * read on every proposal, so hand edits apply straight away), and an approved table goes to the
 * app's live routing.
 */
@Configuration
class GovernorConfig {

    @Bean
    Governor governor(Inventory inventory, ReloadablePricingCalc pricingCalc,
            @Value("${amorphic.catalogue}") String catalogue, @Value("${amorphic.governor.rules}") String rules) {
        Path cataloguePath = Path.of(catalogue);
        Path rulesPath = Path.of(rules);
        TableSink sink = (table, source) -> {
            try {
                return new TableSink.Accepted(pricingCalc.apply(AddressTable.parse(table, source), source).generation());
            } catch (AddressTableException e) {
                return new TableSink.Refused(e.getMessage());
            }
        };
        return new RuleGovernor(inventory, () -> Catalogue.load(cataloguePath), () -> RuleTable.load(rulesPath), sink,
                Clock.systemUTC());
    }
}
