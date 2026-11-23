package dev.amorphic.access;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/** A local HTTP server that stands in for a service in tests. */
final class Stub implements AutoCloseable {

    record Answer(int status, String body) {}

    record Seen(String method, String path, String query, String body, String upgrade) {}

    final HttpServer server;
    final String url;
    volatile Seen last;

    Stub(Function<Seen, Answer> handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            Seen seen = new Seen(ex.getRequestMethod(), ex.getRequestURI().getPath(), ex.getRequestURI().getRawQuery(),
                    new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                    ex.getRequestHeaders().getFirst("Upgrade"));
            last = seen;
            Answer a = handler.apply(seen);
            byte[] b = a.body().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(a.status(), b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
