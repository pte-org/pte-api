package com.pte.chat.support;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Loopback stand-in for the Gemini API used by the chat client tests: records every request (method, path,
 * headers, body), counts them, and answers with the next scripted response. Binds 127.0.0.1 on an ephemeral
 * port, so no test ever reaches the real provider or needs a real key.
 */
public final class FakeGeminiServer implements AutoCloseable {

    public record RecordedRequest(String method, URI uri, Map<String, List<String>> headers, String body) {

        public String header(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .map(entry -> entry.getValue().getFirst())
                    .findFirst()
                    .orElse(null);
        }
    }

    /** {@code delayMs} holds back the headers; {@code bodyStallMs} holds back the body after them. */
    private record Scripted(int status, String body, long delayMs, long bodyStallMs) {
    }

    private final HttpServer server;
    // Handlers run off the dispatcher so a slow response never blocks close().
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger requestCount = new AtomicInteger();
    private final Queue<Scripted> responses = new ConcurrentLinkedQueue<>();

    private FakeGeminiServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new RecordedRequest(exchange.getRequestMethod(), exchange.getRequestURI(),
                    Map.copyOf(exchange.getRequestHeaders()), body));
            requestCount.incrementAndGet();
            Scripted scripted = responses.poll();
            if (scripted == null) {
                scripted = new Scripted(500, "", 0, 0);
            }
            pause(scripted.delayMs());
            byte[] out = scripted.body().getBytes(StandardCharsets.UTF_8);
            try {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(scripted.status(), out.length == 0 ? -1 : out.length);
                if (scripted.bodyStallMs() > 0) {
                    exchange.getResponseBody().flush();
                    pause(scripted.bodyStallMs());
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

    public static FakeGeminiServer start() throws IOException {
        return new FakeGeminiServer();
    }

    /** Base URI of the fake, without a trailing slash or any path. */
    public URI baseUri() {
        return URI.create("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort());
    }

    public void enqueue(int status, String body) {
        responses.add(new Scripted(status, body, 0, 0));
    }

    /** Holds the whole response back for {@code delayMs}. */
    public void enqueueDelayed(long delayMs, int status, String body) {
        responses.add(new Scripted(status, body, delayMs, 0));
    }

    /** Sends the headers at once, then waits {@code stallMs} before writing the body. */
    public void enqueueStalledBody(long stallMs, int status, String body) {
        responses.add(new Scripted(status, body, 0, stallMs));
    }

    public int requestCount() {
        return requestCount.get();
    }

    public List<RecordedRequest> requests() {
        return List.copyOf(requests);
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
