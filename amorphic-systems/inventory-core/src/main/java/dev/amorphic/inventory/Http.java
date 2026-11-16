package dev.amorphic.inventory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/** Small HTTP helpers shared by the tooling's remote clients. */
public final class Http {

    /** Time kept back from a deadline for the reply to travel; the callee gets the rest. */
    static final Duration MARGIN = Duration.ofMillis(500);

    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    private Http() {}

    public record Response(int status, String body) {}

    public static Response get(String url, Duration timeout) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build());
    }

    public static Response post(String url, String body, String contentType, Duration timeout, String... headers)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        for (int i = 0; i + 1 < headers.length; i += 2) {
            b.header(headers[i], headers[i + 1]);
        }
        return send(b.build());
    }

    private static Response send(HttpRequest request) throws Exception {
        HttpResponse<String> r = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        return new Response(r.statusCode(), r.body());
    }

    /** The deadline to hand on to the callee: the caller's, less a margin for the reply. */
    public static long budgetMillis(Duration timeout) {
        return Math.max(100, timeout.minus(MARGIN).toMillis());
    }

    public static String base(String url) {
        return url.replaceAll("/+$", "");
    }

    /** A plain-words reason for a failed call. */
    public static String why(Exception e, Duration timeout) {
        if (e instanceof HttpConnectTimeoutException) {
            return "cannot connect (nothing answered the connection)";
        }
        if (e instanceof HttpTimeoutException) {
            return "no answer within " + timeout.toMillis() + "ms";
        }
        if (e instanceof java.net.ConnectException) {
            return "unreachable: connection refused or host not found";
        }
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return "interrupted";
        }
        return "unreachable: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }
}
