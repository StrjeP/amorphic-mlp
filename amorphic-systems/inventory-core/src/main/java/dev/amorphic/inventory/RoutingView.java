package dev.amorphic.inventory;

import dev.amorphic.addressing.Address;
import java.util.List;

/** What the live address table routes, as the inventory sees it. */
public record RoutingView(long generation, String source, List<Role> roles) {

    /** One role of one capability. {@code url} is null for local bindings; {@code weight} only for canaries. */
    public record Role(String capability, String role, String address, String bindingType, String url, Integer weight) {
        /** The address, read. Throws {@link dev.amorphic.addressing.AddressException} if it can't be. */
        public Address parsedAddress() {
            return Address.parse(address);
        }
    }

    public RoutingView {
        roles = List.copyOf(roles);
    }
}
