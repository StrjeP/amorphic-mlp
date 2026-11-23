package dev.amorphic.access;

import dev.amorphic.addressing.Address;
import dev.amorphic.envelope.Envelope;
import dev.amorphic.envelope.EnvelopeException;
import dev.amorphic.envelope.EnvelopeJson;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * The remote binding: sends the envelope to a service that speaks it. Pass 1: HTTP {@code POST {url}/invoke}, with
 * the envelope as JSON both ways. A failure reply is still a 200; any other status means the service couldn't read
 * the request.
 */
public final class RemoteBinding implements Invoker {

    public static final String PATH = "/invoke";

    private final URI uri;

    public RemoteBinding(String url) {
        this.uri = URI.create(Http.trimSlash(url) + PATH);
    }

    @Override
    public Reply invoke(Address address, Envelope request) {
        long start = System.nanoTime();
        HttpResponse<String> response = Http.send(HttpRequest.newBuilder()
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(EnvelopeJson.write(request))), uri);
        if (response.statusCode() != 200) {
            throw new AccessException(uri + " answered " + response.statusCode() + " for " + address + ": "
                    + response.body());
        }
        try {
            return new Reply(EnvelopeJson.read(response.body()), Timing.of(start));
        } catch (EnvelopeException e) {
            throw new AccessException(uri + " did not answer with an envelope: " + e.getMessage(), e);
        }
    }

    @Override
    public String toString() {
        return "remote " + uri;
    }
}
