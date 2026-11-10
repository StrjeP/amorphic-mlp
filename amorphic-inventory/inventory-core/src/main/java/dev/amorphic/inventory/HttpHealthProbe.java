package dev.amorphic.inventory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/** Probes {@code GET {url}/health}. Healthy only on HTTP 200 within the timeout. */
public final class HttpHealthProbe implements Sources.Health {

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    @Override
    public HealthCheck check(String url, Duration timeout) {
        String target = url.replaceAll("/+$", "") + "/health";
        long start = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(target)).timeout(timeout).GET().build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            long ms = (System.nanoTime() - start) / 1_000_000;
            return response.statusCode() == 200
                    ? new HealthCheck(HealthCheck.Status.UP, target, ms, "200")
                    : new HealthCheck(HealthCheck.Status.DOWN, target, ms, "HTTP " + response.statusCode());
        } catch (HttpConnectTimeoutException e) {
            return new HealthCheck(HealthCheck.Status.DOWN, target, null, "cannot connect (nothing answered the connection)");
        } catch (HttpTimeoutException e) {
            return new HealthCheck(HealthCheck.Status.DOWN, target, null, "no answer within " + timeout.toMillis() + "ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new HealthCheck(HealthCheck.Status.DOWN, target, null, "interrupted");
        } catch (java.net.ConnectException e) {
            return new HealthCheck(HealthCheck.Status.DOWN, target, null,
                    "unreachable: connection refused or host not found");
        } catch (Exception e) {
            String why = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return new HealthCheck(HealthCheck.Status.DOWN, target, null, "unreachable: " + why);
        }
    }
}
