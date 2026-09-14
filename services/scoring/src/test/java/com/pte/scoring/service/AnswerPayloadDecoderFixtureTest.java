package com.pte.scoring.service;

import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.dto.response.AnswerPayloadKind;
import com.pte.scoring.dto.response.DecodedAnswerPayload;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the two structured Listening decoders against the versioned
 * cross-repo fixture. The fixture is loaded from scoring's vendored test
 * resource; its schema/version is independently guarded by
 * {@link ListeningPayloadFixtureSchemaTest}. Contract changes start in
 * pte-doc, then update both vendored copies and their expected-version constants.
 */
class AnswerPayloadDecoderFixtureTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final AnswerPayloadDecoder decoder = new AnswerPayloadDecoder(jsonMapper);

    @Test
    void fillBlanksListening_decodesTypedValuesAndPreservesTrailingEmptyGap() throws IOException {
        Map<String, Object> fixture = fixtureFor("FILL_BLANKS_LISTENING");

        DecodedAnswerPayload decoded = decoder.decode(answerFrom(fixture));

        assertThat(decoded.kind()).isEqualTo(AnswerPayloadKind.POSITIONAL_SELECTION);
        assertThat(decoded.gapValues()).containsExactly("rapid", null, "forest", null);
        assertThat(decoded.text()).isNull();
        assertThat(decoded.options()).isNull();
        assertThat(decoded.wordIndices()).isNull();
    }

    @Test
    void highlightIncorrectWords_decodesTranscriptIndicesNotOptionIndexes() throws IOException {
        Map<String, Object> fixture = fixtureFor("HIGHLIGHT_INCORRECT_WORDS");

        DecodedAnswerPayload decoded = decoder.decode(answerFrom(fixture));

        assertThat(decoded.kind()).isEqualTo(AnswerPayloadKind.WORD_INDICES);
        assertThat(decoded.wordIndices()).containsExactly(3, 7, 11);
        assertThat(decoded.text()).isNull();
        assertThat(decoded.options()).isNull();
        assertThat(decoded.gapValues()).isNull();
    }

    @Test
    void malformedWordIndices_areUnrecognizedAndRetainRawPayload() {
        DecodedAnswerPayload decoded = decoder.decode(
                answer("HIGHLIGHT_INCORRECT_WORDS", null, "3,not-an-index,7"));

        assertThat(decoded.kind()).isEqualTo(AnswerPayloadKind.UNRECOGNIZED);
        assertThat(decoded.text()).isEqualTo("3,not-an-index,7");
        assertThat(decoded.wordIndices()).isNull();
    }

    @Test
    void existingTextSelectionAndAudioShapesRemainUnchanged() {
        DecodedAnswerPayload text = decoder.decode(
                answer("SUMMARIZE_SPOKEN_TEXT", null, "summary draft"));
        assertThat(text.kind()).isEqualTo(AnswerPayloadKind.TEXT);
        assertThat(text.text()).isEqualTo("summary draft");

        String optionsJson = "[{\"text\":\"A\",\"correct\":false,\"orderIndex\":0},"
                + "{\"text\":\"B\",\"correct\":true,\"orderIndex\":1}]";
        DecodedAnswerPayload selection = decoder.decode(
                answer("MC_LISTENING_MULTIPLE", optionsJson, "1"));
        assertThat(selection.kind()).isEqualTo(AnswerPayloadKind.SELECTION);
        assertThat(selection.options()).hasSize(2);

        String mediaPublicId = "00000000-0000-0000-0000-000000000001";
        DecodedAnswerPayload audio = decoder.decode(answer("READ_ALOUD", null, mediaPublicId));
        assertThat(audio.kind()).isEqualTo(AnswerPayloadKind.AUDIO);
        assertThat(audio.mediaPublicId()).hasToString(mediaPublicId);
    }

    @Test
    void nullTaskType_fallsBackToTextWithoutSetLookupFailure() {
        DecodedAnswerPayload decoded = decoder.decode(answer(null, null, "untyped payload"));

        assertThat(decoded.kind()).isEqualTo(AnswerPayloadKind.TEXT);
        assertThat(decoded.text()).isEqualTo("untyped payload");
    }

    private Map<String, Object> fixtureFor(String taskType) throws IOException {
        Map<String, Object> root = jsonMapper.readValue(fixtureJson(), new TypeReference<>() {
        });
        List<?> fixtures = (List<?>) root.get("fixtures");
        for (Object rawFixture : fixtures) {
            Map<?, ?> fixture = (Map<?, ?>) rawFixture;
            if (taskType.equals(fixture.get("taskType"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typedFixture = (Map<String, Object>) fixture;
                return typedFixture;
            }
        }
        throw new IllegalStateException("Fixture entry is missing for taskType=" + taskType);
    }

    private ScoringAnswer answerFrom(Map<String, Object> fixture) {
        return answer(
                (String) fixture.get("taskType"),
                (String) fixture.get("optionsJson"),
                (String) fixture.get("payload"));
    }

    private ScoringAnswer answer(String taskType, String optionsJson, String payload) {
        ScoringAnswer answer = new ScoringAnswer();
        answer.setTaskType(taskType);
        answer.setOptionsJson(optionsJson);
        answer.setPayload(payload);
        return answer;
    }

    private String fixtureJson() {
        try (InputStream stream = getClass().getResourceAsStream(
                "/fixtures/listening-payload-contract.json")) {
            if (stream == null) {
                throw new IllegalStateException(
                        "Vendored Listening payload fixture is missing from scoring test resources: "
                                + "/fixtures/listening-payload-contract.json");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read vendored Listening payload fixture", ex);
        }
    }
}
