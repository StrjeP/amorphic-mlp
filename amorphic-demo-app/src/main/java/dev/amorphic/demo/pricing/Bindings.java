package dev.amorphic.demo.pricing;

import dev.amorphic.access.AdapterBinding;
import dev.amorphic.access.AdapterSpec;
import dev.amorphic.access.Invoker;
import dev.amorphic.access.LocalBinding;
import dev.amorphic.access.RemoteBinding;
import dev.amorphic.addressing.Address;
import dev.amorphic.addressing.Resolution;
import dev.amorphic.addressing.Version;
import dev.amorphic.inventory.Catalogue;
import java.util.Optional;

/**
 * Turns an address-table binding into an {@link Invoker}, failing fast when it can't be honoured. The table says
 * where (local or remote, and the url); the catalogue says how a remote version is reached (it speaks the envelope,
 * or it is wrapped by an adapter) and lists the schemas.
 */
final class Bindings {

    static final String CORE_CLASS = "dev.amorphic.pricing.PricingCalcCore";

    private Bindings() {}

    static Invoker create(Address address, AddressTable.Binding binding, Catalogue catalogue) {
        if (binding == null || binding.type() == null) {
            throw new AddressTableException("address " + address + " has no binding type");
        }
        schema(catalogue, PricingEnvelopes.REQUEST, address);
        return switch (binding.type()) {
            case "remote" -> remote(address, binding, catalogue);
            case "local" -> local(address);
            default -> throw new AddressTableException(
                    "unknown binding type '" + binding.type() + "' for " + address + " (expected remote or local)");
        };
    }

    private static Invoker remote(Address address, AddressTable.Binding binding, Catalogue catalogue) {
        if (binding.url() == null || binding.url().isBlank()) {
            throw new AddressTableException("remote binding for " + address + " has no url");
        }
        Catalogue.Version version = catalogue.find(address.name(), address).orElseThrow(() -> new AddressTableException(
                "address " + address + " is not in the catalogue, so the app can't tell how to reach it"));
        Catalogue.Access access = version.access();
        if (access == null) {
            throw new AddressTableException("the catalogue doesn't say how to reach " + address
                    + " (add access: { via: envelope } or an adapter)");
        }
        if (!access.viaAdapter()) {
            return new RemoteBinding(binding.url());
        }
        Catalogue.Adapter a = access.adapter();
        schema(catalogue, a.schema(), address);
        try {
            return new AdapterBinding(binding.url(), new AdapterSpec(a.method(), a.path(), a.query(), a.reply(), a.schema()));
        } catch (RuntimeException e) {
            throw new AddressTableException("the adapter for " + address + " in the catalogue can't be used: " + e.getMessage());
        }
    }

    /** Fails unless the catalogue resolves {@code schema}, which the calls to {@code address} rely on. */
    private static void schema(Catalogue catalogue, Address schema, Address address) {
        if (schema == null) {
            throw new AddressTableException("the adapter for " + address + " in the catalogue has no reply schema");
        }
        if (catalogue.schema(schema) instanceof Resolution.NoMatch<?> none) {
            throw new AddressTableException("calls to " + address + " use schema " + schema
                    + ", which the catalogue doesn't have: " + none.reason());
        }
    }

    private static Invoker local(Address address) {
        if (!address.qualifier("lang").filter("java"::equals).isPresent()) {
            throw new AddressTableException("local binding for " + address + " needs lang=java: only Java can run in-process");
        }
        try {
            Class.forName(CORE_CLASS, false, Bindings.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new AddressTableException("address " + address + " has a local binding, but pricing-calc-core "
                    + "is not in this build. Rebuild the app with -PfoldIn=true (./demo stage 4 does this).");
        }
        String coreVersion = LocalPricingHandler.coreVersion();
        if (!address.version().equals(Optional.of(Version.parse(coreVersion)))) {
            throw new AddressTableException("address " + address + " asks for version "
                    + address.version().map(Version::toString).orElse("none")
                    + ", but the in-process pricing-calc-core is version " + coreVersion);
        }
        return new LocalBinding(new LocalPricingHandler());
    }
}
