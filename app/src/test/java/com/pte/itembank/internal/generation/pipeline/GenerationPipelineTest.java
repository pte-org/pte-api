package com.pte.itembank.internal.generation.pipeline;

import com.pte.itembank.internal.generation.live.GenerationLiveContext;
import com.pte.itembank.internal.generation.live.GenerationLiveFeed;
import com.pte.itembank.internal.generation.llm.LlmRole;
import com.pte.itembank.internal.generation.llm.TokenSink;
import java.util.concurrent.atomic.AtomicReference;
import com.pte.itembank.internal.generation.media.MediaRenderer;
import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.dto.request.CreateQuestionRequest;
import com.pte.itembank.internal.exception.QuestionValidationException;
import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
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
    private final GenerationLiveFeed liveFeed = mock(GenerationLiveFeed.class);
    private final com.pte.itembank.internal.generation.review.ChartImageReviewer imageReviewer =
            mock(com.pte.itembank.internal.generation.review.ChartImageReviewer.class);
    private final GenerationPipeline pipeline = new GenerationPipeline(List.of(generator), writer, itemState,
            properties, avoidTopicsProvider, itemReviewer, mediaRenderer, liveFeed, imageReviewer);
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
    void theLiveViewHearsTheStagesAndTheEndOfTheItemAndTheThreadIsLeftClean() {
        ready();
        AtomicReference<TokenSink> during =
                new AtomicReference<>();
        when(generator.generate(any(), any())).thenAnswer(call -> {
            during.set(GenerationLiveContext.sinkFor(LlmRole.GENERATOR));
            throw new IllegalStateException("boom");
        });

        assertThat(pipeline.runItem(claim, admin, 0)).isEqualTo(ItemOutcome.FAILED);

        verify(liveFeed).stage(claim.jobPublicId(), 0, ItemStage.GENERATING);
        verify(liveFeed).sink(claim.jobPublicId(), 0, LlmRole.GENERATOR);
        verify(liveFeed).itemEnded(claim.jobPublicId(), 0);
        assertThat(during.get()).isNotSameAs(TokenSink.NONE);
        assertThat(GenerationLiveContext.sinkFor(LlmRole.GENERATOR))
                .isSameAs(TokenSink.NONE);
    }

    @Test
    void aChartTheImageReviewerRefusesFailsTheItemAndItsImageIsRemoved() {
        ready();
        properties.getReview().setVisionCheck(true);
        var chart = new com.pte.itembank.internal.generation.draft.ItemDraft.ChartDraft("Energy", "bar", "Use",
                "Year", "TWh", "TWh", List.of("2020", "2021", "2022"), List.of(), List.of());
        GeneratedItem chartItem = new GeneratedItem(mock(CreateQuestionRequest.class), "stimulus", "topic", "llm",
                chart);
        when(generator.generate(any(), any())).thenReturn(chartItem);
        when(itemReviewer.review(any(), any(), any(), any(), any()))
                .thenReturn(new com.pte.itembank.internal.generation.review.ReviewVerdict("PASS", java.util.Map.of(), null));
        UUID image = UUID.randomUUID();
        byte[] png = {1, 2, 3};
        when(mediaRenderer.render(any(), any(), any(Integer.class), any(Long.class), any())).thenReturn(
                java.util.Optional.of(new com.pte.itembank.internal.generation.media.RenderedMedia(null, image,
                        java.util.Map.of(), png)));
        when(imageReviewer.review(chart, png)).thenReturn(
                new com.pte.itembank.internal.generation.review.ChartImageReviewer.Result(false,
                        List.of("The legend is missing.")));

        assertThat(pipeline.runItem(claim, admin, 0)).isEqualTo(ItemOutcome.FAILED);

        verify(mediaRenderer).discard(null, image, admin);
        verify(itemState).fail(claim, 0, QuestionGenerationConstants.GENERATION_IMAGE_REVIEW_FAILED,
                "The legend is missing.");
        verify(writer, never()).save(any(), any(), any(Integer.class), any(), any());
    }

    @Test
    void withoutTheVisionCheckAChartIsNotSentToTheImageReviewer() {
        ready();
        var chart = new com.pte.itembank.internal.generation.draft.ItemDraft.ChartDraft("Energy", "bar", "Use",
                "Year", "TWh", "TWh", List.of("2020", "2021", "2022"), List.of(), List.of());
        when(generator.generate(any(), any())).thenReturn(new GeneratedItem(mock(CreateQuestionRequest.class),
                "stimulus", "topic", "llm", chart));
        when(itemReviewer.review(any(), any(), any(), any(), any()))
                .thenReturn(new com.pte.itembank.internal.generation.review.ReviewVerdict("PASS", java.util.Map.of(), null));
        when(mediaRenderer.render(any(), any(), any(Integer.class), any(Long.class), any())).thenReturn(
                java.util.Optional.of(new com.pte.itembank.internal.generation.media.RenderedMedia(null,
                        UUID.randomUUID(), java.util.Map.of(), new byte[]{1})));

        pipeline.runItem(claim, admin, 0);

        verify(imageReviewer, never()).review(any(), any());
    }

    @Test
    void aStageTheItemNeverReachedIsNotReported() {
        when(itemState.advance(claim, 0, ItemStage.GENERATING)).thenReturn(false);

        assertThat(pipeline.runItem(claim, admin, 0)).isEqualTo(ItemOutcome.LEASE_LOST);

        verify(liveFeed, never()).stage(any(), any(Integer.class), any());
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
