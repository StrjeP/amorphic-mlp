package dev.amorphic.demo.compose;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.amorphic.access.Handler;
import dev.amorphic.addressing.Address;
import dev.amorphic.envelope.Aspect;
import dev.amorphic.envelope.Envelope;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The positions capability, in-process: an account's holdings, read from accounts.yaml on every call, so a hand
 * edit shows on the next report.
 */
final class PositionsHandler implements Handler {

    static final Address REPLY = Address.parse("pkg:schema/positions-reply@1.0");
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private final Path accounts;

    PositionsHandler(Path accounts) {
        this.accounts = accounts;
    }

    @Override
    public Envelope handle(Envelope request) {
        String account = request.payload().body().path("account").asText();
        JsonNode found;
        try {
            found = YAML.readTree(Files.readString(accounts)).path("accounts").path(account);
        } catch (IOException e) {
            return Envelope.error(request, "can't read the accounts: " + e.getMessage());
        }
        if (found.isMissingNode()) {
            return Envelope.error(request, "no account '" + account + "'");
        }
        var body = Envelope.body().put("account", account);
        var positions = body.putArray("positions");
        for (JsonNode p : found.path("positions")) {
            positions.addObject().put("name", p.path("name").asText()).put("amount", p.path("amount").asText())
                    .put("currency", p.path("currency").asText());
        }
        return Envelope.reply(request, new Aspect(REPLY, body));
    }
}
