package com.pte.itembank.internal.generation.media;

import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.llm.LlmFailureType;
import com.pte.itembank.internal.generation.llm.LlmProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KokoroSpeechClientTest {

    private static final String BASE = "https://tts.example.test/kokoro";
    private static final String KEY = "test-key-not-real";

    private QuestionGenerationProperties properties;
    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private KokoroSpeechClient client;

    @BeforeEach
    void setUp() {
        properties = new QuestionGenerationProperties();
        properties.getKokoro().setBaseUrl(BASE);
        properties.getKokoro().setApiKey(KEY);
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KokoroSpeechClient(properties, builder.build());
    }

    @Test
    void postsTheSpeechRequestWithBearerKeyAndWavFormat() {
        byte[] wav = {'R', 'I', 'F', 'F', 1, 2, 3};
        server.expect(requestTo(BASE + "/v1/audio/speech"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + KEY))
                .andExpect(jsonPath("$.model").value("kokoro"))
                .andExpect(jsonPath("$.input").value("Hello there."))
                .andExpect(jsonPath("$.voice").value("af_heart"))
                .andExpect(jsonPath("$.speed").value(0.95))
                .andExpect(jsonPath("$.response_format").value("wav"))
                .andRespond(withSuccess(wav, MediaType.parseMediaType("audio/wav")));

        assertThat(client.synthesize("Hello there.", "af_heart", 0.95)).isEqualTo(wav);
        server.verify();
    }

    @Test
    void unauthorisedIsClassifiedAsAuth() {
        server.expect(requestTo(BASE + "/v1/audio/speech")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.synthesize("x", "af_heart", 1.0))
                .isInstanceOfSatisfying(LlmProviderException.class,
                        failure -> assertThat(failure.getType()).isEqualTo(LlmFailureType.AUTH));
    }

    @Test
    void rateLimitAndServerErrorsAreUnavailableAndOtherClientErrorsAreRejected() {
        server.expect(requestTo(BASE + "/v1/audio/speech")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> client.synthesize("x", "af_heart", 1.0))
                .isInstanceOfSatisfying(LlmProviderException.class,
                        failure -> assertThat(failure.getType()).isEqualTo(LlmFailureType.UNAVAILABLE));

        server.reset();
        server.expect(requestTo(BASE + "/v1/audio/speech")).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> client.synthesize("x", "af_heart", 1.0))
                .isInstanceOfSatisfying(LlmProviderException.class,
                        failure -> assertThat(failure.getType()).isEqualTo(LlmFailureType.REJECTED));
    }

    @Test
    void emptyAudioBodyIsAnEmptyResponse() {
        server.expect(requestTo(BASE + "/v1/audio/speech")).andRespond(withSuccess(new byte[0], MediaType.parseMediaType("audio/wav")));

        assertThatThrownBy(() -> client.synthesize("x", "af_heart", 1.0))
                .isInstanceOfSatisfying(LlmProviderException.class,
                        failure -> assertThat(failure.getType()).isEqualTo(LlmFailureType.EMPTY_RESPONSE));
    }

    @Test
    void withoutAnUrlOrKeyNothingIsCalled() {
        properties.getKokoro().setBaseUrl("");
        var unconfigured = new KokoroSpeechClient(properties, RestClient.builder().build());

        assertThatThrownBy(() -> unconfigured.synthesize("x", "af_heart", 1.0))
                .isInstanceOfSatisfying(LlmProviderException.class,
                        failure -> assertThat(failure.getType()).isEqualTo(LlmFailureType.UNAVAILABLE));
    }
}
