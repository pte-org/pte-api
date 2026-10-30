package com.pte.practice.internal.service;

import com.pte.practice.internal.domain.PracticeResponse;
import com.pte.scoring.PracticeScoringException;
import com.pte.scoring.PracticeScoringFailure;
import com.pte.scoring.PracticeScoringRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PracticeScoringRequestFactoryTest {

    private final PracticeScoringRequestFactory factory =
            new PracticeScoringRequestFactory(new PracticeAnswerCanonicalizer(JsonMapper.builder().build()));

    @Test
    void speechItemCarriesTheMediaIdAndEveryAuthoredField() {
        UUID media = UUID.randomUUID();
        UUID image = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        PracticeResponse item = item("DESCRIBE_IMAGE", "DESCRIBE_IMAGE_V1",
                "{\"mediaPublicId\":\"" + media + "\"}");
        item.setPromptText("Describe the chart");
        item.setReferenceAnswerText("model answer");
        item.setCorrectAnswerText("answer key");
        item.setImagePromptRef(image);
        item.setMinWordCount(10);
        item.setMaxWordCount(60);

        PracticeScoringRequest request = factory.build(item, tenant);

        assertThat(request.taskType()).isEqualTo("DESCRIBE_IMAGE");
        assertThat(request.payload()).isEqualTo(media.toString());
        assertThat(request.promptText()).isEqualTo("Describe the chart");
        assertThat(request.referenceAnswerText()).isEqualTo("model answer");
        assertThat(request.correctAnswerText()).isEqualTo("answer key");
        assertThat(request.imagePromptRef()).isEqualTo(image);
        assertThat(request.minWordCount()).isEqualTo(10);
        assertThat(request.maxWordCount()).isEqualTo(60);
        assertThat(request.tenantId()).isEqualTo(tenant);
    }

    @Test
    void writingItemCarriesTheRawText() {
        PracticeResponse item = item("SUMMARIZE_WRITTEN_TEXT", "SUMMARIZE_WRITTEN_TEXT_V1", "{\"text\":\"A short summary.\"}");

        assertThat(factory.build(item, UUID.randomUUID()).payload()).isEqualTo("A short summary.");
    }

    @Test
    void objectiveItemCarriesTheFrozenOptionSnapshot() {
        PracticeResponse item = item("MC_SINGLE_ANSWER_READING", "MC_READING_SINGLE_V1",
                "{\"selectedOption\":\"2\"}");
        item.setObjectiveOptionsJson("[{\"text\":\"a\",\"correct\":true,\"orderIndex\":0}]");

        PracticeScoringRequest request = factory.build(item, UUID.randomUUID());

        assertThat(request.payload()).isEqualTo("2");
        assertThat(request.optionsJson()).isEqualTo(item.getObjectiveOptionsJson());
    }

    @Test
    void anAnswerThatCannotBeCanonicalisedIsInvalidPayload() {
        PracticeResponse item = item("SUMMARIZE_WRITTEN_TEXT", "SUMMARIZE_WRITTEN_TEXT_V1", "{\"unexpected\":true}");

        assertThatThrownBy(() -> factory.build(item, UUID.randomUUID()))
                .isInstanceOfSatisfying(PracticeScoringException.class,
                        ex -> assertThat(ex.failure()).isEqualTo(PracticeScoringFailure.INVALID_PAYLOAD));
    }

    @Test
    void aMissingAnswerIsInvalidPayload() {
        PracticeResponse item = item("SUMMARIZE_WRITTEN_TEXT", "SUMMARIZE_WRITTEN_TEXT_V1", null);

        assertThatThrownBy(() -> factory.build(item, UUID.randomUUID()))
                .isInstanceOfSatisfying(PracticeScoringException.class,
                        ex -> assertThat(ex.failure()).isEqualTo(PracticeScoringFailure.INVALID_PAYLOAD));
    }

    private static PracticeResponse item(String taskCode, String rendererKey, String payload) {
        PracticeResponse item = new PracticeResponse();
        item.setTaskCode(taskCode);
        item.setRendererKey(rendererKey);
        item.setSavedPayload(payload);
        return item;
    }
}
