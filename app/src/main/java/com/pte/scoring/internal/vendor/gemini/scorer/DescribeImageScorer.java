package com.pte.scoring.internal.vendor.gemini.scorer;

import com.pte.scoring.internal.vendor.gemini.GeminiImage;
import com.pte.scoring.internal.vendor.gemini.GeminiRestClient;
import com.pte.scoring.internal.vendor.gemini.PromptImageLoader;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import com.pte.scoring.internal.vendor.azure.AzureSpeechAssessor;
import com.pte.scoring.internal.vendor.speech.SpeechAudioLoader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Describe Image (Score Guide pp.18-20): Gemini looks at the picture itself and scores CONTENT 0-6. When the picture
 * cannot be fetched the description is judged against the authored reference text instead, and the score is
 * flagged low confidence with the reason.
 */
@Component
@ConditionalOnProperty(name = "scoring.ai.provider", havingValue = "azure-gemini")
public class DescribeImageScorer extends GeminiSpeakingScorer {

    static final String NO_IMAGE_REFERENCE = "NO_IMAGE_REFERENCE";
    static final String MISSED_DETAILS = "missed_details";

    private static final String EXAMINER = "You are a PTE Academic examiner grading a Describe Image response. "
            + "The candidate was shown an image and described it aloud; you receive the transcript of what they "
            + "said. Score CONTENT: how accurately and fully the description covers the main features of the "
            + "image, the relationships between them and their meaning. Deduct for missing important elements, "
            + "factual inaccuracies, or vague descriptions that could apply to any image.";
    private static final String NO_IMAGE_NOTE = " The image itself is not available to you: judge the description "
            + "against the reference description of the image given below instead.";
    private static final String ASK_WITH_IMAGE = "Look at the attached image, then score CONTENT on its scale as "
            + "described above, write brief feedback (2-3 sentences) on how well the description matches the image, "
            + "and list in missed_details the key visual details from the image that were NOT mentioned in the "
            + "description (empty list if none missing).";
    private static final String ASK_WITH_REFERENCE = "Score CONTENT on its scale as described above against the "
            + "reference description, write brief feedback (2-3 sentences) on how well the candidate's description "
            + "matches it, and list in missed_details the key details of the reference description that were NOT "
            + "mentioned (empty list if none missing).";

    private final PromptImageLoader images;

    public DescribeImageScorer(SpeechAudioLoader audio, AzureSpeechAssessor assessor, GeminiRestClient gemini,
                               JsonMapper json, PromptImageLoader images) {
        super(audio, assessor, gemini, json);
        this.images = images;
    }

    @Override
    public Set<String> taskTypes() {
        return Set.of(ScoringConstants.TASK_TYPE_DESCRIBE_IMAGE);
    }

    @Override
    Prepared prepare(AiScoringRequest request) {
        GeminiImage image = null;
        String fallbackReason = NO_IMAGE_REFERENCE;
        if (request.imagePromptRef() != null) {
            try {
                image = images.load(request.imagePromptRef(), request.tenantId());
            } catch (AiProviderException ex) {
                fallbackReason = ex.getMessage();
            }
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        if (image != null) {
            detail.put("descriptionSource", "IMAGE");
            return new Prepared(EXAMINER, transcript -> GeminiScorers.quoted("Candidate's description", transcript)
                    + "\n" + ASK_WITH_IMAGE, List.of(image), detail, false);
        }
        String reference = GeminiScorers.requireText(request.referenceAnswerText(),
                ScoringConstants.DESCRIBE_IMAGE_SOURCE_MISSING);
        detail.put("descriptionSource", "REFERENCE_TEXT");
        detail.put("fallbackReason", fallbackReason);
        detail.put("confidence", "LOW");
        return new Prepared(EXAMINER + NO_IMAGE_NOTE,
                transcript -> GeminiScorers.quoted("Reference description of the image", reference) + "\n"
                        + GeminiScorers.quoted("Candidate's description", transcript) + "\n" + ASK_WITH_REFERENCE,
                List.of(), detail, true);
    }

    @Override
    Map<String, Object> detailSchema() {
        return Map.of(MISSED_DETAILS, GeminiScorers.strings());
    }
}
