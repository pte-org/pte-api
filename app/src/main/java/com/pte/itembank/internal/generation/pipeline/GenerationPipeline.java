package com.pte.itembank.internal.generation.pipeline;

import com.pte.itembank.internal.generation.config.GenerationLimits;
import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.constant.GenerationLiveConstants;
import com.pte.itembank.internal.generation.domain.ItemStage;
import com.pte.itembank.internal.generation.exception.GenerationCallBudgetExceededException;
import com.pte.itembank.internal.generation.job.AvoidTopicsProvider;
import com.pte.itembank.internal.generation.live.GenerationLiveContext;
import com.pte.itembank.internal.generation.live.GenerationLiveFeed;
import com.pte.itembank.internal.generation.llm.LlmFailureType;
import com.pte.itembank.internal.generation.llm.LlmProviderException;
import com.pte.itembank.internal.generation.review.LlmItemReviewer;
import com.pte.itembank.internal.generation.review.ReviewVerdict;
import com.pte.itembank.internal.generation.exception.GenerationMediaException;
import com.pte.itembank.internal.generation.exception.GenerationRequestInvalidException;
import com.pte.itembank.internal.generation.media.MediaRenderer;
import com.pte.itembank.internal.generation.review.ChartImageReviewer;
import com.pte.itembank.internal.generation.media.RenderedMedia;
import java.util.Optional;
import com.pte.shared.exception.DomainException;
import com.pte.shared.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Runs ONE question: generate → validate → duplicate check → review → save.
 * All provider calls happen outside transactions. Every failure is recorded on the item
 * and turned into an {@link ItemOutcome}; only outcomes are logged, never prompt/response/provider data.
 * What the item goes through is also reported to the live view of the job, which never changes the outcome.
 */
@Service
public class GenerationPipeline {

    private static final Logger LOG = LoggerFactory.getLogger(GenerationPipeline.class);

    private final List<ItemGenerator> generators;
    private final GeneratedQuestionWriter writer;
    private final GenerationItemStateService itemState;
    private final QuestionGenerationProperties properties;
    private final AvoidTopicsProvider avoidTopicsProvider;
    private final LlmItemReviewer itemReviewer;
    private final MediaRenderer mediaRenderer;
    private final GenerationLiveFeed liveFeed;
    private final ChartImageReviewer imageReviewer;
    private final GenerationLimits allowance;

    public GenerationPipeline(List<ItemGenerator> generators, GeneratedQuestionWriter writer,
                              GenerationItemStateService itemState, QuestionGenerationProperties properties,
                              AvoidTopicsProvider avoidTopicsProvider, LlmItemReviewer itemReviewer,
                              MediaRenderer mediaRenderer, GenerationLiveFeed liveFeed,
                              ChartImageReviewer imageReviewer, GenerationLimits allowance) {
        this.imageReviewer = imageReviewer;
        this.allowance = allowance;
        this.generators = generators;
        this.writer = writer;
        this.itemState = itemState;
        this.properties = properties;
        this.avoidTopicsProvider = avoidTopicsProvider;
        this.itemReviewer = itemReviewer;
        this.mediaRenderer = mediaRenderer;
        this.liveFeed = liveFeed;
    }

    public ItemOutcome runItem(JobClaim claim, CurrentUser caller, int index) {
        GenerationLiveContext.open(liveFeed, claim.jobPublicId(), index);
        try {
            return run(claim, caller, index);
        } finally {
            GenerationLiveContext.close();
            liveFeed.itemEnded(claim.jobPublicId(), index);
        }
    }

