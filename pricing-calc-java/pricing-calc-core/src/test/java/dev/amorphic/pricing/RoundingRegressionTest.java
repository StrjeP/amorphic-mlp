package dev.amorphic.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Regression test for the rounding divergence caught by shadow comparison in stage 1.
 *
 * <p>Evidence: request traffic-1793973261-1199, pkg:amorphic/pricing-calc@1.0?lang=python served
 * 916.22, shadow pkg:amorphic/pricing-calc@2.0?lang=java returned 916.23. 732.98 GBP x 1.25 is
 * exactly 916.225 USD; the incumbent rounds half to even (916.22), v2.0 rounded half up.
 */
class RoundingRegressionTest {

    private final PricingCalcCore calc = new PricingCalcCore();

    @Test
    void halfPennyRoundsToEvenLikeTheIncumbent() {
        assertEquals("916.22", calc.convert(new BigDecimal("732.98"), "GBP", "USD").toPlainString());
    }
}
