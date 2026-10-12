package com.pte.scoring.internal.vendor.openai;

import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.internal.config.AiProviderProperties;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.internal.vendor.AiScoreResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleChatClientTest {

    private static final String COMPLETIONS = "http://openai.test/v1/chat/completions";
    private static final String OK_BODY = """
            {"model":"gpt-test","choices":[{"message":{"content":"{\\"rawScore\\":3}"}}]}
            """;
    private static final List<Map<String, Object>> MESSAGES = List.of(Map.of("role", "user", "content", "essay"));

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://openai.test/v1");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final OpenAiCompatibleChatClient client = new OpenAiCompatibleChatClient(builder.build(),
            new AiProviderProperties(), JsonMapper.builder().build(), Clock.systemUTC());

    @Test
    void transientProviderErrorIsRetried() {
        server.expect(once(), requestTo(COMPLETIONS))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "0"));
        server.expect(once(), requestTo(COMPLETIONS)).andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        AiScoreResult result = client.complete("gpt-test", MESSAGES);

        assertThat(result.rawScore()).isEqualTo(3);
        server.verify();
    }

    @Test
    void rejectedKeyFailsOnceWithAReasonAndWithoutTheProviderBody() {
        server.expect(once(), requestTo(COMPLETIONS)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.TEXT_PLAIN).body("provider-detail-must-not-leak"));

        assertThatThrownBy(() -> client.complete("gpt-test", MESSAGES))
                .isInstanceOfSatisfying(AiProviderException.class, ex -> {
                    assertThat(ex.getReason()).isEqualTo(AiFailureReason.PROVIDER_AUTH_FAILED);
                    assertThat(ex.getMessage()).doesNotContain("provider-detail");
                    assertThat(ex.getCause()).isNull();
                });
        server.verify();
    }
}
