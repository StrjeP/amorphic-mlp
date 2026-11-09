package dev.amorphic.demo;

import dev.amorphic.demo.pricing.AddressTable;
import dev.amorphic.demo.pricing.AddressTableException;
import dev.amorphic.demo.pricing.ReloadablePricingCalc;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Reads and replaces the live address table. Out of scope for the MLP: authentication. */
@RestController
class AdminController {

    private final ReloadablePricingCalc pricingCalc;

    AdminController(ReloadablePricingCalc pricingCalc) {
        this.pricingCalc = pricingCalc;
    }

    @GetMapping("/admin/address-table")
    ReloadablePricingCalc.Applied current() {
        return pricingCalc.current();
    }

    /**
     * Applies a new address table (YAML body) without a restart. A table that can't be
     * honoured, such as a local binding in a build without pricing-calc-core, is rejected
     * with 400 and the current routing stays live.
     */
    @PostMapping("/admin/address-table")
    ReloadablePricingCalc.Applied apply(@RequestBody String yaml,
            @RequestHeader(value = "X-Table-Source", required = false) String source) {
        String name = source == null || source.isBlank() ? "POST /admin/address-table" : source;
        return pricingCalc.apply(AddressTable.parse(yaml, name), name);
    }

    @ExceptionHandler(AddressTableException.class)
    ResponseEntity<Map<String, Object>> rejected(AddressTableException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage(),
                "kept", pricingCalc.current().routing(),
                "generation", pricingCalc.current().generation()));
    }
}
