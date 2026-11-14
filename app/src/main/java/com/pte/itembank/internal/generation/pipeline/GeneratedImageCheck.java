package com.pte.itembank.internal.generation.pipeline;

import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.draft.ItemDraft;
import com.pte.itembank.internal.generation.constant.GenerationLiveConstants;
import com.pte.itembank.internal.generation.live.GenerationLiveFeed;
import com.pte.itembank.internal.generation.media.MediaRenderer;
import com.pte.itembank.internal.generation.media.RenderedMedia;
import com.pte.itembank.internal.generation.review.ChartImageReviewer;
import com.pte.shared.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

/**
 * The image half of one generated Describe Image question: the vision review of a drawn chart or a painted picture,
 * and the switch to a chart when the picture keeps failing. One instance serves one item, so it counts that item's
 * picture failures.
 */
final class GeneratedImageCheck {

    private static final Logger LOG = LoggerFactory.getLogger(GeneratedImageCheck.class);

    private final QuestionGenerationProperties properties;
    private final ChartImageReviewer reviewer;
    private final GenerationLiveFeed liveFeed;
    private final MediaRenderer mediaRenderer;
    private int pictureRefusals;
    private String fallbackReason;

    GeneratedImageCheck(QuestionGenerationProperties properties, ChartImageReviewer reviewer,
                        GenerationLiveFeed liveFeed, MediaRenderer mediaRenderer) {
        this.properties = properties;
        this.reviewer = reviewer;
        this.liveFeed = liveFeed;
        this.mediaRenderer = mediaRenderer;
    }

    /**
     * With {@code itembank.generation.review.vision-check} on, a reviewer looks at the image next to what it should
     * show. A refused image is removed while it can still be redrawn; the last one is kept for a person.
     *
     * @return the reviewer's issues when it refused the image, or null when it passed or was not checked
     */
    String issues(UUID jobPublicId, int index, Runnable reviewing, CurrentUser caller, GeneratedItem item,
                  RenderedMedia media, boolean canRedraw) {
        if (!properties.getReview().isVisionCheck() || media.imagePng() == null) {
            return null;
        }
        ChartImageReviewer.Result look;
        if (item.draft() instanceof ItemDraft.ChartDraft chart) {
            reviewing.run();
            look = reviewer.review(chart, media.imagePng());
        } else if (item.draft() instanceof ItemDraft.PictureDraft picture) {
            reviewing.run();
            look = reviewer.review(picture, media.imagePng());
        } else {
            return null;
        }
        liveFeed.review(jobPublicId, index, GenerationLiveConstants.REVIEW_IMAGE, look.passed(),
                String.join(" ", look.issues()), Map.of());
        if (look.passed()) {
            return null;
        }
        LOG.info("Generation image refused: jobId={}, item={}, redraw={}, issues={}", jobPublicId, index, canRedraw,
                String.join(" | ", look.issues()));
        if (canRedraw) {
            mediaRenderer.discard(null, media.imagePromptRef(), caller);
        } else {
            LOG.info("Generation image kept for a person after the last refusal: jobId={}, item={}", jobPublicId, index);
        }
        return String.join(" ", look.issues());
    }

    /** Counts a refused picture; true once the picture has used up the attempts set on the picture prompt. */
    boolean pictureRefused() {
        pictureRefusals++;
        return pictureRefusals >= mediaRenderer.pictureSettings().maxAttempts();
    }

    /** False when the picture prompt says an item whose picture keeps failing is failed, not made as a chart. */
    boolean fallsBackToChart() {
        return mediaRenderer.pictureSettings().fallbackToChart();
    }

    /** Tells the live view the picture is given up, and returns the same slot asked for as a chart. */
    GenerationRequest fallBack(UUID jobPublicId, int index, GenerationRequest request, String reason) {
        fallbackReason = reason == null ? "" : reason;
        LOG.info("Generation picture replaced by a chart: jobId={}, item={}", jobPublicId, index);
        liveFeed.review(jobPublicId, index, GenerationLiveConstants.REVIEW_IMAGE, false,
                QuestionGenerationConstants.IMAGE_FALLBACK_NOTE + fallbackReason, Map.of());
        return request.asChart();
    }

    /** The chart's media, marked with why it replaced a picture; unchanged when no picture was given up. */
    RenderedMedia marked(RenderedMedia media) {
        if (fallbackReason == null) {
            return media;
        }
        Map<String, Object> meta = new java.util.LinkedHashMap<>(media.meta());
        meta.put("imageFallback", Map.of("from", "PICTURE", "reason", fallbackReason));
        return new RenderedMedia(media.audioPromptRef(), media.imagePromptRef(), meta, media.imagePng());
    }
}
