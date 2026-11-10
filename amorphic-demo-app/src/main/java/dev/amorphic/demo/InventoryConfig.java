package dev.amorphic.demo;

import dev.amorphic.demo.evidence.EvidenceRecorder;
import dev.amorphic.demo.pricing.AddressTable;
import dev.amorphic.demo.pricing.ReloadablePricingCalc;
import dev.amorphic.inventory.Catalogue;
import dev.amorphic.inventory.EvidenceView;
import dev.amorphic.inventory.HttpHealthProbe;
import dev.amorphic.inventory.Inventory;
import dev.amorphic.inventory.ObservedInventory;
import dev.amorphic.inventory.RoutingView;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Runs the inventory in-process. Its sources read the app's live table and evidence directly;
 * health is probed over HTTP because the addresses it checks are remote.
 */
@Configuration
class InventoryConfig {

    @Bean
    Inventory inventory(ReloadablePricingCalc pricingCalc, EvidenceRecorder evidence,
            @Value("${amorphic.catalogue}") String catalogue,
            @Value("${amorphic.inventory.min-evidence}") int minEvidence) {
        Path cataloguePath = Path.of(catalogue);
        return new ObservedInventory(
                () -> routingView(pricingCalc.current()),
                () -> evidenceView(evidence.summary()),
                new HttpHealthProbe(),
                // Read on every observation: the catalogue is edited by hand and nothing is cached.
                () -> Catalogue.load(cataloguePath),
                minEvidence,
                Clock.systemUTC(),
                // Tooling the address table doesn't route yet: say where it actually runs.
                Map.of(ToolingAddresses.INVENTORY, "in-process in the demo app (this inventory)",
                        ToolingAddresses.CATALOGUE, "file " + cataloguePath));
    }

    static RoutingView routingView(ReloadablePricingCalc.Applied applied) {
        List<RoutingView.Role> roles = new ArrayList<>();
        applied.table().capabilities().forEach((capability, c) -> {
            add(roles, capability, "champion", c.champion());
            add(roles, capability, "shadow", c.shadow());
            add(roles, capability, "canary", c.canary());
        });
        return new RoutingView(applied.generation(), applied.source(), roles);
    }

    private static void add(List<RoutingView.Role> roles, String capability, String role, AddressTable.Role r) {
        if (r == null) {
            return;
        }
        String type = r.binding() == null ? null : r.binding().type();
        String url = r.binding() == null ? null : r.binding().url();
        roles.add(new RoutingView.Role(capability, role, r.address(), type, url, r.weight()));
    }

    @SuppressWarnings("unchecked")
    static EvidenceView evidenceView(Map<String, Object> summary) {
        List<EvidenceView.Served> served = new ArrayList<>();
        for (Map<String, Object> s : (List<Map<String, Object>>) summary.get("served")) {
            served.add(new EvidenceView.Served((String) s.get("address"), (String) s.get("binding"),
                    ((Number) s.get("total")).longValue()));
        }
        List<EvidenceView.Comparison> comparisons = new ArrayList<>();
        for (Map<String, Object> c : (List<Map<String, Object>>) summary.get("comparisons")) {
            comparisons.add(new EvidenceView.Comparison((String) c.get("served"), (String) c.get("shadow"),
                    ((Number) c.get("total")).longValue(), ((Number) c.get("matches")).longValue(),
                    ((Number) c.get("mismatches")).longValue(), ((Number) c.get("consecutiveMatches")).longValue()));
        }
        return new EvidenceView(((Number) summary.get("generation")).longValue(), served, comparisons);
    }
}
