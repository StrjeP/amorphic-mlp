package dev.amorphic.pricing.service;

import dev.amorphic.pricing.PricingCalcCore;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Same /convert and /health contract as the Python service. */
@RestController
class ConvertController {

    private final PricingCalcCore calc = new PricingCalcCore();

    @GetMapping("/convert")
    Map<String, String> convert(@RequestParam String amount, @RequestParam String from, @RequestParam String to) {
        BigDecimal value;
        try {
            value = new BigDecimal(amount);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid amount '" + amount + "'");
        }
        return Map.of("amount", calc.convert(value, from, to).toPlainString(), "currency", to);
    }

    @GetMapping("/health")
    Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
