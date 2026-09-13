package com.pte.scoring.vendor.openai;

import com.pte.scoring.config.AiProviderProperties;
import com.pte.scoring.vendor.AiProviderException;
import com.pte.scoring.vendor.AiScoreResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleEssayScoringClientTest {

    private static final String BASE_URL = "http://provider.internal/v1";
    private static final String API_KEY = "test-provider-key";

    private AiProviderProperties properties;
    private MockRestServiceServer server;
    private OpenAiCompatibleEssayScoringClient client;

    @BeforeEach
    void setUp() {
        properties = new AiProviderProperties();
        properties.setApiKey(API_KEY);
        properties.setEssayModel("essay-model");
        properties.setMaxTokens(800);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Authorization", "Bearer " + API_KEY);
        server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleChatClient chatClient = new OpenAiCompatibleChatClient(
                builder.build(), properties, JsonMapper.builder().build());
        client = new OpenAiCompatibleEssayScoringClient(chatClient, properties);
    }

    @Test
    void score_sendsAuthenticatedJsonRubricRequestAndParsesResponse() {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + API_KEY))
                .andExpect(content().string(allOf(
                        containsString("\"model\":\"essay-model\""),
                        containsString("\"response_format\":{\"type\":\"json_object\"}"),
                        containsString("candidate answer"))))
                .andRespond(withSuccess(providerResponse(
                        "{\"rawScore\":82,\"subScores\":{\"GRAMMAR\":80},\"feedback\":\"concise feedback\"}"),
                        MediaType.APPLICATION_JSON));

        AiScoreResult result = client.score("candidate answer", "essay prompt");

        assertThat(result.rawScore()).isEqualTo(82);
        assertThat(result.subScores()).containsEntry("GRAMMAR", 80);
        assertThat(result.feedback()).isEqualTo("concise feedback");
        server.verify();
    }

    @Test
    void score_acceptsJsonInsideMarkdownFence() {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(providerResponse("```json\n{\"rawScore\":71}\n```"),
                        MediaType.APPLICATION_JSON));

        assertThat(client.score("candidate", "prompt").rawScore()).isEqualTo(71);
        server.verify();
    }

    @Test
    void score_rejectsUnsafeProviderScore() {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(providerResponse("{\"rawScore\":101}"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.score("candidate", "prompt"))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("rawScore");
        server.verify();
    }

    @Test
    void score_rejectsMalformedProviderJson() {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(providerResponse("not-json"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.score("candidate", "prompt"))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("Could not parse");
        server.verify();
    }

    @Test
    void score_wrapsHttpFailureForQueueRetry() {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.score("candidate", "prompt"))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("AI provider request failed");
        server.verify();
    }

    private String providerResponse(String content) {
        return "{\"choices\":[{\"message\":{\"content\":"
                + JsonMapper.builder().build().writeValueAsString(content) + "}}]}";
    }
}
