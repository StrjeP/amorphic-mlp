package dev.amorphic.inventory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Builds the inventory from observation on every call: the live address table, a /health probe
 * of each remote address, the evidence counters and the catalogue. Nothing is stored, so after
 * any move the next observation reflects the new shape.
 */
public final class ObservedInventory implements Inventory {

    private static final Duration MAX_PROBE = Duration.ofSeconds(2);

    private final Sources.Routing routing;
    private final Sources.Evidence evidence;
    private final Sources.Health health;
    private final Supplier<Catalogue> catalogue;
    private final int minEvidence;
    private final Clock clock;
    private final Map<String, String> knownPlacements;
    private final ExecutorService probes = Executors.newVirtualThreadPerTaskExecutor();

    public ObservedInventory(Sources.Routing routing, Sources.Evidence evidence, Sources.Health health,
            Supplier<Catalogue> catalogue, int minEvidence, Clock clock) {
        this(routing, evidence, health, catalogue, minEvidence, clock, Map.of());
    }

    /**
     * @param knownPlacements where tooling that the address table doesn't route is running, by address
     *                        (for example the inventory itself, in-process). Shown against its catalogue entry.
     */
    public ObservedInventory(Sources.Routing routing, Sources.Evidence evidence, Sources.Health health,
            Supplier<Catalogue> catalogue, int minEvidence, Clock clock, Map<String, String> knownPlacements) {
        this.knownPlacements = Map.copyOf(knownPlacements);
        this.routing = routing;
        this.evidence = evidence;
        this.health = health;
        this.catalogue = catalogue;
        this.minEvidence = minEvidence;
        this.clock = clock;
    }

    @Override
    public Result observe(Duration timeout) {
        Instant deadline = clock.instant().plus(timeout);
        RoutingView table;
        Catalogue cat;
        try {
            table = routing.current();
        } catch (Exception e) {
            return new Unavailable("cannot read the live address table: " + message(e));
        }
        try {
            cat = catalogue.get();
        } catch (Exception e) {
            return new Unavailable("cannot read the catalogue: " + message(e));
        }

        Map<String, HealthCheck> checks = probeAll(table, deadline);

        EvidenceView ev;
        try {
            // Read after the table and probes, so a table switch during observation shows as a generation mismatch.
            ev = evidence.current();
        } catch (Exception e) {
            return new Unavailable("cannot read evidence: " + message(e));
        }

        List<InventorySnapshot.Placement> placements = new ArrayList<>();
        List<String> notReady = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (RoutingView.Role r : table.roles()) {
            HealthCheck h = "local".equals(r.bindingType()) ? HealthCheck.inProcess() : checks.get(r.url());
            placements.add(new InventorySnapshot.Placement(r.capability(), r.role(), r.address(), r.bindingType(),
                    r.url(), r.weight(), h));
            if (!h.healthy()) {
                notReady.add(r.address() + " (" + r.role() + ", " + r.url() + ") is not answering /health: " + h.detail());
            }
            cat.find(r.capability(), r.address()).ifPresentOrElse(v -> {
                if (v.isWithdrawn()) {
                    problems.add(r.address() + " is routed as " + r.role() + " but is withdrawn: " + v.withdrawn());
                }
                if (!v.allows(r.bindingType())) {
                    problems.add(r.address() + " is bound " + r.bindingType() + " but the catalogue allows only "
                            + v.locations());
                }
            }, () -> problems.add(r.address() + " is routed as " + r.role() + " but is not in the catalogue"));
        }

        if (ev.generation() != table.generation()) {
            notReady.add("the address table changed while observing (table generation " + table.generation()
                    + ", evidence generation " + ev.generation() + ")");
        } else if (ev.servedTotal() < minEvidence) {
            notReady.add("only " + ev.servedTotal() + " evidence records for generation " + ev.generation()
                    + " (need " + minEvidence + ")");
        }

        return new Observed(new InventorySnapshot(clock.instant(), table.generation(), table.source(),
                notReady.isEmpty(), notReady, minEvidence, placements, ev, catalogueEntries(cat, table, knownPlacements),
                problems));
    }

    /** Probes each distinct remote URL in parallel, within the overall deadline. */
    private Map<String, HealthCheck> probeAll(RoutingView table, Instant deadline) {
        Map<String, CompletableFuture<HealthCheck>> pending = new LinkedHashMap<>();
        for (RoutingView.Role r : table.roles()) {
            if (!"local".equals(r.bindingType()) && r.url() != null && !pending.containsKey(r.url())) {
                Duration budget = min(MAX_PROBE, Duration.between(clock.instant(), deadline));
                pending.put(r.url(), CompletableFuture.supplyAsync(() -> health.check(r.url(), budget), probes));
            }
        }
        Map<String, HealthCheck> results = new LinkedHashMap<>();
        pending.forEach((url, future) -> {
            long left = Math.max(1, Duration.between(clock.instant(), deadline).toMillis());
            try {
                results.put(url, future.get(left, TimeUnit.MILLISECONDS));
            } catch (Exception e) {
                future.cancel(true);
                results.put(url, new HealthCheck(HealthCheck.Status.DOWN, url + "/health", null,
                        "no answer before the inventory's deadline"));
            }
        });
        return results;
    }

    private static List<InventorySnapshot.CatalogueEntry> catalogueEntries(Catalogue cat, RoutingView table,
            Map<String, String> known) {
        List<InventorySnapshot.CatalogueEntry> entries = new ArrayList<>();
        cat.capabilities().forEach((capability, c) -> {
            for (Catalogue.Version v : cat.versions(capability)) {
                List<String> roles = table.roles().stream()
                        .filter(r -> r.capability().equals(capability) && r.address().equals(v.address()))
                        .map(RoutingView.Role::role)
                        .toList();
                entries.add(new InventorySnapshot.CatalogueEntry(capability, cat.kind(capability), v.address(),
                        v.locations(), v.withdrawn(), v.planned(), roles, known.get(v.address())));
            }
        });
        return entries;
    }

    private static Duration min(Duration a, Duration b) {
        Duration m = a.compareTo(b) <= 0 ? a : b;
        return m.isNegative() || m.isZero() ? Duration.ofMillis(1) : m;
    }

    private static String message(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
