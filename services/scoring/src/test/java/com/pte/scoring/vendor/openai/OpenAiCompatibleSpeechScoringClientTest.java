package com.pte.scoring.vendor.openai;

import com.pte.scoring.client.MediaClient;
import com.pte.scoring.client.dto.MediaPresignedDownloadResponse;
import com.pte.scoring.config.AiProviderProperties;
import com.pte.scoring.vendor.AiProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleSpeechScoringClientTest {

    private static final String PROVIDER_URL = "http://provider.internal/v1";
    private static final String MEDIA_URL = "https://signed.media/audio.wav";
    private static final String API_KEY = "test-provider-key";
    private static final UUID MEDIA_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private AiProviderProperties properties;
    private MediaClient mediaClient;
    private MockRestServiceServer providerServer;
    private MockRestServiceServer mediaServer;
    private OpenAiCompatibleSpeechScoringClient client;

    @BeforeEach
    void setUp() {
        properties = new AiProviderProperties();
        properties.setApiKey(API_KEY);
        properties.setSpeechModel("speech-model");
        properties.setMediaUrlTtlSeconds(120);
        properties.setAudioFormat("wav");
        mediaClient = mock(MediaClient.class);

        RestClient.Builder providerBuilder = RestClient.builder()
                .baseUrl(PROVIDER_URL)
                .defaultHeader("Authorization", "Bearer " + API_KEY);
        providerServer = MockRestServiceServer.bindTo(providerBuilder).build();
        OpenAiCompatibleChatClient chatClient = new OpenAiCompatibleChatClient(
                providerBuilder.build(), properties, JsonMapper.builder().build());

        RestClient.Builder mediaBuilder = RestClient.builder();
        mediaServer = MockRestServiceServer.bindTo(mediaBuilder).build();
        client = new OpenAiCompatibleSpeechScoringClient(mediaClient, mediaBuilder.build(), chatClient, properties);
    }

    @Test
    void score_resolvesTenantMediaDownloadsAudioAndSendsBase64() {
        when(mediaClient.presignGet(MEDIA_ID, 120, TENANT_ID))
                .thenReturn(new MediaPresignedDownloadResponse(MEDIA_URL, 120, 12));
        mediaServer.expect(requestTo(MEDIA_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(new byte[]{1, 2, 3}, MediaType.APPLICATION_OCTET_STREAM));
        providerServer.expect(requestTo(PROVIDER_URL + "/chat/completions"))
                .andExpect(header("Authorization", "Bearer " + API_KEY))
                .andExpect(content().string(containsString("AQID")))
                .andExpect(content().string(containsString("input_audio")))
                .andRespond(withSuccess(providerResponse("{\"rawScore\":74}"), MediaType.APPLICATION_JSON));

        assertThat(client.score(MEDIA_ID.toString(), "reference", TENANT_ID).rawScore()).isEqualTo(74);

        verify(mediaClient).presignGet(MEDIA_ID, 120, TENANT_ID);
        mediaServer.verify();
        providerServer.verify();
    }

    @Test
    void score_rejectsMissingTenantBeforeResolvingMedia() {
        assertThatThrownBy(() -> client.score(MEDIA_ID.toString(), "reference", null))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("tenant ID");
    }

    private String providerResponse(String content) {
        return "{\"choices\":[{\"message\":{\"content\":"
                + JsonMapper.builder().build().writeValueAsString(content) + "}}]}";
    }
}
