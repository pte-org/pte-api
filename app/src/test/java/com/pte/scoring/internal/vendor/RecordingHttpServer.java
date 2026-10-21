package com.pte.scoring.internal.vendor;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loopback HTTP server for vendor-client tests: records every request and
 * answers with the next scripted response, so tests never reach a real
 * provider (or spend its credit).
 */
public final class RecordingHttpServer implements AutoCloseable {

    public record RecordedRequest(String method, URI uri, Map<String, List<String>> headers, byte[] body) {

        public String header(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .map(entry -> entry.getValue().getFirst())
                    .findFirst()
                    .orElse(null);
        }

        public String bodyText() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    /** {@code delayMs} holds back the headers; {@code bodyStallMs} holds back the body after them. */
    private record ScriptedResponse(int status, Map<String, String> headers, String body, long delayMs,
                                    long bodyStallMs) {
    }

    private final HttpServer server;
    // Handlers run off the dispatcher thread so a delayed response never blocks close().
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private final Queue<ScriptedResponse> responses = new ConcurrentLinkedQueue<>();

    private RecordingHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            requests.add(new RecordedRequest(exchange.getRequestMethod(), exchange.getRequestURI(),
                    Map.copyOf(exchange.getRequestHeaders()), body));
            ScriptedResponse response = responses.poll();
            if (response == null) {
                response = new ScriptedResponse(500, Map.of(), "", 0, 0);
            }
            pause(response.delayMs());
            response.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
            byte[] out = response.body().getBytes(StandardCharsets.UTF_8);
            try {
                exchange.sendResponseHeaders(response.status(), out.length == 0 ? -1 : out.length);
                if (response.bodyStallMs() > 0) {
                    exchange.getResponseBody().flush();
                    pause(response.bodyStallMs());
                }
                if (out.length > 0) {
                    exchange.getResponseBody().write(out);
                }
            } catch (IOException ignored) {
                // The client may already have timed out and closed the connection.
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(executor);
        server.start();
    }

    public static RecordingHttpServer start() throws IOException {
        return new RecordingHttpServer();
    }

    public URI uri(String pathAndQuery) {
        return URI.create("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort()
                + pathAndQuery);
    }

    public void enqueue(int status, String body) {
        enqueue(status, Map.of(), body);
    }

    public void enqueue(int status, Map<String, String> headers, String body) {
        responses.add(new ScriptedResponse(status, headers, body, 0, 0));
    }

    public void enqueueDelayed(long delayMs, int status, String body) {
        responses.add(new ScriptedResponse(status, Map.of(), body, delayMs, 0));
    }

    /** Sends the headers at once, then waits {@code stallMs} before the body. */
    public void enqueueStalledBody(long stallMs, int status, String body) {
        responses.add(new ScriptedResponse(status, Map.of(), body, 0, stallMs));
    }

    public List<RecordedRequest> requests() {
        return new ArrayList<>(requests);
    }

    private static void pause(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
