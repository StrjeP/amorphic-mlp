package dev.amorphic.demo;

import dev.amorphic.addressing.Address;
import dev.amorphic.addressing.AddressException;
import dev.amorphic.topology.CallRecord;
import dev.amorphic.topology.Topology;
import dev.amorphic.topology.TopologyLog;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * What calls ran on (spec 03). {@code /topology?address=} with a topology's exact address returns it; with a broader
 * one, such as {@code pkg:topology/pricing-calc}, every topology within it. {@code /topology/calls?address=} returns
 * the calls that ran on one topology.
 */
@RestController
class TopologyController {

    private final TopologyLog log;

    TopologyController(TopologyLog log) {
        this.log = log;
    }

    @GetMapping("/topology")
    ResponseEntity<Object> topology(@RequestParam String address) {
        Address a = parse(address);
        if (a.qualifier("hash").isPresent()) {
            return log.topology(a).<ResponseEntity<Object>>map(t -> ResponseEntity.ok(view(t)))
                    .orElseGet(() -> notFound("no topology at " + a));
        }
        return ResponseEntity.ok(log.topologies(a).stream().map(TopologyController::view).toList());
    }

    @GetMapping("/topology/calls")
    ResponseEntity<Object> calls(@RequestParam String address) {
        Address a = parse(address);
        if (log.topology(a).isEmpty()) {
            return notFound("no topology at " + a);
        }
        List<Map<String, Object>> out = log.calls(a).stream().map(TopologyController::view).toList();
        return ResponseEntity.ok(out);
    }

    private static Address parse(String address) {
        try {
            Address a = Address.parse(address);
            if (!Topology.KIND.equals(a.kind())) {
                throw new IllegalArgumentException(address + " is not a topology address (pkg:topology/...)");
            }
            return a;
        } catch (AddressException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    private static Object view(Topology t) {
        var json = t.json();
        json.put("address", t.address().toString());
        return json;
    }

    private static Map<String, Object> view(CallRecord c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("envelope", c.envelope());
        m.put("causedBy", c.causedBy());
        m.put("generation", c.generation());
        m.put("at", c.at().toString());
        return m;
    }

    private static ResponseEntity<Object> notFound(String message) {
        return ResponseEntity.status(404).body(Map.of("error", message));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> bad(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
