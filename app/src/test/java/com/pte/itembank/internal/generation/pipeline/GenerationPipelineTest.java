package com.pte.itembank.internal.generation.pipeline;

import com.pte.billing.BillingSettingsService;
import com.pte.itembank.internal.generation.config.GenerationLimits;
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
            properties, avoidTopicsProvider, itemReviewer, mediaRenderer, liveFeed, imageReviewer,
            new GenerationLimits(mock(BillingSettingsService.class, call -> call.getArgument(1)), properties));
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
    void aChartTheImageReviewerKeepsRefusingIsRedrawnThenKeptForAPerson() {
        ready();
        properties.getLimits().setMaxCallsPerItem(15);
        properties.getLimits().setMaxCallsPerRound(5);
        properties.getReview().setVisionCheck(true);
        var chart = new com.pte.itembank.internal.generation.draft.ItemDraft.ChartDraft("Energy", "bar", "Use",
                "Year", "TWh", "TWh", List.of("2020", "2021", "2022"), List.of(), List.of());
        GeneratedItem chartItem = new GeneratedItem(mock(CreateQuestionRequest.class), "stimulus", "topic", "llm",
                chart);
        when(generator.generate(any(), any())).thenReturn(chartItem);
        when(itemReviewer.review(any(GeneratedItem.class), any(), any(), any(), any()))
                .thenReturn(new com.pte.itembank.internal.generation.review.ReviewVerdict("PASS", java.util.Map.of(), null));
        UUID image = UUID.randomUUID();
        byte[] png = {1, 2, 3};
        when(mediaRenderer.render(any(), any(), any(Integer.class), any(Long.class), any(), any())).thenReturn(
                java.util.Optional.of(new com.pte.itembank.internal.generation.media.RenderedMedia(null, image,
                        java.util.Map.of(), png)));
        when(imageReviewer.review(chart, png)).thenReturn(
                new com.pte.itembank.internal.generation.review.ChartImageReviewer.Result(false,
                        List.of("The legend is missing.")));

        when(writer.save(any(), any(), any(Integer.class), any(), any())).thenReturn(ItemOutcome.SAVED);

        // 15 calls at 5 a round give 3 rounds: two redraws, then the last chart is kept.
        assertThat(pipeline.runItem(claim, admin, 0)).isEqualTo(ItemOutcome.SAVED);

        verify(liveFeed).draft(claim.jobPublicId(), 0, 1, chartItem.question());
        verify(liveFeed).draft(claim.jobPublicId(), 0, 3, chartItem.question());
        verify(liveFeed, org.mockito.Mockito.times(3)).review(claim.jobPublicId(), 0, "IMAGE", false,
                "The legend is missing.", java.util.Map.of());
        verify(mediaRenderer, org.mockito.Mockito.times(2)).discard(null, image, admin);
        verify(itemState, never()).fail(any(), any(Integer.class), any(), any());
    }

    @Test
    void aBrokenDraftAndAPassingProviderFailureAreWrittenAgainInsteadOfFailing() {
        ready();
        when(generator.generate(any(), any()))
                .thenThrow(new com.pte.itembank.internal.generation.exception.GenerationRequestInvalidException(
                        QuestionGenerationConstants.GENERATION_VALIDATION_FAILED, "WORD_COUNT"))
                .thenThrow(new LlmProviderException(
                        LlmFailureType.UNAVAILABLE))
                .thenReturn(item);
        when(writer.save(any(), any(), any(Integer.class), any(), any())).thenReturn(ItemOutcome.SAVED);

        assertThat(pipeline.runItem(claim, admin, 0)).isEqualTo(ItemOutcome.SAVED);

        verify(generator, org.mockito.Mockito.times(3)).generate(any(), any());
        verify(itemState, never()).fail(any(), any(Integer.class), any(), any());
    }

    @Test
    void aJobFromTheQuestionFormEndsReadyWithoutSavingAQuestion() {
        JobClaim formClaim = new JobClaim(claim.jobId(), claim.jobPublicId(), claim.requesterId(),
                claim.taskTypeKey(), claim.cefrLevel(), claim.pool(), claim.topicHint(), claim.workerId(), false);
        when(itemState.advance(formClaim, 0, ItemStage.GENERATING)).thenReturn(true);
        when(generator.supports("MC_READING_SINGLE")).thenReturn(true);
        when(avoidTopicsProvider.getAvoidTopicsForTaskType("MC_READING_SINGLE")).thenReturn("No prior topics.");
        when(generator.generate(any(), any())).thenReturn(item);

        assertThat(pipeline.runItem(formClaim, admin, 0)).isEqualTo(ItemOutcome.SAVED);

        verify(itemState).ready(formClaim, 0);
        verify(liveFeed).stage(formClaim.jobPublicId(), 0, ItemStage.READY);
        verify(writer, never()).save(any(), any(), any(Integer.class), any(), any());
    }

    @Test
    void withoutTheVisionCheckAChartIsNotSentToTheImageReviewer() {
        ready();
        var chart = new com.pte.itembank.internal.generation.draft.ItemDraft.ChartDraft("Energy", "bar", "Use",
                "Year", "TWh", "TWh", List.of("2020", "2021", "2022"), List.of(), List.of());
        when(generator.generate(any(), any())).thenReturn(new GeneratedItem(mock(CreateQuestionRequest.class),
                "stimulus", "topic", "llm", chart));
        when(itemReviewer.review(any(GeneratedItem.class), any(), any(), any(), any()))
                .thenReturn(new com.pte.itembank.internal.generation.review.ReviewVerdict("PASS", java.util.Map.of(), null));
        when(mediaRenderer.render(any(), any(), any(Integer.class), any(Long.class), any(), any())).thenReturn(
                java.util.Optional.of(new com.pte.itembank.internal.generation.media.RenderedMedia(null,
                        UUID.randomUUID(), java.util.Map.of(), new byte[]{1})));

        pipeline.runItem(claim, admin, 0);

        verify(imageReviewer, never()).review(any(ItemDraft.ChartDraft.class), any());
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
    void mediaStoredBeforeAnUnexpectedFailureIsRemoved() {
        ready();
        var chart = new com.pte.itembank.internal.generation.draft.ItemDraft.ChartDraft("Energy", "bar", "Use",
                "Year", "TWh", "TWh", List.of("2020", "2021", "2022"), List.of(), List.of());
        when(generator.generate(any(), any())).thenReturn(new GeneratedItem(mock(CreateQuestionRequest.class),
                "stimulus", "topic", "llm", chart));
        when(itemReviewer.review(any(GeneratedItem.class), any(), any(), any(), any()))
                .thenReturn(new com.pte.itembank.internal.generation.review.ReviewVerdict("PASS", java.util.Map.of(), null));
        var rendered = new com.pte.itembank.internal.generation.media.RenderedMedia(null, UUID.randomUUID(),
                java.util.Map.of(), new byte[]{1});
        when(mediaRenderer.render(any(), any(), any(Integer.class), any(Long.class), any(), any()))
                .thenReturn(java.util.Optional.of(rendered));
        org.mockito.Mockito.doThrow(new IllegalStateException("live view down")).when(liveFeed)
                .media(any(), any(Integer.class), any(), any());

        assertThat(pipeline.runItem(claim, admin, 0)).isEqualTo(ItemOutcome.FAILED);
        verify(mediaRenderer).discard(rendered, admin);
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

    private JobClaim pictureClaim() {
        JobClaim picture = new JobClaim(6L, UUID.randomUUID(), claim.requesterId(), "DESCRIBE_IMAGE", CefrLevel.B2,
                QuestionPool.PRACTICE, "Recycling", "worker-1", true,
                com.pte.itembank.internal.generation.domain.ImageKind.PICTURE);
        when(itemState.advance(picture, 0, ItemStage.GENERATING)).thenReturn(true);
        when(generator.supports("DESCRIBE_IMAGE")).thenReturn(true);
        when(itemReviewer.review(any(GeneratedItem.class), any(), any(), any(), any()))
                .thenReturn(new com.pte.itembank.internal.generation.review.ReviewVerdict("PASS", java.util.Map.of(), null));
        when(writer.save(any(), any(), any(Integer.class), any(), any())).thenReturn(ItemOutcome.SAVED);
        when(mediaRenderer.pictureSettings()).thenReturn(
                new com.pte.itembank.internal.generation.prompt.PictureSettings(2, true));
        return picture;
    }

    @Test
    void aPictureThatCannotBePaintedIsMadeAgainAsAChart() {
        JobClaim picture = pictureClaim();
        var pictureDraft = new com.pte.itembank.internal.generation.draft.ItemDraft.PictureDraft("Recycling",
                "Recycling", "A flowchart of four recycling stages", List.of("a", "b", "c"), "Model answer.");
        var chartDraft = new com.pte.itembank.internal.generation.draft.ItemDraft.ChartDraft("Recycling", "BAR",
                "Rates", "Year", "Percent", "percent", List.of("2020", "2021", "2022"), List.of(), List.of());
        GeneratedItem pictureItem = new GeneratedItem(mock(CreateQuestionRequest.class), "s", "t", "llm", pictureDraft);
        GeneratedItem chartItem = new GeneratedItem(mock(CreateQuestionRequest.class), "s", "t", "llm", chartDraft);
        org.mockito.ArgumentCaptor<GenerationRequest> asked = org.mockito.ArgumentCaptor.forClass(GenerationRequest.class);
        when(generator.generate(asked.capture(), any())).thenReturn(pictureItem, chartItem);
        UUID image = UUID.randomUUID();
        when(mediaRenderer.render(eq(pictureDraft), any(), any(Integer.class), any(Long.class), any(), any()))
                .thenThrow(new com.pte.itembank.internal.generation.exception.GenerationMediaException(
                        QuestionGenerationConstants.GENERATION_IMAGE_FAILED, "UNAVAILABLE"));
        when(mediaRenderer.render(eq(chartDraft), any(), any(Integer.class), any(Long.class), any(), any())).thenReturn(
                java.util.Optional.of(new com.pte.itembank.internal.generation.media.RenderedMedia(null, image,
                        java.util.Map.of())));

        assertThat(pipeline.runItem(picture, admin, 0)).isEqualTo(ItemOutcome.SAVED);

        assertThat(asked.getAllValues()).extracting(GenerationRequest::isPicture).containsExactly(true, false);
        verify(liveFeed).review(picture.jobPublicId(), 0, "IMAGE", false,
                QuestionGenerationConstants.IMAGE_FALLBACK_NOTE + "UNAVAILABLE", java.util.Map.of());
        verify(itemState, never()).fail(any(), any(Integer.class), any(), any());
    }

    @Test
    void aPaintedPictureThatPassesIsKeptWithoutAChart() {
        JobClaim picture = pictureClaim();
        var pictureDraft = new com.pte.itembank.internal.generation.draft.ItemDraft.PictureDraft("Recycling",
                "Recycling", "A flowchart of four recycling stages", List.of("a", "b", "c"), "Model answer.");
        when(generator.generate(any(), any())).thenReturn(
                new GeneratedItem(mock(CreateQuestionRequest.class), "s", "t", "llm", pictureDraft));
        when(mediaRenderer.render(any(), any(), any(Integer.class), any(Long.class), any(), any())).thenReturn(
                java.util.Optional.of(new com.pte.itembank.internal.generation.media.RenderedMedia(null,
                        UUID.randomUUID(), java.util.Map.of())));

        assertThat(pipeline.runItem(picture, admin, 0)).isEqualTo(ItemOutcome.SAVED);

        verify(generator, org.mockito.Mockito.times(1)).generate(any(), any());
    }

    @Test
    void aPictureSetToFailIsNotMadeAsAChart() {
        JobClaim picture = pictureClaim();
        when(mediaRenderer.pictureSettings()).thenReturn(
                new com.pte.itembank.internal.generation.prompt.PictureSettings(2, false));
        var pictureDraft = new com.pte.itembank.internal.generation.draft.ItemDraft.PictureDraft("Recycling",
                "Recycling", "A flowchart of four recycling stages", List.of("a", "b", "c"), "Model answer.");
        when(generator.generate(any(), any())).thenReturn(
                new GeneratedItem(mock(CreateQuestionRequest.class), "s", "t", "llm", pictureDraft));
        when(mediaRenderer.render(any(), any(), any(Integer.class), any(Long.class), any(), any()))
                .thenThrow(new com.pte.itembank.internal.generation.exception.GenerationMediaException(
                        QuestionGenerationConstants.GENERATION_IMAGE_FAILED, "UNAVAILABLE"));

        assertThat(pipeline.runItem(picture, admin, 0)).isEqualTo(ItemOutcome.FAILED);

        verify(generator, org.mockito.Mockito.times(1)).generate(any(), any());
    }
}
