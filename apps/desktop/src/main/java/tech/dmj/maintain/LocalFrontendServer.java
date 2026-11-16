package tech.dmj.maintain;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.concurrent.Executors;

/** Loopback-only static server and same-origin proxy for migrated React and FastAPI. */
public final class LocalFrontendServer implements AutoCloseable {
    private HttpServer server;
    private String backendTarget;
    private final HttpClient proxy = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build();

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 32);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", this::handle);
        server.start();
    }
    public String origin() {
        if (server == null) throw new IllegalStateException("Frontend server has not started");
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
    public void setBackendTarget(String target) { backendTarget = target; }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getRawPath();
        if (path == null) path = "/";
        if (path.startsWith("/api/")) { proxyApi(exchange, path); return; }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) && !"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1); exchange.close(); return;
        }
        if (path.contains("..") || path.contains(String.valueOf((char)92))) {
            exchange.sendResponseHeaders(400, -1); exchange.close(); return;
        }
        String relative = path.equals("/") ? "index.html" : path.substring(1);
        InputStream input = getClass().getClassLoader().getResourceAsStream("web/" + relative);
        if (input == null && !relative.contains(".")) {
            relative = "index.html";
            input = getClass().getClassLoader().getResourceAsStream("web/index.html");
        }
        if (input == null) { exchange.sendResponseHeaders(404, -1); exchange.close(); return; }
        byte[] body;
        try (input) { body = input.readAllBytes(); }
        exchange.getResponseHeaders().set("Content-Type", contentType(relative));
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Cache-Control", relative.equals("index.html") ? "no-store" : "public, max-age=31536000, immutable");
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
        } else {
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }

    private void proxyApi(HttpExchange exchange, String path) throws IOException {
        if (backendTarget == null) { exchange.sendResponseHeaders(503, -1); exchange.close(); return; }
        String target = backendTarget + path + (exchange.getRequestURI().getRawQuery() == null ? "" : "?" + exchange.getRequestURI().getRawQuery());
        try {
            String method = exchange.getRequestMethod();
            HttpRequest.BodyPublisher body = ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method))
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofByteArray(exchange.getRequestBody().readAllBytes());
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(target))
                    .timeout(java.time.Duration.ofSeconds(90)).method(method, body);
            for (String header : new String[] {"Accept", "Authorization", "Content-Type", "X-Device-Key", "X-Maintain-Application", "Range", "If-None-Match"}) {
                String value = exchange.getRequestHeaders().getFirst(header);
                if (value != null) request.header(header, value);
            }
            HttpResponse<byte[]> response = proxy.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            for (String header : new String[] {"Content-Type", "Content-Disposition", "Cache-Control", "ETag", "Accept-Ranges", "Content-Range"}) {
                response.headers().firstValue(header).ifPresent(value -> exchange.getResponseHeaders().set(header, value));
            }
            byte[] responseBody = response.body();
            if (response.statusCode() == 204 || response.statusCode() == 304 || responseBody.length == 0) {
                exchange.sendResponseHeaders(response.statusCode(), -1);
            } else {
                exchange.sendResponseHeaders(response.statusCode(), responseBody.length);
                exchange.getResponseBody().write(responseBody);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            exchange.sendResponseHeaders(503, -1);
        } catch (Exception error) {
            exchange.sendResponseHeaders(502, -1);
        } finally {
            exchange.close();
        }
    }

    private static String contentType(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html")) return "text/html; charset=utf-8";
        if (lower.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".woff")) return "font/woff";
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        if (lower.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }
    @Override public void close() {
        if (server != null) { server.stop(0); server = null; }
    }
}
