package dev.amorphic.inventory;

import java.util.Map;

/**
 * The tooling's catalogue addresses and its address-table entries. The inventory and governor are
 * routed by the address table like any capability; with no entry they run in-process in the demo app.
 */
public final class Tooling {

    /** Address-table and catalogue names. */
    public static final String INVENTORY = "inventory";
    public static final String GOVERNOR = "governor";

    public static final String INVENTORY_ADDRESS = "pkg:amorphic/inventory@0.1?lang=java";
    public static final String GOVERNOR_ADDRESS = "pkg:amorphic/governor@0.1?lang=java";
    public static final String CATALOGUE_ADDRESS = "pkg:amorphic/catalogue@1.0?lang=yaml";

    private Tooling() {}

    /** Where the tooling runs when the address table doesn't route it. Shown against its catalogue entry. */
    public static Map<String, String> defaultPlacements(String cataloguePath) {
        return Map.of(
                INVENTORY_ADDRESS, "in-process in the demo app (no address-table entry, so the default)",
                GOVERNOR_ADDRESS, "in-process in the demo app (no address-table entry, so the default)",
                CATALOGUE_ADDRESS, "file " + cataloguePath);
    }
}
