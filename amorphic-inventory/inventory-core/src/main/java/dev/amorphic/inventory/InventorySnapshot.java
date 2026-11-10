package dev.amorphic.inventory;

import java.time.Instant;
import java.util.List;

/**
 * One observation of the system. Immutable, and built fresh on every call.
 *
 * @param ready           true when every remote address answers /health, the evidence belongs to the live
 *                        table's generation, and there is enough of it to judge
 * @param notReadyBecause why it isn't ready, in plain words (empty when ready)
 * @param placements      what the live table routes, and how each address is doing
 * @param catalogue       every catalogued version and how it is currently used
 * @param problems        mismatches between the table and the catalogue; reported, not acted on
 */
public record InventorySnapshot(
        Instant observedAt,
        long generation,
        String tableSource,
        boolean ready,
        List<String> notReadyBecause,
        int minEvidence,
        List<Placement> placements,
        EvidenceView evidence,
        List<CatalogueEntry> catalogue,
        List<String> problems) {

    /** One routed role: where it is bound and whether it answers. */
    public record Placement(String capability, String role, String address, String bindingType, String url,
            Integer weight, HealthCheck health) {}

    /**
     * One catalogued version: the roles it plays in the address table, and, for tooling the table doesn't
     * route yet, where it is actually running.
     */
    public record CatalogueEntry(String capability, String kind, String address, List<String> locations,
            String withdrawn, String planned, List<String> routedAs, String runningAs) {}

    public InventorySnapshot {
        notReadyBecause = List.copyOf(notReadyBecause);
        placements = List.copyOf(placements);
        catalogue = List.copyOf(catalogue);
        problems = List.copyOf(problems);
    }
}
