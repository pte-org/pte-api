package com.pte.scoring.client;

import com.pte.common.security.InternalServiceAuth;
import com.pte.scoring.client.dto.MediaPresignedDownloadResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Mirrors exam-delivery's (untested) MediaClient at the HTTP level via
 * MockRestServiceServer — no existing precedent in this codebase for testing
 * a resilience4j {@code @CircuitBreaker} annotation itself (it requires a
 * Spring AOP proxy, not present in a plain unit test), so the failure-mode
 * case here verifies the underlying HTTP failure propagates correctly rather
 * than being silently swallowed — the fallback method is exercised only via
 * the full Spring context in production.
 */
class MediaClientTest {

    private static final String SERVICE_KEY = "test-internal-service-key";
    private static final UUID MEDIA_PUBLIC_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private RestClient.Builder builderFor(MockRestServiceServer[] serverHolder) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://media.internal/api/media")
                .defaultHeader(InternalServiceAuth.HEADER, SERVICE_KEY);
        serverHolder[0] = MockRestServiceServer.bindTo(builder).build();
        return builder;
    }

    @Test
    void presignGet_success_returnsUrlAndDuration() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        RestClient restClient = builderFor(holder).build();
        MockRestServiceServer server = holder[0];
        MediaClient client = new MediaClient(restClient);

        server.expect(requestTo(
                        "http://media.internal/api/media/internal/media-objects/" + MEDIA_PUBLIC_ID
                                + "/presigned-url?ttlSeconds=120&tenantId=" + TENANT_ID))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header(InternalServiceAuth.HEADER, SERVICE_KEY))
                .andRespond(withSuccess(
                        "{\"success\":true,\"data\":{\"url\":\"http://minio/signed\",\"expiresInSeconds\":120,\"durationSeconds\":33},\"message\":null}",
                        MediaType.APPLICATION_JSON));

        MediaPresignedDownloadResponse result = client.presignGet(MEDIA_PUBLIC_ID, 120L, TENANT_ID);

        assertThat(result).isNotNull();
        assertThat(result.url()).isEqualTo("http://minio/signed");
        assertThat(result.expiresInSeconds()).isEqualTo(120L);
        assertThat(result.durationSeconds()).isEqualTo(33);
        server.verify();
    }

    @Test
    void presignGet_upstreamFailure_propagatesRatherThanSilentlyReturningNull() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        RestClient restClient = builderFor(holder).build();
        MockRestServiceServer server = holder[0];
        MediaClient client = new MediaClient(restClient);

        server.expect(requestTo(
                        "http://media.internal/api/media/internal/media-objects/" + MEDIA_PUBLIC_ID
                                + "/presigned-url?ttlSeconds=60&tenantId=" + TENANT_ID))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.presignGet(MEDIA_PUBLIC_ID, 60L, TENANT_ID))
                .isInstanceOf(RestClientException.class);
        server.verify();
    }
}
