package dev.amorphic.governor;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/** A local HTTP server that stands in for a remote service in tests. */
final class Stub implements AutoCloseable {

    record Reply(int status, String body) {}

    record Request(String method, String path, String query, String body, String source) {}

    final HttpServer server;
    final String url;
    volatile Request last;

    Stub(Function<Request, Reply> handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            last = new Request(ex.getRequestMethod(), ex.getRequestURI().getPath(), ex.getRequestURI().getQuery(),
                    new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                    ex.getRequestHeaders().getFirst("X-Table-Source"));
            Reply r = handler.apply(last);
            byte[] b = r.body().getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(r.status(), b.length);
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
