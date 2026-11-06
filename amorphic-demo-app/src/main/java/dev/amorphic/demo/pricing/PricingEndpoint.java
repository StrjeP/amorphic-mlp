package dev.amorphic.demo.pricing;

import java.math.BigDecimal;

/** One reachable implementation of pricing-calc, behind a remote or local binding. */
public interface PricingEndpoint {
    BigDecimal convert(BigDecimal amount, String from, String to);
}
