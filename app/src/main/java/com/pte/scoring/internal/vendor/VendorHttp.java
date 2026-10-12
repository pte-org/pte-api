package com.pte.scoring.internal.vendor;

import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.internal.constant.ScoringConstants;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Transport and failure classification shared by the Azure and Gemini REST
 * clients. Failures carry the vendor and classification code only — never
 * the provider body, which may echo the transcript or essay.
 */
public final class VendorHttp {

    private VendorHttp() {
    }

    /**
     * The request timeout only bounds the wait for the response headers, so
     * the call is also waited on with it: a body that stalls after the
     * headers times out instead of holding the worker indefinitely. Inside an
     * {@link AiJobBudget} the wait is cut to the time the job has left, and a
     * spent budget fails without sending.
     */
    public static HttpResponse<byte[]> send(HttpClient httpClient, HttpRequest request, String vendor) {
        Optional<Duration> wait = shorter(request.timeout(), AiJobBudget.remaining());
        if (wait.filter(Duration::isZero).isPresent()) {
            throw failure(vendor, AiFailureReason.PROVIDER_TIMEOUT);
        }
        CompletableFuture<HttpResponse<byte[]>> call =
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
        try {
            return wait.isPresent()
                    ? call.get(wait.get().toMillis(), TimeUnit.MILLISECONDS)
                    : call.get();
        } catch (TimeoutException ex) {
            call.cancel(true);
            throw failure(vendor, AiFailureReason.PROVIDER_TIMEOUT, ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            AiFailureReason reason = cause instanceof HttpTimeoutException
                    ? AiFailureReason.PROVIDER_TIMEOUT
                    : AiFailureReason.PROVIDER_NETWORK_ERROR;
            throw failure(vendor, reason, cause);
        } catch (InterruptedException ex) {
            call.cancel(true);
            Thread.currentThread().interrupt();
            throw failure(vendor, AiFailureReason.PROVIDER_NETWORK_ERROR, ex);
        }
    }

    private static Optional<Duration> shorter(Optional<Duration> a, Optional<Duration> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return a.or(() -> b);
        }
        return Optional.of(a.get().compareTo(b.get()) <= 0 ? a.get() : b.get());
    }

    public static boolean isSuccess(HttpResponse<?> response) {
        return response.statusCode() / 100 == 2;
    }

    public static AiFailureReason classifyStatus(int status) {
        if (status == 429) {
            return AiFailureReason.PROVIDER_RATE_LIMITED;
        }
        if (status == 408) {
            return AiFailureReason.PROVIDER_TIMEOUT;
        }
        if (status >= 500) {
            return AiFailureReason.PROVIDER_UNAVAILABLE;
        }
        if (status == 401 || status == 403) {
            return AiFailureReason.PROVIDER_AUTH_FAILED;
        }
        return AiFailureReason.PROVIDER_BAD_REQUEST;
    }

    /** A non-2xx answer classified by status alone, with the vendor's {@code Retry-After} hint. */
    public static AiProviderException httpFailure(String vendor, HttpResponse<?> response) {
        int status = response.statusCode();
        return httpFailure(vendor, classifyStatus(status), status, retryAfterHeader(response));
    }

    public static AiProviderException httpFailure(String vendor, AiFailureReason reason, int status,
                                                  Duration retryAfter) {
        return new AiProviderException(reason, message(vendor, reason) + " (HTTP " + status + ")", retryAfter);
    }

    public static JsonNode readJson(JsonMapper jsonMapper, byte[] body, String vendor) {
        try {
            return jsonMapper.readTree(body);
        } catch (JacksonException ex) {
            // The parser message quotes the body, so the cause is dropped on purpose.
            throw failure(vendor, AiFailureReason.PROVIDER_RESPONSE_INVALID);
        }
    }

    public static AiProviderException failure(String vendor, AiFailureReason reason) {
        return new AiProviderException(reason, message(vendor, reason));
    }

    /** Only for transport exceptions, whose messages never hold provider content. */
    private static AiProviderException failure(String vendor, AiFailureReason reason, Throwable cause) {
        return new AiProviderException(reason, message(vendor, reason), cause);
    }

    /** {@code Retry-After} in delta-seconds; the HTTP-date form is rare from these vendors and ignored. */
    public static Duration retryAfterHeader(HttpResponse<?> response) {
        return retryAfter(response.headers().firstValue("Retry-After").orElse(null));
    }

    /** @return the delta-seconds {@code Retry-After} value, or null when absent or in another form */
    public static Duration retryAfter(String headerValue) {
        return Optional.ofNullable(headerValue)
                .map(String::trim)
                .filter(value -> value.matches("\\d{1,6}"))
                .map(value -> Duration.ofSeconds(Long.parseLong(value)))
                .orElse(null);
    }

    private static String message(String vendor, AiFailureReason reason) {
        return ScoringConstants.AI_VENDOR_REQUEST_FAILED.formatted(vendor, reason.name());
    }
}