    private ItemOutcome run(JobClaim claim, CurrentUser caller, int index) {
        if (!advance(claim, index, ItemStage.GENERATING)) {
            return ItemOutcome.LEASE_LOST;
        }
        // Media of the current draft not yet handed on: removed if the item fails, so no file is left unused.
        RenderedMedia stored = null;
        try {
            int maxCalls = allowance.maxCallsPerItem();
            GenerationCallBudget budget = new GenerationCallBudget(maxCalls);

            ItemGenerator generator = generators.stream()
                    .filter(candidate -> candidate.supports(claim.taskTypeKey())).findFirst().orElse(null);
            if (generator == null) {
                return fail(claim, index, QuestionGenerationConstants.GENERATION_NO_GENERATOR, null,
                        ItemOutcome.FAILED);
            }

            String avoidTopics = avoidTopicsProvider.getAvoidTopicsForTaskType(claim.taskTypeKey());
            GenerationRequest request = new GenerationRequest(claim.jobPublicId(), index, claim.taskTypeKey(),
                    claim.cefrLevel(), claim.pool(), claim.topicHint(), avoidTopics, null,
                    claim.imageKind().resolve(claim.topicHint()));
            GeneratedImageCheck images = new GeneratedImageCheck(properties, imageReviewer, liveFeed, mediaRenderer);
            // Write, review and make the media until every reviewer is satisfied or the item's calls run out: a broken
            // answer, a passing provider failure or a refusal goes back to the writer as feedback. When the calls run
            // out on a content refusal, the last draft is still saved with the reviewer's notes for a person to finish.
            // The allowance also caps the rounds, even for a generator that does not count its calls.
            String feedback = null;
            int callsPerRound = properties.getLimits().getMaxCallsPerRound();
            int maxRounds = Math.max(1, maxCalls / callsPerRound);
            GeneratedItem item;
            ReviewVerdict verdict;
            for (int round = 1; ; round++) {
                if (round > 1 && itemState.cancelRequested(claim)) {
                    return ItemOutcome.CANCELLED; // stopped by a person: the draft in progress is dropped
                }
                if (round > 1) {
                    advance(claim, index, ItemStage.GENERATING);
                }
                try {
                    item = generator.generate(feedback == null ? request : request.withFeedback(feedback), budget);
                } catch (GenerationRequestInvalidException invalid) {
                    if (!isFixable(invalid.getCode()) || !canRewrite(budget, round, maxRounds)) {
                        throw invalid;
                    }
                    feedback = QuestionGenerationConstants.FEEDBACK_INVALID + " " + invalid.getUserMessage();
                    continue;
                } catch (LlmProviderException passing) {
                    if (!isPassing(passing.getType()) || !canRewrite(budget, round, maxRounds)) {
                        throw passing;
                    }
                    continue; // the provider failed for a moment: ask again with the same feedback
                }
                liveFeed.draft(claim.jobPublicId(), index, round, item.question());
                // A form-started question is not saved, so a duplicate on the last round is still shown to a person.
                boolean duplicate = writer.isDuplicate(claim, item);
                DuplicateCheck.report(claim.taskTypeKey(), item, duplicate);
                if (duplicate
                        && (claim.saveQuestions() || canRewrite(budget, round, maxRounds))) {
                    if (!canRewrite(budget, round, maxRounds)) {
                        return fail(claim, index, QuestionGenerationConstants.GENERATION_DUPLICATE_CONTENT,
                                "The same content already exists for this task type.", ItemOutcome.FAILED);
                    }
                    feedback = QuestionGenerationConstants.FEEDBACK_DUPLICATE;
                    continue;
                }
                verdict = null;
                if (item.draft() == null) {
                    break; // the stub generator: nothing to review or render
                }
                advance(claim, index, ItemStage.REVIEWING);
                verdict = itemReviewer.review(item, claim.taskTypeKey(), claim.cefrLevel(), budget, feedback);
                reportReview(claim, index, verdict);
                if (!verdict.isPassed()) {
                    if (canRewrite(budget, round, maxRounds)) {
                        feedback = verdict.fixSuggestion();
                        continue;
                    }
                    LOG.info("Generation item kept for a person after the last refusal: jobId={}, item={}",
                            claim.jobPublicId(), index);
                }
                // Media only after the text went through review, so nothing is spoken or drawn for a draft rewritten anyway.
                advance(claim, index, ItemStage.RENDERING_MEDIA);
                Optional<RenderedMedia> media;
                try {
                    media = mediaRenderer.render(item.draft(), claim.taskTypeKey(), index,
                            claim.jobPublicId().getLeastSignificantBits(), caller, claim.pool());
                } catch (GenerationMediaException mediaFailure) {
                    if (request.isPicture() && QuestionGenerationConstants.GENERATION_IMAGE_FAILED
                            .equals(mediaFailure.getCode()) && images.fallsBackToChart()
                            && canRewrite(budget, round, maxRounds)) {
                        request = images.fallBack(claim.jobPublicId(), index, request, mediaFailure.getUserMessage());
                        feedback = null;
                        continue;
                    }
                    if (!QuestionGenerationConstants.GENERATION_AUDIO_DURATION_OUT_OF_RANGE.equals(mediaFailure.getCode())
                            || !canRewrite(budget, round, maxRounds)) {
                        throw mediaFailure;
                    }
                    feedback = QuestionGenerationConstants.FEEDBACK_AUDIO_LENGTH + " " + mediaFailure.getUserMessage();
                    continue;
                }
                if (media.isPresent()) {
                    stored = media.get();
                    item = item.withMedia(images.marked(media.get()));
                    liveFeed.media(claim.jobPublicId(), index, media.get().audioPromptRef(),
                            media.get().imagePromptRef());
                    boolean canRedraw = canRewrite(budget, round, maxRounds);
                    String imageIssues = images.issues(claim.jobPublicId(), index,
                            () -> advance(claim, index, ItemStage.REVIEWING), caller, item, media.get(), canRedraw);
                    if (imageIssues != null && canRedraw) {
                        stored = null; // the image check already removed the refused image
                        if (request.isPicture() && images.pictureRefused() && images.fallsBackToChart()) {
                            request = images.fallBack(claim.jobPublicId(), index, request, imageIssues);
                            feedback = null;
                        } else {
                            feedback = QuestionGenerationConstants.FEEDBACK_IMAGE + " " + imageIssues;
                        }
                        continue;
                    }
                }
                break;
            }

            // From here the media belongs to the form or to the writer below, which removes it if saving fails.
            stored = null;
            // Asked from the question form: the person decides whether to save it, so the media stays for the form.
            if (!claim.saveQuestions()) {
                itemState.ready(claim, index);
                liveFeed.stage(claim.jobPublicId(), index, ItemStage.READY);
                return ItemOutcome.SAVED;
            }

            // The media is already stored: if the question cannot be created, remove it again.
            ItemOutcome outcome;
            try {
                outcome = writer.save(claim, caller, index, item, verdict);
            } catch (RuntimeException failure) {
                mediaRenderer.discard(item.question().audioPromptRef(), item.question().imagePromptRef(), caller);
                throw failure;
            }
            if (outcome != ItemOutcome.SAVED) {
                mediaRenderer.discard(item.question().audioPromptRef(), item.question().imagePromptRef(), caller);
            }
            return outcome;
        } catch (LlmProviderException failure) {
            return fail(claim, index, failure.getType().code(), null, outcomeFor(failure.getType()));
        } catch (GenerationCallBudgetExceededException failure) {
            return fail(claim, index, QuestionGenerationConstants.GENERATION_CALL_BUDGET_EXCEEDED, null,
                    ItemOutcome.FAILED);
        } catch (GenerationMediaException media) {
            return fail(claim, index, media.getCode(), media.getUserMessage(), ItemOutcome.FAILED);
        } catch (GenerationRequestInvalidException invalid) {
            return fail(claim, index, invalid.getCode(), invalid.getUserMessage(), ItemOutcome.FAILED);
        } catch (DomainException rejected) {
            return fail(claim, index, QuestionGenerationConstants.GENERATION_ITEMBANK_REJECTED,
                    rejected.getMessage(), ItemOutcome.FAILED);
        } catch (RuntimeException unexpected) {
            LOG.warn("Generation item failed: jobId={}, item={}, errorType={}", claim.jobPublicId(), index,
                    unexpected.getClass().getSimpleName());
            return fail(claim, index, QuestionGenerationConstants.GENERATION_INTERNAL_ERROR, null,
                    ItemOutcome.FAILED);
        } finally {
            mediaRenderer.discard(stored, caller);
        }
    }

