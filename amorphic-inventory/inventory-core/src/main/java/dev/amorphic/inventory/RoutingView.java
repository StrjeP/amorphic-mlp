package dev.amorphic.inventory;

import java.util.List;

/** What the live address table routes, as the inventory sees it. */
public record RoutingView(long generation, String source, List<Role> roles) {

    /** One role of one capability. {@code url} is null for local bindings; {@code weight} only for canaries. */
    public record Role(String capability, String role, String address, String bindingType, String url, Integer weight) {}

    public RoutingView {
        roles = List.copyOf(roles);
    }
}
