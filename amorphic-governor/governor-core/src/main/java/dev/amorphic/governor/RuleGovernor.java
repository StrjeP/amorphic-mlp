package dev.amorphic.governor;

import dev.amorphic.inventory.Catalogue;
import dev.amorphic.inventory.EvidenceView;
import dev.amorphic.inventory.Inventory;
import dev.amorphic.inventory.InventorySnapshot;
import dev.amorphic.inventory.RoutingView;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Applies the rule table to what the inventory observes and proposes the next address table.
 * Proposals are held in memory until a human approves one; nothing is persisted.
 */
public final class RuleGovernor implements Governor {

    private final Inventory inventory;
    private final Supplier<Catalogue> catalogue;
    private final Supplier<RuleTable> rules;
    private final TableSink sink;
    private final Clock clock;
    private final Map<String, Proposal> pending = new ConcurrentHashMap<>();

    public RuleGovernor(Inventory inventory, Supplier<Catalogue> catalogue, Supplier<RuleTable> rules, TableSink sink,
            Clock clock) {
        this.inventory = inventory;
        this.catalogue = catalogue;
        this.rules = rules;
        this.sink = sink;
        this.clock = clock;
    }

    @Override
    public ProposeResult propose(Duration timeout) {
        InventorySnapshot snapshot;
        switch (inventory.observe(timeout)) {
            case Inventory.Unavailable u -> {
                return new Unavailable("inventory unavailable: " + u.reason());
            }
            case Inventory.Observed o -> snapshot = o.snapshot();
        }
        if (!snapshot.ready()) {
            // After any move the inventory has to rebuild its picture first.
            return new Waiting(snapshot.notReadyBecause());
        }
        RuleTable table;
        Catalogue cat;
        try {
            table = rules.get();
            cat = catalogue.get();
        } catch (Exception e) {
            return new Unavailable(e.getMessage());
        }
        pending.values().removeIf(p -> p.basedOnGeneration() != snapshot.generation());

        List<RoutingView.Role> current = roles(snapshot);
        Set<String> capabilities = new LinkedHashSet<>();
        for (RoutingView.Role r : current) {
            if (!Tables.HUMAN_ONLY.contains(r.capability()) && "capability".equals(cat.kind(r.capability()))) {
                capabilities.add(r.capability());
            }
        }
        List<String> looked = new ArrayList<>();
        for (String capability : capabilities) {
            State s = new State(capability, current, cat, snapshot.evidence());
            for (RuleTable.Rule rule : table.rules()) {
                if (!s.matches(rule)) {
                    continue;
                }
                if (rule.propose().equals("hold")) {
                    return new Holding(rule.id(), rule.why(), s.mismatchDetails());
                }
                Optional<String> gateShortfall = s.gateShortfall(rule);
                if (gateShortfall.isPresent()) {
                    return new Waiting(List.of(gateShortfall.get()));
                }
                Optional<List<RoutingView.Role>> next = s.apply(rule);
                if (next.isEmpty()) {
                    return new NoChange("rule " + rule.id() + " matched, but the catalogue has nothing to propose");
                }
                Tables.guardHumanOnly(current, next.get());
                Proposal p = new Proposal("g" + snapshot.generation() + "-" + rule.id(), clock.instant(),
                        snapshot.generation(), capability, rule.id(), rule.why(), s.gateText(rule),
                        snapshot.evidence(), Tables.render(current), Tables.render(next.get()), next.get(),
                        rule.propose().equals("fold-in"));
                pending.put(p.id(), p);
                return new Proposed(p);
            }
            looked.add(capability + ": " + s.describe());
        }
        return new NoChange("no rule applies (" + String.join("; ", looked) + ")");
    }

