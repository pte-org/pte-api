package com.pte.scoring.internal.vendor.gemini;

import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.internal.vendor.ProviderGuard;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.http.HttpClient;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OpenAiCompatibleStructuredClientTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final OpenAiCompatibleStructuredClient client = new OpenAiCompatibleStructuredClient(
            mock(HttpClient.class), json, "https://gateway.example/v1/", "key", "Capstone", Duration.ofSeconds(5),
            ProviderGuard.passThrough(OpenAiCompatibleStructuredClient.VENDOR));

    @Test
    void aRecordingIsSentAsInputAudioBeforeTheText() {
        GeminiRequest request = new GeminiRequest("system", "Score it.", java.util.Map.of("type", "OBJECT"),
                java.util.List.of(), new GeminiAudio(GeminiAudio.WAV, new byte[]{1, 2, 3}), null);

        JsonNode user = json.valueToTree(client.body(request)).path("messages").path(1).path("content");

        assertThat(user.path(0).path("type").asText()).isEqualTo("input_audio");
        assertThat(user.path(0).path("input_audio").path("format").asText()).isEqualTo("wav");
        assertThat(user.path(0).path("input_audio").path("data").asText()).isEqualTo("AQID");
        assertThat(user.path(1).path("text").asText()).isEqualTo("Score it.");
    }

    @Test
    void aTextOnlyCallKeepsThePlainStringContent() {
        GeminiRequest request = new GeminiRequest("system", "Score it.", java.util.Map.of("type", "OBJECT"),
                java.util.List.of());

        assertThat(json.valueToTree(client.body(request)).path("messages").path(1).path("content").asText())
                .isEqualTo("Score it.");
    }

    @Test
    void asksForOneJsonBodyInsteadOfAStream() {
        GeminiRequest request = new GeminiRequest("system", "Score it.", java.util.Map.of("type", "OBJECT"),
                java.util.List.of());

        assertThat(json.valueToTree(client.body(request)).path("stream").asBoolean(true)).isFalse();
    }

    @Test
    void readsTheJsonObjectEvenInsideAFenceAndKeepsTheServedModel() {
        GeminiResponse response = client.parse(reply("```json\n{\"content\": 2}\n```", "stop", "capstone-2026"));

        assertThat(response.content().path("content").asInt()).isEqualTo(2);
        assertThat(response.modelVersion()).isEqualTo("capstone-2026");
    }

    @Test
    void fallsBackToTheConfiguredModelName() {
        GeminiResponse response = client.parse(reply("{\"form\": 1}", null, null));

        assertThat(response.modelVersion()).isEqualTo("Capstone");
    }

    @Test
    void aFilteredAnswerIsBlockedAndANonObjectIsInvalid() {
        assertThatThrownBy(() -> client.parse(reply("", "content_filter", null)))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(AiFailureReason.PROVIDER_CONTENT_BLOCKED));
        assertThatThrownBy(() -> client.parse(reply("[1, 2]", null, null)))
                .isInstanceOf(AiProviderException.class);
        assertThatThrownBy(() -> client.parse(json.createObjectNode().set("choices", json.createArrayNode())))
                .isInstanceOf(AiProviderException.class);
    }

    private JsonNode reply(String content, String finishReason, String model) {
        ObjectNode root = json.createObjectNode();
        if (model != null) {
            root.put("model", model);
        }
        ObjectNode choice = root.putArray("choices").addObject();
        if (finishReason != null) {
            choice.put("finish_reason", finishReason);
        }
        choice.putObject("message").put("content", content);
        return root;
    }
}
