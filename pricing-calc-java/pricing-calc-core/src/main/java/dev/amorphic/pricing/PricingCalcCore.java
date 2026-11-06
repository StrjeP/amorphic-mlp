package dev.amorphic.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/** Java port of pricing-calc. Plain library: no Spring, no web layer. */
public final class PricingCalcCore {

    /** Version of this implementation, matched against the address's {@code @version}. */
    public static final String VERSION = "2.1";

    /**
     * Rounding applied to every result: half to even, matching the incumbent Python service.
     * v2.0 used HALF_UP, which shadow comparison caught on exact half-penny results.
     */
    static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private static final Map<String, BigDecimal> RATES = Map.of(
            "GBP:USD", new BigDecimal("1.25"),
            "EUR:USD", new BigDecimal("1.05"),
            "GBP:EUR", new BigDecimal("1.19"));

    public BigDecimal convert(BigDecimal amount, String from, String to) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        BigDecimal rate = RATES.get(from + ":" + to);
        if (rate == null) {
            throw new UnsupportedPairException(from, to);
        }
        return amount.multiply(rate).setScale(2, ROUNDING);
    }
}
