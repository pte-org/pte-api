package com.pte.practice.internal.service;

import com.pte.attempt.ResponseConfidence;
import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.exception.PracticeSessionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.Set;

/**
 * Shared practice answer boundary for Phase 05/06. Blank payloads represent a
 * skip/empty draft and may omit confidence; answer-bearing payloads may not.
 */
@Service
public class PracticeAnswerValidationService {

    private final ObjectMapper objectMapper;

    /** Kept for focused unit tests that exercise the validation boundary alone. */
    public PracticeAnswerValidationService() {
        this(JsonMapper.builder().build());
    }

    @Autowired
    public PracticeAnswerValidationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void requireConfidenceForAnsweredPayload(String payload, ResponseConfidence confidence) {
        if (payload != null && !payload.isBlank() && confidence == null) {
            throw new PracticeSessionException(HttpStatus.UNPROCESSABLE_ENTITY,
                    PracticeConstants.PRACTICE_CONFIDENCE_REQUIRED,
                    PracticeConstants.PRACTICE_CONFIDENCE_REQUIRED_MESSAGE);
        }
    }

    /**
     * Validates only the public answer shape for the allowlisted renderer. It
     * intentionally never receives or evaluates a correct answer.
     */
    public void validate(String rendererKey, String payload, ResponseConfidence confidence) {
        validate(rendererKey, 1, payload, confidence);
    }

    public void validate(String rendererKey, Integer answerSchemaVersion, String payload,
            ResponseConfidence confidence) {
        validatePayload(rendererKey, answerSchemaVersion, payload, confidence, true);
    }

    /** Validates a non-empty draft while allowing confidence to be selected later. */
    public void validateDraft(String rendererKey, Integer answerSchemaVersion, String payload,
            ResponseConfidence confidence) {
        validatePayload(rendererKey, answerSchemaVersion, payload, confidence, false);
    }

    private void validatePayload(String rendererKey, Integer answerSchemaVersion, String payload,
            ResponseConfidence confidence, boolean confidenceRequired) {
        if (confidenceRequired) {
            requireConfidenceForAnsweredPayload(payload, confidence);
        }
        if (payload == null || payload.isBlank()) {
            throw invalidPayload(PracticeConstants.PRACTICE_ANSWER_PAYLOAD_REQUIRED,
                    PracticeConstants.PRACTICE_ANSWER_PAYLOAD_REQUIRED_MESSAGE);
        }
        if (payload.length() > PracticeConstants.MAX_ANSWER_PAYLOAD_LENGTH
                || answerSchemaVersion == null || answerSchemaVersion != 1) {
            throw invalidPayload(PracticeConstants.PRACTICE_ANSWER_INVALID,
                    PracticeConstants.PRACTICE_ANSWER_INVALID_MESSAGE);
        }
        if (!PracticeConstants.PRACTICE_CLIENT_SUPPORTED_RENDERER_KEYS.contains(rendererKey)) {
            throw new PracticeSessionException(HttpStatus.UNPROCESSABLE_ENTITY,
                    PracticeConstants.PRACTICE_UNSUPPORTED_RUNTIME,
                    PracticeConstants.PRACTICE_UNSUPPORTED_RUNTIME_MESSAGE);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception ex) {
            throw invalidPayload(PracticeConstants.PRACTICE_ANSWER_INVALID,
                    PracticeConstants.PRACTICE_ANSWER_INVALID_MESSAGE);
        }
        if (root == null || !root.isObject()) {
            throw invalidPayload(PracticeConstants.PRACTICE_ANSWER_INVALID,
                    PracticeConstants.PRACTICE_ANSWER_INVALID_MESSAGE);
        }

        if (isSingleChoice(rendererKey)) {
            requireOnlyFields(root, "selectedOption");
            requireNonBlankText(root, "selectedOption");
        } else if (isMultipleChoice(rendererKey)) {
            requireOnlyFields(root, "selectedOptions");
            requireNonBlankTextArray(root, "selectedOptions");
        } else if ("RE_ORDER_PARAGRAPHS_V1".equals(rendererKey)) {
            requireOnlyFields(root, "order");
            requireNonBlankTextArray(root, "order");
        } else if ("FILL_IN_THE_BLANKS_DRAG_AND_DROP_V1".equals(rendererKey)) {
            requireOnlyFields(root, "placements");
            requireNonEmptyObject(root, "placements");
        } else if ("FILL_IN_THE_BLANKS_DROPDOWN_V1".equals(rendererKey)) {
            requireOnlyFields(root, "selections");
            requireNonEmptyObject(root, "selections");
        } else if (isTextResponse(rendererKey)) {
            requireOnlyFields(root, "text");
            requireNonBlankText(root, "text");
        } else {
            throw new PracticeSessionException(HttpStatus.UNPROCESSABLE_ENTITY,
                    PracticeConstants.PRACTICE_UNSUPPORTED_RUNTIME,
                    PracticeConstants.PRACTICE_UNSUPPORTED_RUNTIME_MESSAGE);
        }
    }