    @Override
    public ApproveResult approve(String proposalId, Duration timeout) {
        Proposal p = pending.get(proposalId);
        if (p == null) {
            return new Rejected("no pending proposal '" + proposalId + "'. Ask for a new one (./demo propose).");
        }
        InventorySnapshot snapshot;
        switch (inventory.observe(timeout)) {
            case Inventory.Unavailable u -> {
                return new Rejected("cannot check the live table: " + u.reason());
            }
            case Inventory.Observed o -> snapshot = o.snapshot();
        }
        if (snapshot.generation() != p.basedOnGeneration()) {
            pending.remove(proposalId);
            return new Rejected("the address table has changed since this proposal (generation " + p.basedOnGeneration()
                    + " -> " + snapshot.generation() + "). Ask for a new one.");
        }
        try {
            Tables.guardHumanOnly(roles(snapshot), p.toRoles());
        } catch (IllegalStateException e) {
            return new Rejected(e.getMessage());
        }
        if (p.needsRebuild()) {
            return new NeedsRebuild(p, "This moves " + p.capability() + " in-process, and the running app wasn't built "
                    + "with it. Rebuild the app with this table (./demo approve does this via ./demo stage 4).");
        }
        return switch (sink.apply(p.toTable(), "governor proposal " + p.id() + " (rule " + p.rule() + ")")) {
            case TableSink.Accepted a -> {
                pending.remove(proposalId);
                yield new Applied(a.generation(), p);
            }
            case TableSink.Refused r -> new Rejected("the app refused the table: " + r.reason());
        };
    }

    @Override
    public Optional<Proposal> proposal(String proposalId) {
        return Optional.ofNullable(pending.get(proposalId));
    }

    private static List<RoutingView.Role> roles(InventorySnapshot s) {
        return s.placements().stream()
                .map(p -> new RoutingView.Role(p.capability(), p.role(), p.address(), p.bindingType(), p.url(),
                        p.weight()))
                .toList();
    }

    /** The situation of one capability, and the moves the rules can make from it. */
    private static final class State {
        final String capability;
        final List<RoutingView.Role> all;
        final Catalogue cat;
        final EvidenceView evidence;
        final RoutingView.Role champion;
        final RoutingView.Role shadow;
        final RoutingView.Role canary;

        State(String capability, List<RoutingView.Role> all, Catalogue cat, EvidenceView evidence) {
            this.capability = capability;
            this.all = all;
            this.cat = cat;
            this.evidence = evidence;
            this.champion = find("champion");
            this.shadow = find("shadow");
            this.canary = find("canary");
        }

        RoutingView.Role find(String role) {
            return all.stream().filter(r -> r.capability().equals(capability) && r.role().equals(role)).findFirst()
                    .orElse(null);
        }

        boolean alone() {
            return champion != null && shadow == null && canary == null;
        }

        boolean remote(RoutingView.Role r) {
            return r != null && "remote".equals(r.bindingType());
        }

        /** The newest catalogued version that isn't withdrawn or planned and can run as a service. */
        Optional<Catalogue.Version> newest() {
            return cat.versions(capability).stream()
                    .filter(v -> !v.isWithdrawn() && !v.isPlanned() && v.allows("remote"))
                    .max(Comparator.comparing(Catalogue.Version::address, Tables::compareVersions));
        }

        boolean newerThanChampion(Catalogue.Version v) {
            return Tables.compareVersions(v.address(), champion.address()) > 0;
        }

        boolean matches(RuleTable.Rule rule) {
            return switch (rule.when()) {
                case "shadow-withdrawn" -> shadow != null
                        && cat.find(capability, shadow.address()).map(Catalogue.Version::isWithdrawn).orElse(false);
                case "mismatch" -> evidence.comparisons().stream().anyMatch(c -> c.mismatches() > 0);
                case "champion-alone-newer" -> alone() && remote(champion)
                        && newest().map(this::newerThanChampion).orElse(false);
                case "shadow-newer" -> shadow != null && Tables.compareVersions(shadow.address(), champion.address()) > 0;
                case "canary-below" -> canary != null && canary.weight() != null && canary.weight() < rule.weight();
                case "canary-at-least" -> canary != null && canary.weight() != null && canary.weight() >= rule.weight();
                case "shadow-older" -> shadow != null && Tables.compareVersions(shadow.address(), champion.address()) < 0;
                case "champion-alone-remote" -> alone() && remote(champion)
                        && newest().map(v -> !newerThanChampion(v)).orElse(true)
                        && cat.find(capability, champion.address()).map(v -> v.allows("local")).orElse(false);
                default -> false;
            };
        }