    /** Another round is tried only while its worst case still fits in the item's allowance. */
    private boolean canRewrite(GenerationCallBudget budget, int round, int maxRounds) {
        return round < maxRounds && budget.remaining() >= properties.getLimits().getMaxCallsPerRound();
    }

    /** A draft broke the JSON shape or the task rules: the writer can fix that when told what was wrong. */
    private static boolean isFixable(String code) {
        return QuestionGenerationConstants.GENERATION_SCHEMA_INVALID.equals(code)
                || QuestionGenerationConstants.GENERATION_VALIDATION_FAILED.equals(code);
    }

    /** The provider was down or answered nothing for a moment; a wrong key or a refused request stays an error. */
    private static boolean isPassing(LlmFailureType type) {
        return type == LlmFailureType.UNAVAILABLE || type == LlmFailureType.EMPTY_RESPONSE;
    }

    /** Tells the live view what the content reviewer said, with the note of every check that failed. */
    private void reportReview(JobClaim claim, int index, ReviewVerdict verdict) {
        Map<String, String> notes = new LinkedHashMap<>();
        if (verdict.checks() != null) {
            verdict.checks().forEach((check, result) -> {
                if (result != null && !result.ok() && result.note() != null) {
                    notes.put(check, result.note());
                }
            });
        }
        liveFeed.review(claim.jobPublicId(), index, GenerationLiveConstants.REVIEW_CONTENT, verdict.isPassed(),
                verdict.fixSuggestion(), notes);
    }

    /** @return false when the lease was lost; the live view only hears of a stage the item really reached */
    private boolean advance(JobClaim claim, int index, ItemStage stage) {
        boolean moved = itemState.advance(claim, index, stage);
        if (moved) {
            liveFeed.stage(claim.jobPublicId(), index, stage);
        }
        return moved;
    }

    private ItemOutcome fail(JobClaim claim, int index, String code, String note, ItemOutcome outcome) {
        itemState.fail(claim, index, code, note);
        return outcome;
    }

    private static ItemOutcome outcomeFor(LlmFailureType type) {
        return switch (type) {
            case AUTH -> ItemOutcome.FAILED_PROVIDER_AUTH;
            case UNAVAILABLE -> ItemOutcome.FAILED_PROVIDER_UNAVAILABLE;
            default -> ItemOutcome.FAILED;
        };
    }
}
