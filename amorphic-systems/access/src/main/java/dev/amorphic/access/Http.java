package dev.amorphic.access;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** The HTTP client the remote and adapter bindings share. */
final class Http {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    /**
     * HTTP/1.1 only. The JDK client defaults to HTTP/2, which over plain http sends an {@code Upgrade: h2c} header on
     * every request; uvicorn (the Python service) logs a warning for each one. Tested in {@code BindingsTest}.
     */
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
            .version(HttpClient.Version.HTTP_1_1).build();

    private Http() {}

    static HttpResponse<String> send(HttpRequest.Builder request, URI uri) {
        try {
            return CLIENT.send(request.uri(uri).timeout(READ_TIMEOUT).build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new AccessException("cannot reach " + uri + ": " + describe(e), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AccessException("interrupted while calling " + uri, e);
        }
    }

    static String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            throw new AccessException("a binding needs a url");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String describe(IOException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
