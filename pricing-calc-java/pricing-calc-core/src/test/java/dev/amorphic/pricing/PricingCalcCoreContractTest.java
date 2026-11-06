package dev.amorphic.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PricingCalcCoreContractTest {

    private final PricingCalcCore calc = new PricingCalcCore();

    static List<GoldenVectors.Case> golden() {
        return GoldenVectors.load();
    }

    @ParameterizedTest
    @MethodSource("golden")
    void matchesGoldenVector(GoldenVectors.Case c) {
        BigDecimal got = calc.convert(new BigDecimal(c.amount()), c.from(), c.to());
        assertEquals(c.expected(), got.toPlainString());
    }

    @Test
    void rejectsUnsupportedPair() {
        assertThrows(UnsupportedPairException.class, () -> calc.convert(BigDecimal.ONE, "USD", "GBP"));
    }

    @Test
    void rejectsNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> calc.convert(new BigDecimal("-1"), "GBP", "USD"));
    }
}
