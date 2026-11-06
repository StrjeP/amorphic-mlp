package dev.amorphic.demo;

import dev.amorphic.demo.evidence.EvidenceRecorder;
import dev.amorphic.demo.pricing.PricingCalc;
import dev.amorphic.demo.pricing.PricingRejectedException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class QuoteController {

    private static final Logger LOG = LoggerFactory.getLogger(QuoteController.class);

    private final PricingCalc pricingCalc;
    private final EvidenceRecorder evidence;

    QuoteController(PricingCalc pricingCalc, EvidenceRecorder evidence) {
        this.pricingCalc = pricingCalc;
        this.evidence = evidence;
    }

    /** The business endpoint. Its one pricing call never changes between stages. */
    @GetMapping("/quote")
    Map<String, Object> quote(@RequestParam String amount, @RequestParam String from, @RequestParam String to,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        String id = requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
        BigDecimal value;
        try {
            value = new BigDecimal(amount);
        } catch (NumberFormatException e) {
            throw new PricingRejectedException("invalid amount '" + amount + "'");
        }

        PricingCalc.Result result = pricingCalc.convert(id, value, from, to);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", result.amount().toPlainString());
        body.put("currency", result.currency());
        body.put("servedBy", result.servedBy());
        body.put("binding", result.binding());
        body.put("requestId", id);
        return body;
    }

    @GetMapping("/evidence")
    Map<String, Object> evidence() {
        return evidence.summary();
    }

    @PostMapping("/evidence/reset")
    Map<String, Object> resetEvidence() {
        evidence.reset();
        return evidence.summary();
    }

    @ExceptionHandler(PricingRejectedException.class)
    ResponseEntity<Map<String, String>> rejected(PricingRejectedException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<Map<String, String>> unavailable(RuntimeException e) {
        LOG.warn("pricing-calc call failed", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "pricing-calc unavailable: " + e.getMessage()));
    }
}
