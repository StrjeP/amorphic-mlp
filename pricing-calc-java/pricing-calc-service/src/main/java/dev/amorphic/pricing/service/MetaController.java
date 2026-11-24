package dev.amorphic.pricing.service;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /meta}: who this service is (spec 03, section 6), for the caller's handshake. */
@RestController
class MetaController {

    private final Self self;

    MetaController(Self self) {
        this.self = self;
    }

    @GetMapping(path = "/meta", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> meta() {
        return self.instance()
                .map(i -> ResponseEntity.ok(i.json().toString()))
                .orElseGet(() -> ResponseEntity.status(503).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"no identity was declared for this instance (AMORPHIC_INSTANCE is not set)\"}"));
    }
}
