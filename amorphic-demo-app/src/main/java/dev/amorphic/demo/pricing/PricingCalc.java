package dev.amorphic.demo.pricing;

import java.math.BigDecimal;

/**
 * The app's one call site for pricing. What actually runs behind it is decided by the
 * address table, never by the caller.
 */
public interface PricingCalc {

    Result convert(String requestId, BigDecimal amount, String from, String to);

    record Result(BigDecimal amount, String currency, String servedBy, String binding) {}
}