        Optional<String> gateShortfall(RuleTable.Rule rule) {
            if (rule.gate() == null) {
                return Optional.empty();
            }
            if (evidence.comparisons().isEmpty()) {
                return Optional.of("rule " + rule.id() + " needs " + rule.gate()
                        + " consecutive matches, but nothing has been compared yet");
            }
            long worst = evidence.comparisons().stream().mapToLong(EvidenceView.Comparison::consecutiveMatches).min()
                    .orElse(0);
            if (worst < rule.gate()) {
                return Optional.of("rule " + rule.id() + " needs " + rule.gate() + " consecutive matches; " + worst
                        + " so far");
            }
            return Optional.empty();
        }

        String gateText(RuleTable.Rule rule) {
            if (rule.gate() == null) {
                return null;
            }
            long worst = evidence.comparisons().stream().mapToLong(EvidenceView.Comparison::consecutiveMatches).min()
                    .orElse(0);
            return worst + " consecutive matches, 0 mismatches (rule needs " + rule.gate() + ")";
        }

        List<String> mismatchDetails() {
            return evidence.comparisons().stream().filter(c -> c.mismatches() > 0)
                    .map(c -> c.served() + " vs " + c.shadow() + ": " + c.mismatches() + " mismatches in " + c.total()
                            + " calls")
                    .toList();
        }

        Optional<List<RoutingView.Role>> apply(RuleTable.Rule rule) {
            List<RoutingView.Role> next = new ArrayList<>();
            switch (rule.propose()) {
                case "shadow-newest" -> {
                    Optional<Catalogue.Version> n = newest().filter(this::newerThanChampion);
                    if (n.isEmpty() || n.get().service() == null) {
                        return Optional.empty();
                    }
                    next.add(champion);
                    next.add(role("shadow", n.get().address(), "remote", n.get().service().url(), null));
                }
                case "canary" -> {
                    RoutingView.Role c = canary != null ? canary : shadow;
                    next.add(champion);
                    next.add(role("canary", c.address(), c.bindingType(), c.url(), rule.weight()));
                }
                case "promote" -> {
                    next.add(role("champion", canary.address(), canary.bindingType(), canary.url(), null));
                    next.add(role("shadow", champion.address(), champion.bindingType(), champion.url(), null));
                }
                case "drop-shadow" -> next.add(champion);
                case "fold-in" -> next.add(role("champion", champion.address(), "local", null, null));
                default -> {
                    return Optional.empty();
                }
            }
            return Optional.of(inPlace(next));
        }

        /** Puts this capability's new roles where its old ones were, so other entries keep their place. */
        List<RoutingView.Role> inPlace(List<RoutingView.Role> mine) {
            List<RoutingView.Role> out = new ArrayList<>();
            boolean placed = false;
            for (RoutingView.Role r : all) {
                if (!r.capability().equals(capability)) {
                    out.add(r);
                } else if (!placed) {
                    out.addAll(mine);
                    placed = true;
                }
            }
            return out;
        }

        RoutingView.Role role(String role, String address, String type, String url, Integer weight) {
            return new RoutingView.Role(capability, role, address, type, url, weight);
        }

        String describe() {
            StringBuilder sb = new StringBuilder("champion ").append(champion.address()).append(' ')
                    .append(champion.bindingType());
            if (shadow != null) sb.append(", shadow ").append(shadow.address());
            if (canary != null) sb.append(", canary ").append(canary.address()).append(' ').append(canary.weight()).append('%');
            if (alone() && "local".equals(champion.bindingType())) sb.append(": folded in, nothing further to propose");
            return sb.toString();
        }
    }
}
