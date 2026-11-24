package dev.amorphic.demo;

import com.fasterxml.jackson.databind.JsonNode;
import dev.amorphic.composer.CompositionException;
import dev.amorphic.composer.Run;
import dev.amorphic.demo.compose.Outcomes;
import dev.amorphic.demo.pricing.PricingRejectedException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The account report: what an account is worth in one currency. It asks for the account-value outcome; the plan
 * (load the positions, convert each, add them up) is the composer's.
 */
@RestController
class AccountController {

    private static final Logger LOG = LoggerFactory.getLogger(AccountController.class);

    private final Outcomes outcomes;

    AccountController(Outcomes outcomes) {
        this.outcomes = outcomes;
    }

    @GetMapping("/accounts/{account}/value")
    Map<String, Object> value(@PathVariable String account, @RequestParam(defaultValue = "USD") String currency,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        String id = requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
        Run run = outcomes.accountValue(id, account, currency);
        if (run.failed()) {
            throw new PricingRejectedException(run.failure().reason());
        }

        Run.StepRun convert = run.step("convert");
        List<Map<String, Object>> positions = new ArrayList<>();
        for (int i = 0; i < convert.items().size(); i++) {
            JsonNode p = convert.items().get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", p.path("name").asText());
            m.put("amount", p.path("amount").asText());
            m.put("currency", p.path("currency").asText());
            m.put("value", convert.output().get(i).path("amount").asText());
            m.put("convertedBy", convert.callFor(i).isPresent() ? convert.route().served().resolved().toString() : null);
            positions.add(m);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("account", account);
        body.put("currency", run.result().path("currency").asText());
        body.put("total", run.result().path("amount").asText());
        body.put("positions", positions);
        body.put("outcome", run.recipe().address().toString());
        body.put("requestId", id);
        return body;
    }

    @ExceptionHandler(PricingRejectedException.class)
    ResponseEntity<Map<String, String>> rejected(PricingRejectedException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(CompositionException.class)
    ResponseEntity<Map<String, String>> unavailable(CompositionException e) {
        LOG.warn("account value failed", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "account value unavailable: " + e.getMessage()));
    }
}
