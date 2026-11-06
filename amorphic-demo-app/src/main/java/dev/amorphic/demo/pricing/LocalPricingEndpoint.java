package dev.amorphic.demo.pricing;

import dev.amorphic.pricing.PricingCalcCore;
import java.math.BigDecimal;

/**
 * `local` binding: pricing-calc-core running in-process. Only loaded after
 * {@link Bindings} has checked that core is on the classpath.
 */
class LocalPricingEndpoint implements PricingEndpoint {

    static String coreVersion() {
        return PricingCalcCore.VERSION;
    }

    private final PricingCalcCore core = new PricingCalcCore();

    @Override
    public BigDecimal convert(BigDecimal amount, String from, String to) {
        try {
            return core.convert(amount, from, to);
        } catch (IllegalArgumentException e) {
            throw new PricingRejectedException(e.getMessage());
        }
    }
}