    private boolean isSingleChoice(String rendererKey) {
        return "MC_READING_SINGLE_V1".equals(rendererKey);
    }

    private boolean isMultipleChoice(String rendererKey) {
        return "MC_READING_MULTIPLE_V1".equals(rendererKey);
    }

    private boolean isTextResponse(String rendererKey) {
        return "SUMMARIZE_WRITTEN_TEXT_V1".equals(rendererKey)
                || "WRITE_ESSAY_V1".equals(rendererKey);
    }

    private void requireNonBlankText(JsonNode root, String field) {
        JsonNode value = root.path(field);
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > 16_384) {
            throw invalidPayload(PracticeConstants.PRACTICE_ANSWER_INVALID,
                    PracticeConstants.PRACTICE_ANSWER_INVALID_MESSAGE);
        }
    }

    private void requireNonBlankTextArray(JsonNode root, String field) {
        JsonNode value = root.path(field);
        if (!value.isArray() || value.isEmpty() || value.size() > 100) {
            throw invalidPayload(PracticeConstants.PRACTICE_ANSWER_INVALID,
                    PracticeConstants.PRACTICE_ANSWER_INVALID_MESSAGE);
        }
        for (JsonNode entry : value) {
            if (!entry.isTextual() || entry.asText().isBlank()) {
                throw invalidPayload(PracticeConstants.PRACTICE_ANSWER_INVALID,
                        PracticeConstants.PRACTICE_ANSWER_INVALID_MESSAGE);
            }
        }
    }

    private void requireNonEmptyObject(JsonNode root, String field) {
        JsonNode value = root.path(field);
        if (!value.isObject() || value.isEmpty() || value.size() > 100) {
            throw invalidPayload(PracticeConstants.PRACTICE_ANSWER_INVALID,
                    PracticeConstants.PRACTICE_ANSWER_INVALID_MESSAGE);
        }
        value.properties().forEach(entry -> {
            JsonNode fieldValue = entry.getValue();
            if (entry.getKey().isBlank() || !fieldValue.isTextual() || fieldValue.asText().isBlank()
                    || fieldValue.asText().length() > 128) {
                throw invalidPayload(PracticeConstants.PRACTICE_ANSWER_INVALID,
                        PracticeConstants.PRACTICE_ANSWER_INVALID_MESSAGE);
            }
        });
    }

    private void requireOnlyFields(JsonNode root, String expectedField) {
        Set<String> fields = new HashSet<>(root.propertyNames());
        if (fields.size() != 1 || !fields.contains(expectedField)) {
            throw invalidPayload(PracticeConstants.PRACTICE_ANSWER_INVALID,
                    PracticeConstants.PRACTICE_ANSWER_INVALID_MESSAGE);
        }
    }

    private PracticeSessionException invalidPayload(String code, String message) {
        return new PracticeSessionException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
