package dev.amorphic.inventory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/** A local HTTP server that stands in for a remote service in tests. */
final class Stub implements AutoCloseable {

    record Reply(int status, String body) {}

    final HttpServer server;
    final String url;
    volatile String lastQuery;
    volatile String lastBody;

    Stub(String path, Function<String, Reply> handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, ex -> {
            lastQuery = ex.getRequestURI().getQuery();
            lastBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Reply r = handler.apply(ex.getRequestURI().getPath());
            send(ex, r);
        });
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void send(HttpExchange ex, Reply r) throws IOException {
        byte[] b = r.body().getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(r.status(), b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
