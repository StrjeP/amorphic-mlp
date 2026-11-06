package dev.amorphic.demo.pricing;

/** Turns an address-table binding into a {@link PricingEndpoint}, failing fast when it can't be honoured. */
final class Bindings {

    static final String CORE_CLASS = "dev.amorphic.pricing.PricingCalcCore";

    private Bindings() {}

    static PricingEndpoint create(Address address, AddressTable.Binding binding) {
        if (binding == null || binding.type() == null) {
            throw new AddressTableException("address " + address + " has no binding type");
        }
        return switch (binding.type()) {
            case "remote" -> {
                if (binding.url() == null || binding.url().isBlank()) {
                    throw new AddressTableException("remote binding for " + address + " has no url");
                }
                yield new RemotePricingEndpoint(binding.url());
            }
            case "local" -> local(address);
            default -> throw new AddressTableException(
                    "unknown binding type '" + binding.type() + "' for " + address + " (expected remote or local)");
        };
    }

    private static PricingEndpoint local(Address address) {
        if (!"java".equals(address.lang())) {
            throw new AddressTableException("local binding for " + address + " needs lang=java: only Java can run in-process");
        }
        try {
            Class.forName(CORE_CLASS, false, Bindings.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new AddressTableException("address " + address + " has a local binding, but pricing-calc-core "
                    + "is not in this build. Rebuild the app with -PfoldIn=true (./demo stage 4 does this).");
        }
        String coreVersion = LocalPricingEndpoint.coreVersion();
        if (!coreVersion.equals(address.version())) {
            throw new AddressTableException("address " + address + " asks for version " + address.version()
                    + ", but the in-process pricing-calc-core is version " + coreVersion);
        }
        return new LocalPricingEndpoint();
    }
}
