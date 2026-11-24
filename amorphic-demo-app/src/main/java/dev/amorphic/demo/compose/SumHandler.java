package dev.amorphic.demo.compose;

import com.fasterxml.jackson.databind.JsonNode;
import dev.amorphic.access.Handler;
import dev.amorphic.addressing.Address;
import dev.amorphic.envelope.Aspect;
import dev.amorphic.envelope.Envelope;
import java.math.BigDecimal;

/** The sum capability, in-process: adds up amounts that are all in one currency, and refuses a mix. */
final class SumHandler implements Handler {

    static final Address REPLY = Address.parse("pkg:schema/sum-reply@1.0");

    @Override
    public Envelope handle(Envelope request) {
        JsonNode body = request.payload().body();
        String currency = body.path("currency").asText();
        BigDecimal total = BigDecimal.ZERO;
        for (JsonNode a : body.path("amounts")) {
            String c = a.path("currency").asText();
            if (!currency.equals(c)) {
                return Envelope.error(request, "can't add an amount in " + c + " to a total in " + currency);
            }
            try {
                total = total.add(new BigDecimal(a.path("amount").asText()));
            } catch (NumberFormatException e) {
                return Envelope.error(request, "invalid amount '" + a.path("amount").asText() + "'");
            }
        }
        return Envelope.reply(request, new Aspect(REPLY, Envelope.body()
                .put("amount", total.toPlainString()).put("currency", currency)));
    }
}
