package dev.amorphic.demo;

import dev.amorphic.demo.pricing.AddressTable;
import dev.amorphic.inventory.Inventory;
import dev.amorphic.inventory.RemoteInventory;
import dev.amorphic.inventory.Tooling;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The {@link Inventory} the app's call sites use. Each call goes where the live address table's
 * {@code inventory} entry says: {@code local} (or no entry) runs it in this app, {@code remote}
 * calls the inventory service at the url. Read on every call, so a new table applies at once.
 */
final class RoutedInventory implements Inventory {

    private final Supplier<AddressTable> table;
    private final Inventory local;
    private final Map<String, RemoteInventory> remotes = new ConcurrentHashMap<>();

    RoutedInventory(Supplier<AddressTable> table, Inventory local) {
        this.table = table;
        this.local = local;
    }

    @Override
    public Result observe(Duration timeout) {
        AddressTable.Binding b = table.get().tooling(Tooling.INVENTORY);
        if (b == null || "local".equals(b.type())) {
            return local.observe(timeout);
        }
        return remotes.computeIfAbsent(b.url(), RemoteInventory::new).observe(timeout);
    }
}
