package com.pte.itembank.internal.generation.draft.image;

import com.pte.itembank.dto.request.CreateQuestionRequest;
import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.draft.ItemDraft;
import com.pte.itembank.internal.generation.exception.GenerationRequestInvalidException;
import com.pte.itembank.internal.generation.validate.ValidationIssue;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * The picture variant of Describe Image: reading the writer's JSON, its structural rules, and the question it becomes.
 * The picture itself is painted from {@code imagePrompt}; the key points are what a candidate should describe.
 */
public final class PictureDrafts {

    private static final int MIN_KEY_POINTS = 3;
    private static final int MAX_KEY_POINTS = 6;
    private static final int MIN_IMAGE_PROMPT_CHARS = 40;
    private static final String TASK_TYPE = "DESCRIBE_IMAGE";

    private PictureDrafts() {
    }

    public static ItemDraft.PictureDraft parse(JsonNode root) {
        List<String> keyPoints = new ArrayList<>();
        JsonNode keyNode = root.get("keyPoints");
        if (keyNode != null && keyNode.isArray()) {
            keyNode.forEach(point -> {
                if (point.isTextual() && !point.asText().isBlank()) {
                    keyPoints.add(point.asText().strip());
                }
            });
        }
        return new ItemDraft.PictureDraft(require(root, "topic"), require(root, "title"), require(root, "imagePrompt"),
                keyPoints, require(root, "referenceDescription"));
    }

    public static List<ValidationIssue> validate(ItemDraft.PictureDraft draft) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (draft.imagePrompt().length() < MIN_IMAGE_PROMPT_CHARS) {
            issues.add(new ValidationIssue("imagePrompt",
                    QuestionGenerationConstants.PICTURE_PROMPT_TOO_SHORT.formatted(MIN_IMAGE_PROMPT_CHARS)));
        }
        int points = draft.keyPoints().size();
        if (points < MIN_KEY_POINTS || points > MAX_KEY_POINTS) {
            issues.add(new ValidationIssue("keyPoints",
                    QuestionGenerationConstants.PICTURE_KEY_POINTS_OUT_OF_RANGE.formatted(MIN_KEY_POINTS, MAX_KEY_POINTS,
                            points)));
        }
        return issues;
    }

    /** The reference a scorer compares a spoken description with: the model description, then the key points. */
    public static CreateQuestionRequest toQuestion(ItemDraft.PictureDraft draft, CefrLevel cefr, QuestionPool pool) {
        String reference = draft.referenceDescription() + "\n" + String.join("\n", draft.keyPoints());
        return new CreateQuestionRequest(TASK_TYPE, TASK_TYPE, null, null, null, reference.strip(), null, null, null,
                null, pool.name(), null, null, cefr.name());
    }

    private static String require(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new GenerationRequestInvalidException(QuestionGenerationConstants.GENERATION_SCHEMA_INVALID,
                    QuestionGenerationConstants.DRAFT_FIELD_MISSING.formatted(field));
        }
        return value.asText().strip();
    }
}
