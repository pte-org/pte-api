package com.pte.itembank.internal.generation.batch.dto;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.dto.response.GenerationMetaResponse;
import com.pte.itembank.dto.response.OptionResponse;
import com.pte.itembank.dto.response.QuestionResponse;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.domain.ItemStage;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJobItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BatchQuestionResponseTest {

    private static final UUID JOB = UUID.randomUUID();
    private static final UUID QUESTION = UUID.randomUUID();

    private static QuestionGenerationJob job() {
        QuestionGenerationJob job = mock(QuestionGenerationJob.class);
        when(job.getPublicId()).thenReturn(JOB);
        when(job.getBatchSeq()).thenReturn(2);
        when(job.getTaskTypeKey()).thenReturn("MC_READING_SINGLE");
        when(job.getCefrLevel()).thenReturn(CefrLevel.B2);
        return job;
    }

    private static QuestionGenerationJobItem item(ItemStage stage, UUID question) {
        QuestionGenerationJobItem item = mock(QuestionGenerationJobItem.class);
        when(item.getItemIndex()).thenReturn(1);
        when(item.getStage()).thenReturn(stage);
        when(item.getQuestionPublicId()).thenReturn(question);
        when(item.getFailureCode()).thenReturn(stage == ItemStage.FAILED ? "GENERATION_TTS_FAILED" : null);
        return item;
    }

    @Test
    void aSlotWithoutAQuestionCarriesOnlyWhereItStands() {
        BatchQuestionResponse slot = BatchQuestionResponse.of(job(), item(ItemStage.FAILED, null), null);

        assertThat(slot.stage()).isEqualTo("FAILED");
        assertThat(slot.failureCode()).isEqualTo("GENERATION_TTS_FAILED");
        assertThat(slot.cefrLevel()).isEqualTo("B2");
        assertThat(slot.batchSeq()).isEqualTo(2);
        assertThat(slot.promptExcerpt()).isNull();
        assertThat(slot.options()).isEmpty();
    }

    @Test
    void aSavedQuestionIsPreviewedWithItsOptionsMediaAndReview() {
        QuestionResponse question = mock(QuestionResponse.class);
        when(question.publicId()).thenReturn(QUESTION);
        when(question.cefrLevel()).thenReturn("C1");
        when(question.section()).thenReturn("READING");
        when(question.promptText()).thenReturn("x".repeat(QuestionGenerationConstants.BATCH_PREVIEW_PROMPT_CHARS + 50));
        when(question.options()).thenReturn(List.of(
                new OptionResponse(UUID.randomUUID(), "Second", true, 1, null, null),
                new OptionResponse(UUID.randomUUID(), "First", false, 0, null, null)));
        GenerationMetaResponse meta = mock(GenerationMetaResponse.class);
        when(meta.topic()).thenReturn("Museums");
        when(meta.review()).thenReturn(new GenerationMetaResponse.ReviewVerdictResponse("PASS", Map.of(
                "format", new GenerationMetaResponse.CheckResultResponse(true, "", null),
                "cefr", new GenerationMetaResponse.CheckResultResponse(false, "too easy", null)), ""));
        when(question.generationMeta()).thenReturn(meta);

        BatchQuestionResponse slot = BatchQuestionResponse.of(job(), item(ItemStage.SAVED, QUESTION), question);

        assertThat(slot.questionPublicId()).isEqualTo(QUESTION);
        assertThat(slot.cefrLevel()).isEqualTo("C1");
        assertThat(slot.topic()).isEqualTo("Museums");
        assertThat(slot.promptExcerpt()).hasSize(QuestionGenerationConstants.BATCH_PREVIEW_PROMPT_CHARS);
        assertThat(slot.options()).containsExactly("First", "Second");
        assertThat(slot.correctOptionIndex()).isEqualTo(1);
        assertThat(slot.reviewPassed()).isEqualTo(1);
        assertThat(slot.reviewTotal()).isEqualTo(2);
    }
}
