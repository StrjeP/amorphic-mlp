package dev.amorphic.demo.pricing;

import dev.amorphic.access.Reply;
import dev.amorphic.addressing.Address;
import dev.amorphic.envelope.Aspect;
import dev.amorphic.envelope.Envelope;
import java.math.BigDecimal;

/**
 * Pricing-calc's payloads, by schema address (listed in catalogue.yaml): a convert request, and its reply. Failures
 * use the framework's error schema.
 */
public final class PricingEnvelopes {

    public static final Address REQUEST = Address.parse("pkg:schema/pricing-calc-convert-request@1.0");
    public static final Address REPLY = Address.parse("pkg:schema/pricing-calc-convert-reply@1.0");

    private PricingEnvelopes() {}

    /** One convert request, caused by the incoming call's request id. Amounts travel as text, so nothing is lost. */
    public static Envelope request(String requestId, BigDecimal amount, String from, String to) {
        return Envelope.request(new Aspect(REQUEST, Envelope.body()
                .put("amount", amount.toPlainString()).put("from", from).put("to", to)), requestId);
    }

    public static Envelope reply(Envelope request, BigDecimal amount, String currency) {
        return Envelope.reply(request, new Aspect(REPLY, Envelope.body()
                .put("amount", amount.toPlainString()).put("currency", currency)));
    }

    /** The converted amount, or {@link PricingRejectedException} if the reply is a failure. */
    public static BigDecimal amount(Reply reply) {
        Envelope e = reply.envelope();
        if (e.isError()) {
            throw new PricingRejectedException(e.errorReason().orElse("rejected"));
        }
        if (!REPLY.equals(e.payload().schema())) {
            throw new IllegalStateException("expected a " + REPLY + " reply, got " + e.payload().schema());
        }
        return new BigDecimal(e.payload().body().path("amount").asText());
    }
}
