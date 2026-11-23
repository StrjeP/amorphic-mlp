package dev.amorphic.pricing.service;

import dev.amorphic.addressing.Address;
import dev.amorphic.envelope.Aspect;
import dev.amorphic.envelope.Envelope;
import dev.amorphic.envelope.EnvelopeException;
import dev.amorphic.envelope.EnvelopeJson;
import dev.amorphic.pricing.PricingCalcCore;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The envelope (spec 02), spoken natively: a request envelope in, a reply envelope out. A request that can't be
 * done is a failure reply, still a 200; only an unreadable envelope is a 400.
 */
@RestController
class InvokeController {

    static final Address REQUEST = Address.parse("pkg:schema/pricing-calc-convert-request@1.0");
    static final Address REPLY = Address.parse("pkg:schema/pricing-calc-convert-reply@1.0");
    private static final Address ANY_REQUEST = Address.of(REQUEST.kind(), REQUEST.name()).at("1.*");

    private final PricingCalcCore calc = new PricingCalcCore();

    @PostMapping(path = "/invoke", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Object> invoke(@RequestBody String body) {
        Envelope request;
        try {
            request = EnvelopeJson.read(body);
        } catch (EnvelopeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.ok(EnvelopeJson.write(answer(request)));
    }

    private Envelope answer(Envelope request) {
        Aspect payload = request.payload();
        if (!ANY_REQUEST.matches(payload.schema())) {
            return Envelope.error(request, "this service answers " + ANY_REQUEST + ", not " + payload.schema());
        }
        String amount = payload.body().path("amount").asText();
        String from = payload.body().path("from").asText();
        String to = payload.body().path("to").asText();
        try {
            BigDecimal result = calc.convert(parse(amount), from, to);
            return Envelope.reply(request, new Aspect(REPLY, Envelope.body()
                    .put("amount", result.toPlainString()).put("currency", to)));
        } catch (IllegalArgumentException e) {
            return Envelope.error(request, e.getMessage());
        }
    }

    private static BigDecimal parse(String amount) {
        try {
            return new BigDecimal(amount);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid amount '" + amount + "'");
        }
    }
}
