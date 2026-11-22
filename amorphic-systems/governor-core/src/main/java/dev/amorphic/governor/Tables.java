package dev.amorphic.governor;

import dev.amorphic.inventory.RoutingView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Renders address tables and guards the entries only a human may change. */
public final class Tables {

    /** Capabilities whose entries the governor must never change: its own and the inventory's. */
    public static final Set<String> HUMAN_ONLY = Set.of("governor", "inventory");

    private static final List<String> ROLE_ORDER = List.of("champion", "shadow", "canary");

    private Tables() {}

    /** Renders roles as an address table, in exactly the layout of the stage files. */
    public static String render(List<RoutingView.Role> roles) {
        Map<String, List<RoutingView.Role>> byCapability = new LinkedHashMap<>();
        for (RoutingView.Role r : roles) {
            byCapability.computeIfAbsent(r.capability(), k -> new ArrayList<>()).add(r);
        }
        StringBuilder out = new StringBuilder("capabilities:\n");
        byCapability.forEach((capability, list) -> {
            out.append("  ").append(capability).append(":\n");
            for (String role : ROLE_ORDER) {
                list.stream().filter(r -> r.role().equals(role)).findFirst().ifPresent(r -> {
                    out.append("    ").append(role).append(":\n");
                    out.append("      address: ").append(r.address()).append('\n');
                    if ("local".equals(r.bindingType())) {
                        out.append("      binding: { type: local }\n");
                    } else {
                        out.append("      binding: { type: ").append(r.bindingType()).append(", url: ").append(r.url())
                                .append(" }\n");
                    }
                    if (r.weight() != null) {
                        out.append("      weight: ").append(r.weight()).append('\n');
                    }
                });
            }
        });
        return out.toString();
    }

    /**
     * Fails if {@code proposed} changes, adds or removes any human-only entry compared with {@code current}.
     * Checked on every proposal and again before anything is applied.
     */
    public static void guardHumanOnly(List<RoutingView.Role> current, List<RoutingView.Role> proposed) {
        for (String capability : HUMAN_ONLY) {
            List<RoutingView.Role> before = current.stream().filter(r -> r.capability().equals(capability)).toList();
            List<RoutingView.Role> after = proposed.stream().filter(r -> r.capability().equals(capability)).toList();
            if (!Objects.equals(before, after)) {
                throw new IllegalStateException("the governor may not change the " + capability
                        + " entry in the address table; only a human can (./demo apply)");
            }
        }
    }
}
