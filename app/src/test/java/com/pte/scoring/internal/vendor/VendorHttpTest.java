package com.pte.scoring.internal.vendor;

import com.pte.scoring.domain.enums.AiFailureReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class VendorHttpTest {

    @ParameterizedTest
    @CsvSource({
            "429, PROVIDER_RATE_LIMITED",
            "408, PROVIDER_TIMEOUT",
            "500, PROVIDER_UNAVAILABLE",
            "503, PROVIDER_UNAVAILABLE",
            "400, PROVIDER_BAD_REQUEST",
            "404, PROVIDER_BAD_REQUEST",
            "415, PROVIDER_BAD_REQUEST",
            "401, PROVIDER_AUTH_FAILED",
            "403, PROVIDER_AUTH_FAILED"
    })
    void classifiesHttpStatus(int status, AiFailureReason reason) {
        assertThat(VendorHttp.classifyStatus(status)).isEqualTo(reason);
    }

    @Test
    void aBodyThatStallsAfterTheHeadersStillTimesOut() throws Exception {
        // The server closes first, so the client's close() never waits out the stall.
        try (HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
             RecordingHttpServer server = RecordingHttpServer.start()) {
            server.enqueueStalledBody(3_000, 200, "{\"ok\":true}");
            HttpRequest request = HttpRequest.newBuilder(server.uri("/stall"))
                    .timeout(Duration.ofMillis(300)).GET().build();

            assertThatThrownBy(() -> VendorHttp.send(httpClient, request, "Vendor"))
                    .isInstanceOfSatisfying(AiProviderException.class,
                            ex -> assertThat(ex.getReason()).isEqualTo(AiFailureReason.PROVIDER_TIMEOUT));
        }
    }

    @Test
    void theWaitIsCutToWhatTheJobBudgetHasLeft() throws Exception {
        try (HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
             RecordingHttpServer server = RecordingHttpServer.start()) {
            server.enqueueStalledBody(3_000, 200, "{\"ok\":true}");
            HttpRequest request = HttpRequest.newBuilder(server.uri("/stall"))
                    .timeout(Duration.ofSeconds(30)).GET().build();

            assertThatThrownBy(() -> AiJobBudget.run(com.pte.TestClocks.fixed(), Duration.ofMillis(300),
                    () -> VendorHttp.send(httpClient, request, "Vendor")))
                    .isInstanceOfSatisfying(AiProviderException.class,
                            ex -> assertThat(ex.getReason()).isEqualTo(AiFailureReason.PROVIDER_TIMEOUT));
        }
    }

    @Test
    void aSpentJobBudgetFailsWithoutSending() {
        HttpClient httpClient = mock(HttpClient.class);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost/never"))
                .timeout(Duration.ofSeconds(30)).GET().build();

        assertThatThrownBy(() -> AiJobBudget.run(com.pte.TestClocks.fixed(), Duration.ZERO,
                () -> VendorHttp.send(httpClient, request, "Vendor")))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(AiFailureReason.PROVIDER_TIMEOUT));
        verifyNoInteractions(httpClient);
    }

    @Test
    void unreadableJsonIsAnInvalidResponseThatHidesTheBody() {
        byte[] body = "not json provider-detail-must-not-leak".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> VendorHttp.readJson(JsonMapper.builder().build(), body, "Vendor"))
                .isInstanceOfSatisfying(AiProviderException.class, ex -> {
                    assertThat(ex.getReason()).isEqualTo(AiFailureReason.PROVIDER_RESPONSE_INVALID);
                    assertThat(ex.getMessage()).contains("Vendor").doesNotContain("provider-detail");
                    assertThat(ex.getCause()).isNull();
                });
    }
}
