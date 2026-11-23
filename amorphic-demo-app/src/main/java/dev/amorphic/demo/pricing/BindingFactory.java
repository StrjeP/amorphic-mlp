package dev.amorphic.demo.pricing;

import dev.amorphic.access.Invoker;
import dev.amorphic.addressing.Address;
import dev.amorphic.inventory.Catalogue;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Public handle on the default binding factory. */
public final class BindingFactory {

    private BindingFactory() {}

    /**
     * Bindings that read the catalogue each time a table is resolved, so hand edits to it apply with the next table.
     */
    public static BiFunction<Address, AddressTable.Binding, Invoker> reading(Supplier<Catalogue> catalogue) {
        return (address, binding) -> Bindings.create(address, binding, catalogue.get());
    }
}
