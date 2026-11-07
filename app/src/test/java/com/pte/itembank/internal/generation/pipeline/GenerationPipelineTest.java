package com.pte.itembank.internal.generation.pipeline;

import com.pte.itembank.internal.generation.media.MediaRenderer;
import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.dto.request.CreateQuestionRequest;
import com.pte.itembank.internal.exception.QuestionValidationException;
import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.domain.ItemStage;
import com.pte.itembank.internal.generation.draft.ItemDraft;
import com.pte.itembank.internal.generation.exception.GenerationCallBudgetExceededException;
import com.pte.itembank.internal.generation.job.AvoidTopicsProvider;
import com.pte.itembank.internal.generation.llm.LlmFailureType;
import com.pte.itembank.internal.generation.llm.LlmProviderException;
import com.pte.itembank.internal.generation.review.LlmItemReviewer;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GenerationPipelineTest {

    private final ItemGenerator generator = mock(ItemGenerator.class);
    private final GeneratedQuestionWriter writer = mock(GeneratedQuestionWriter.class);
    private final GenerationItemStateService itemState = mock(GenerationItemStateService.class);
    private final QuestionGenerationProperties properties = new QuestionGenerationProperties();
    private final AvoidTopicsProvider avoidTopicsProvider = mock(AvoidTopicsProvider.class);
    private final LlmItemReviewer itemReviewer = mock(LlmItemReviewer.class);
    private final MediaRenderer mediaRenderer = mock(MediaRenderer.class);
    private final GenerationPipeline pipeline = new GenerationPipeline(List.of(generator), writer, itemState,
            properties, avoidTopicsProvider, itemReviewer, mediaRenderer);
    private final JobClaim claim = new JobClaim(5L, UUID.randomUUID(), UUID.randomUUID(), "MC_READING_SINGLE",
            CefrLevel.C1, QuestionPool.EXAM, "Climate", "worker-1");
    private final CurrentUser admin = new CurrentUser(claim.requesterId(), null, List.of("PLATFORM_ADMIN"));
    private final GeneratedItem item = new GeneratedItem(mock(CreateQuestionRequest.class), "stimulus", "topic",
            "stub");

    private void ready() {
        when(itemState.advance(claim, 0, ItemStage.GENERATING)).thenReturn(true);
        when(generator.supports("MC_READING_SINGLE")).thenReturn(true);
        when(avoidTopicsProvider.getAvoidTopicsForTaskType("MC_READING_SINGLE")).thenReturn("No prior topics.");
    }

    @Test
    void aSuccessfulItemGoesThroughTheGeneratorAndTheWriterWithTheRequestedFields() {
        ready();
        when(generator.generate(any(), any())).thenReturn(item);
        when(writer.save(claim, admin, 0, item, null)).thenReturn(ItemOutcome.SAVED);

        assertThat(pipeline.runItem(claim, admin, 0)).isEqualTo(ItemOutcome.SAVED);

        ArgumentCaptor<GenerationRequest> request = ArgumentCaptor.forClass(GenerationRequest.class);
        verify(generator).generate(request.capture(), any());
        assertThat(request.getValue().jobPublicId()).isEqualTo(claim.jobPublicId());
        assertThat(request.getValue().itemIndex()).isZero();
        assertThat(request.getValue().cefrLevel()).isEqualTo(CefrLevel.C1);
        assertThat(request.getValue().pool()).isEqualTo(QuestionPool.EXAM);
        assertThat(request.getValue().topicHint()).isEqualTo("Climate");
        verify(itemState, never()).fail(any(), any(Integer.class), any(), any());
    }

    @Test
    void theCallBudgetComesFromTheConfiguredAllowance() {
        properties.getLimits().setMaxCallsPerItem(4);
        ready();
        when(generator.generate(any(), any())).thenReturn(item);
        when(writer.save(any(), any(), any(Integer.class), any(), any())).thenReturn(ItemOutcome.SAVED);

        pipeline.runItem(claim, admin, 0);

        ArgumentCaptor<GenerationCallBudget> budget = ArgumentCaptor.forClass(GenerationCallBudget.class);
        verify(generator).generate(any(), budget.capture());
        assertThat(budget.getValue().remaining()).isEqualTo(4);
    }

    @Test
    void aLostLeaseStopsBeforeAnyWork() {
        when(itemState.advance(claim, 0, ItemStage.GENERATING)).thenReturn(false);

        assertThat(pipeline.runItem(claim, admin, 0)).isEqualTo(ItemOutcome.LEASE_LOST);
        verifyNoInteractions(generator, writer);
    }

    @Test
    void aTaskTypeNoGeneratorHandlesFailsTheItem() {
        when(itemState.advance(claim, 0, ItemStage.GENERATING)).thenReturn(true);
        when(generator.supports(any())).thenReturn(false);

        assertThat(pipeline.runItem(claim, admin, 0)).isEqualTo(ItemOutcome.FAILED);
        verify(itemState).fail(claim, 0, "GENERATION_NO_GENERATOR", null);
    }

    @Test
    void aRejectedProviderKeyIsReportedSoTheJobCanStop() {
        ready();
        when(generator.generate(any(), any())).thenThrow(new LlmProviderException(LlmFailureType.AUTH));

        assertThat(pipeline.runItem(claim, admin, 0)).isEqualTo(ItemOutcome.FAILED_PROVIDER_AUTH);
        verify(itemState).fail(claim, 0, "GENERATION_PROVIDER_AUTH", null);
    }

    @Test
    void anOutageIsReportedSoTheRunnerCanCountIt() {
        ready();
        when(generator.generate(any(), any())).thenThrow(new LlmProviderException(LlmFailureType.UNAVAILABLE));

        assertThat(pipeline.runItem(claim, admin, 0)).isEqualTo(ItemOutcome.FAILED_PROVIDER_UNAVAILABLE);
        verify(itemState).fail(claim, 0, "GENERATION_PROVIDER_UNAVAILABLE", null);
    }

    @Test
    void aRejectedOrEmptyAnswerOnlyFailsThatItem() {
        ready();
        when(generator.generate(any(), any())).thenThrow(new LlmProviderException(LlmFailureType.EMPTY_RESPONSE));

        assertThat(pipeline.runItem(claim, admin, 0)).isEqualTo(ItemOutcome.FAILED);
        verify(itemState).fail(claim, 0, "GENERATION_EMPTY_RESPONSE", null);
    }

    @Test
    void anExhaustedCallBudgetFailsTheItemWithItsOwnCode() {
        ready();
        when(generator.generate(any(), any())).thenThrow(new GenerationCallBudgetExceededException());

        assertThat(pipeline.runItem(claim, admin, 0)).isEqualTo(ItemOutcome.FAILED);
        verify(itemState).fail(claim, 0, "GENERATION_CALL_BUDGET_EXCEEDED", null);
    }

    @Test
    void theItembankRejectingTheDraftKeepsItsStableCode() {
        ready();
        when(generator.generate(any(), any())).thenReturn(item);
        when(writer.save(any(), any(), any(Integer.class), any(), any()))
                .thenThrow(new QuestionValidationException("OPTIONS_REQUIRED"));

        assertThat(pipeline.runItem(claim, admin, 0)).isEqualTo(ItemOutcome.FAILED);
        verify(itemState).fail(claim, 0, "GENERATION_ITEMBANK_REJECTED", "OPTIONS_REQUIRED");
    }

    @Test
    void anUnexpectedErrorIsRecordedWithoutItsMessage() {
        ready();
        when(generator.generate(any(), any())).thenThrow(new IllegalStateException("contains the secret prompt"));

        assertThat(pipeline.runItem(claim, admin, 0)).isEqualTo(ItemOutcome.FAILED);
        verify(itemState).fail(eq(claim), eq(0), eq("GENERATION_INTERNAL_ERROR"), isNull());
    }

    @Test
    void theWritersOutcomeIsPassedThrough() {
        ready();
        when(generator.generate(any(), any())).thenReturn(item);
        when(writer.save(claim, admin, 0, item, null)).thenReturn(ItemOutcome.CANCELLED);

        assertThat(pipeline.runItem(claim, admin, 0)).isEqualTo(ItemOutcome.CANCELLED);
    }
}
