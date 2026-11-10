package dev.amorphic.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpHealthProbeTest {

    HttpServer server;
    String url;
    final HttpHealthProbe probe = new HttpHealthProbe();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/up/health", ex -> { ex.sendResponseHeaders(200, -1); ex.close(); });
        server.createContext("/broken/health", ex -> { ex.sendResponseHeaders(500, -1); ex.close(); });
        server.createContext("/slow/health", ex -> {
            try { Thread.sleep(1000); } catch (InterruptedException ignored) { }
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void upWhenHealthAnswers200() {
        HealthCheck h = probe.check(url + "/up", Duration.ofSeconds(1));
        assertThat(h.status()).isEqualTo(HealthCheck.Status.UP);
        assertThat(h.target()).isEqualTo(url + "/up/health");
        assertThat(h.latencyMs()).isNotNull();
    }

    @Test
    void downOnAnErrorStatus() {
        HealthCheck h = probe.check(url + "/broken", Duration.ofSeconds(1));
        assertThat(h.status()).isEqualTo(HealthCheck.Status.DOWN);
        assertThat(h.detail()).isEqualTo("HTTP 500");
    }

    @Test
    void downWhenTooSlow() {
        HealthCheck h = probe.check(url + "/slow", Duration.ofMillis(200));
        assertThat(h.status()).isEqualTo(HealthCheck.Status.DOWN);
        assertThat(h.detail()).contains("no answer within 200ms");
    }

    @Test
    void downWhenNothingIsListening() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        HealthCheck h = probe.check("http://127.0.0.1:" + port, Duration.ofSeconds(1));
        assertThat(h.status()).isEqualTo(HealthCheck.Status.DOWN);
        assertThat(h.detail()).startsWith("unreachable");
    }
}
