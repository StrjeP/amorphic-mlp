package dev.amorphic.demo.pricing;

import dev.amorphic.access.Handler;
import dev.amorphic.envelope.Envelope;
import dev.amorphic.pricing.PricingCalcCore;
import java.math.BigDecimal;

/**
 * pricing-calc-core in-process, answering envelopes for a {@code local} binding. Only loaded after {@link Bindings}
 * has checked that core is on the classpath.
 */
class LocalPricingHandler implements Handler {

    static String coreVersion() {
        return PricingCalcCore.VERSION;
    }

    private final PricingCalcCore core = new PricingCalcCore();

    @Override
    public Envelope handle(Envelope request) {
        var body = request.payload().body();
        String amount = body.path("amount").asText();
        String to = body.path("to").asText();
        try {
            BigDecimal value;
            try {
                value = new BigDecimal(amount);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid amount '" + amount + "'");
            }
            return PricingEnvelopes.reply(request, core.convert(value, body.path("from").asText(), to), to);
        } catch (IllegalArgumentException e) {
            return Envelope.error(request, e.getMessage());
        }
    }
}
