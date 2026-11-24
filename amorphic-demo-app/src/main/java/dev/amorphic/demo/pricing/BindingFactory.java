package dev.amorphic.demo.pricing;

import dev.amorphic.addressing.Address;
import dev.amorphic.addressing.Instance;
import dev.amorphic.inventory.Catalogue;
import dev.amorphic.inventory.Instances;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Public handle on the default binding factory. */
public final class BindingFactory {

    private BindingFactory() {}

    /**
     * Bindings that read the catalogue and the declared instances each time a table is resolved, so hand edits apply
     * with the next table, and that shake hands with every remote role before it is used.
     *
     * @param self the app's own instance: a local role is a part of it
     */
    public static BiFunction<Address, AddressTable.Binding, Bound> reading(Supplier<Catalogue> catalogue,
            Supplier<Instances> instances, Instance self, MetaReader meta) {
        return (address, binding) -> Bindings.bind(address, binding, catalogue.get(), instances.get(), self, meta);
    }
}
