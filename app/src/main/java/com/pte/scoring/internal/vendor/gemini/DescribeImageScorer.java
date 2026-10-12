package com.pte.scoring.internal.vendor.gemini;

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
 * Describe Image: Gemini looks at the picture itself. When the picture cannot
 * be fetched the description is judged against the authored reference text
 * instead, and the score is flagged low confidence with the reason.
 */
@Component
@ConditionalOnProperty(name = "scoring.ai.provider", havingValue = "azure-gemini")
public class DescribeImageScorer extends GeminiSpeakingScorer {

    static final String NO_IMAGE_REFERENCE = "NO_IMAGE_REFERENCE";

    private static final String EXAMINER = "You are a PTE Academic examiner grading a Describe Image response. "
            + "The candidate was shown an image and had to describe it aloud. "
            + "Score Content 0-100: does the description accurately and thoroughly cover the key visual details, "
            + "data, or trends shown in the image? Deduct for missing important elements, factual inaccuracies, "
            + "or vague/generic descriptions that could apply to any image. Do not score pronunciation or "
            + "fluency; they are measured separately.";
    private static final String NO_IMAGE_NOTE = " The image itself is not available to you: judge the description "
            + "against the reference description of the image given below instead.";
    private static final String ASK_WITH_IMAGE = "Look at the attached image, then score Content 0-100, write "
            + "brief feedback (2-3 sentences) on how well the description matches the image, and list the key "
            + "visual details from the image that were NOT mentioned in the description (empty list if none "
            + "missing).";
    private static final String ASK_WITH_REFERENCE = "Score Content 0-100 against the reference description, "
            + "write brief feedback (2-3 sentences) on how well the candidate's description matches it, and list "
            + "the key details of the reference description that were NOT mentioned (empty list if none missing).";
    private static final Map<String, Object> SCHEMA = GeminiScorers.object(
            "content", GeminiScorers.score(), "feedback", GeminiScorers.string(),
            "missed_details", GeminiScorers.strings());

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
            GeminiImage attached = image;
            return new Prepared(transcript -> new GeminiRequest(GeminiScorers.system(EXAMINER),
                    GeminiScorers.quoted("Candidate's description", transcript) + "\n" + ASK_WITH_IMAGE,
                    SCHEMA, List.of(attached)), detail, false);
        }
        String reference = GeminiScorers.requireText(request.referenceAnswerText(),
                ScoringConstants.DESCRIBE_IMAGE_SOURCE_MISSING);
        detail.put("descriptionSource", "REFERENCE_TEXT");
        detail.put("fallbackReason", fallbackReason);
        detail.put("confidence", "LOW");
        return new Prepared(transcript -> new GeminiRequest(GeminiScorers.system(EXAMINER + NO_IMAGE_NOTE),
                GeminiScorers.quoted("Reference description of the image", reference) + "\n"
                        + GeminiScorers.quoted("Candidate's description", transcript) + "\n" + ASK_WITH_REFERENCE,
                SCHEMA, List.of()), detail, true);
    }

    @Override
    String[] detailFields() {
        return new String[] {"missed_details"};
    }

    @Override
    boolean missingProsodyScoresZero() {
        return true;
    }
}
