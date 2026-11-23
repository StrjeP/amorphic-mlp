package dev.amorphic.envelope;

import dev.amorphic.addressing.Address;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * The framework's own schemas, shipped in this jar: the two envelope purposes, causation and error. Each is a JSON
 * Schema file named after its address. A catalogue lists these alongside its own.
 */
public final class Schemas {

    /** Each built-in schema's address and the classpath resource holding it. */
    public static final Map<Address, String> BUILT_IN = Map.of(
            Envelope.REQUEST, "schemas/request-1.0.json",
            Envelope.REPLY, "schemas/reply-1.0.json",
            Envelope.CAUSATION_SCHEMA, "schemas/causation-1.0.json",
            Envelope.ERROR_SCHEMA, "schemas/error-1.0.json");

    private Schemas() {}

    /** The text of a built-in schema. */
    public static String read(Address address) {
        String resource = BUILT_IN.get(address);
        if (resource == null) {
            throw new EnvelopeException(address + " is not one of the framework's own schemas");
        }
        try (InputStream in = Schemas.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new EnvelopeException("schema " + address + " is missing from the jar (" + resource + ")");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
