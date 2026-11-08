package com.pte.itembank.internal.generation.pipeline;

import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
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
import com.pte.itembank.internal.generation.draft.ItemDraft;
import com.pte.itembank.internal.generation.review.ChartImageReviewer;
import com.pte.itembank.internal.generation.media.RenderedMedia;
import java.util.Optional;
import com.pte.shared.exception.DomainException;
import com.pte.shared.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public GenerationPipeline(List<ItemGenerator> generators, GeneratedQuestionWriter writer,
                              GenerationItemStateService itemState, QuestionGenerationProperties properties,
                              AvoidTopicsProvider avoidTopicsProvider, LlmItemReviewer itemReviewer,
                              MediaRenderer mediaRenderer, GenerationLiveFeed liveFeed,
                              ChartImageReviewer imageReviewer) {
        this.imageReviewer = imageReviewer;
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
        try {
            GenerationCallBudget budget = new GenerationCallBudget(properties.getLimits().getMaxCallsPerItem());

            // Generate
            ItemGenerator generator = generators.stream()
                    .filter(candidate -> candidate.supports(claim.taskTypeKey())).findFirst().orElse(null);
            if (generator == null) {
                return fail(claim, index, QuestionGenerationConstants.GENERATION_NO_GENERATOR, null,
                        ItemOutcome.FAILED);
            }

            String avoidTopics = avoidTopicsProvider.getAvoidTopicsForTaskType(claim.taskTypeKey());
            GenerationRequest request = new GenerationRequest(claim.jobPublicId(), index, claim.taskTypeKey(),
                    claim.cefrLevel(), claim.pool(), claim.topicHint(), avoidTopics);
            GeneratedItem item = generator.generate(request, budget);

            // Duplicate check (before review)
            if (writer.isDuplicate(claim, item)) {
                return fail(claim, index, QuestionGenerationConstants.GENERATION_DUPLICATE_CONTENT,
                        "The same content already exists for this task type.", ItemOutcome.FAILED);
            }

            // Review (only if draft is available; stub generator may have null draft)
            ReviewVerdict verdict = null;
            if (item.draft() != null) {
                advance(claim, index, ItemStage.REVIEWING);
                verdict = itemReviewer.review(item.draft(), claim.taskTypeKey(),
                        claim.cefrLevel(), budget, null);

                if (!verdict.isPassed()) {
                    // One retry allowed per item (shared with validator)
                    if (budget.remaining() > 0) {
                        GeneratedItem retried = generator.generate(request.withFeedback(verdict.fixSuggestion()), budget);
                        if (!writer.isDuplicate(claim, retried)) {
                            ReviewVerdict retriedVerdict = itemReviewer.review(retried.draft(),
                                    claim.taskTypeKey(), claim.cefrLevel(), budget, verdict.fixSuggestion());
                            if (retriedVerdict.isPassed()) {
                                item = retried;
                                verdict = retriedVerdict;
                            } else {
                                return fail(claim, index, QuestionGenerationConstants.GENERATION_REVIEW_FAILED,
                                        retriedVerdict.fixSuggestion(), ItemOutcome.FAILED);
                            }
                        } else {
                            return fail(claim, index, QuestionGenerationConstants.GENERATION_DUPLICATE_CONTENT,
                                    "Duplicate after retry.", ItemOutcome.FAILED);
                        }
                    } else {
                        return fail(claim, index, QuestionGenerationConstants.GENERATION_REVIEW_FAILED,
                                verdict.fixSuggestion(), ItemOutcome.FAILED);
                    }
                }
            }

            // Media only after the text passed review, so nothing is spoken or drawn for a rejected draft.
            if (item.draft() != null) {
                advance(claim, index, ItemStage.RENDERING_MEDIA);
                Optional<RenderedMedia> media = mediaRenderer.render(item.draft(), claim.taskTypeKey(), index,
                        claim.jobPublicId().getLeastSignificantBits(), caller);
                if (media.isPresent()) {
                    item = item.withMedia(media.get());
                    liveFeed.media(claim.jobPublicId(), index, media.get().audioPromptRef(),
                            media.get().imagePromptRef());
                    ItemOutcome refused = reviewImage(claim, caller, index, item, media.get());
                    if (refused != null) {
                        return refused;
                    }
                }
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
        }
    }

    /**
     * With {@code itembank.generation.review.vision-check} on, a reviewer looks at a drawn chart next to its data.
     * A chart is drawn by code from the data, so drawing it again gives the same picture: a refused image fails the
     * item, with the reviewer's issues as its note, and the stored image is removed.
     *
     * @return the outcome of a refused image, or null when the item goes on
     */
    private ItemOutcome reviewImage(JobClaim claim, CurrentUser caller, int index, GeneratedItem item,
                                    RenderedMedia media) {
        if (!properties.getReview().isVisionCheck() || media.imagePng() == null
                || !(item.draft() instanceof ItemDraft.ChartDraft chart)) {
            return null;
        }
        advance(claim, index, ItemStage.REVIEWING);
        ChartImageReviewer.Result look = imageReviewer.review(chart, media.imagePng());
        if (look.passed()) {
            return null;
        }
        mediaRenderer.discard(null, media.imagePromptRef(), caller);
        String note = String.join(" ", look.issues());
        return fail(claim, index, QuestionGenerationConstants.GENERATION_IMAGE_REVIEW_FAILED,
                note.length() > QuestionGenerationConstants.MAX_FAILURE_NOTE_LENGTH
                        ? note.substring(0, QuestionGenerationConstants.MAX_FAILURE_NOTE_LENGTH) : note,
                ItemOutcome.FAILED);
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
