package dev.amorphic.access;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.amorphic.envelope.EnvelopeException;
import dev.amorphic.envelope.InstanceRef;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Asks a service who it is (spec 03, section 6): {@code GET {url}/meta} answers with its instance, the identity it
 * was declared with and the address it realises. The handshake compares that with what the routing expects.
 */
public final class Meta {

    private static final ObjectMapper JSON = new ObjectMapper();

    private Meta() {}

    public static InstanceRef read(String url) {
        URI uri = URI.create(Http.trimSlash(url) + "/meta");
        HttpResponse<String> response = Http.send(HttpRequest.newBuilder().GET(), uri);
        if (response.statusCode() != 200) {
            throw new AccessException(uri + " answered HTTP " + response.statusCode() + ", so it can't say who it is");
        }
        try {
            return InstanceRef.read(JSON.readTree(response.body()));
        } catch (IOException | EnvelopeException e) {
            throw new AccessException(uri + " didn't answer with an instance: " + e.getMessage(), e);
        }
    }
}
