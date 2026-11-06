package dev.amorphic.demo.pricing;

import java.util.function.BiFunction;

/** Public handle on the default binding factory. */
public final class BindingFactory {
    public static final BiFunction<Address, AddressTable.Binding, PricingEndpoint> DEFAULT = Bindings::create;

    private BindingFactory() {}
}
