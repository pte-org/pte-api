package com.pte.rubric.internal.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.rubric.internal.constant.RubricConstants;
import com.pte.rubric.internal.exception.RubricValidationException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Validates the minimum safe shape before a rubric can be submitted or published. */
@Component
public class RubricDocumentValidator {

    private static final Set<String> ALLOWED_LEVELS = Set.of(
            "NOT_ASSESSABLE", "NEEDS_SUPPORT", "DEVELOPING", "EFFECTIVE", "STRONG");
    private static final Set<String> ALLOWED_COVERAGE = Set.of("FULL", "LIMITED", "NOT_ASSESSABLE");

    private static final Map<String, Set<String>> TASK_CRITERIA = Map.ofEntries(
            Map.entry("PERSONAL_INTRODUCTION", Set.of()),
            Map.entry("READ_ALOUD", Set.of("CONTENT_COVERAGE", "PRONUNCIATION", "FLUENCY", "PROSODY")),
            Map.entry("REPEAT_SENTENCE", Set.of("CONTENT_COVERAGE", "PRONUNCIATION", "FLUENCY", "PROSODY")),
            Map.entry("DESCRIBE_IMAGE", Set.of("TASK_FULFILMENT", "CONTENT_COVERAGE", "LANGUAGE_CONTROL", "PRONUNCIATION", "FLUENCY")),
            Map.entry("RE_TELL_LECTURE", Set.of("CONTENT_COVERAGE", "FAITHFULNESS", "LANGUAGE_CONTROL", "PRONUNCIATION", "FLUENCY")),
            Map.entry("ANSWER_SHORT_QUESTION", Set.of("CONTENT_COVERAGE", "PRONUNCIATION", "FLUENCY")),
            Map.entry("RESPOND_TO_A_SITUATION", Set.of("TASK_FULFILMENT", "REGISTER_APPROPRIATENESS", "LANGUAGE_CONTROL", "PRONUNCIATION", "FLUENCY")),
            Map.entry("SUMMARIZE_GROUP_DISCUSSION", Set.of("TASK_FULFILMENT", "CONTENT_COVERAGE", "REGISTER_APPROPRIATENESS", "LANGUAGE_CONTROL", "PRONUNCIATION", "FLUENCY")),
            Map.entry("SUMMARIZE_WRITTEN_TEXT", Set.of("SOURCE_COVERAGE", "FAITHFULNESS", "FORM", "GRAMMAR", "VOCABULARY")),
            Map.entry("WRITE_ESSAY", Set.of("TASK_FULFILMENT", "DEVELOPMENT_SUPPORT", "ORGANIZATION_COHERENCE", "GRAMMAR", "VOCABULARY", "SPELLING", "FORM")),
            Map.entry("MC_READING_SINGLE", Set.of("ANSWER_MATCH")),
            Map.entry("MC_READING_MULTIPLE", Set.of("ANSWER_SET")),
            Map.entry("RE_ORDER_PARAGRAPHS", Set.of("SEQUENCE_RELATION")),
            Map.entry("FILL_IN_THE_BLANKS_DRAG_AND_DROP", Set.of("BLANK_MATCH")),
            Map.entry("FILL_IN_THE_BLANKS_DROPDOWN", Set.of("BLANK_MATCH")),
            Map.entry("SUMMARIZE_SPOKEN_TEXT", Set.of("SOURCE_COVERAGE", "FAITHFULNESS", "ORGANIZATION_COHERENCE", "GRAMMAR", "VOCABULARY", "SPELLING", "FORM")),
            Map.entry("MC_LISTENING_SINGLE", Set.of("ANSWER_MATCH")),
            Map.entry("MC_LISTENING_MULTIPLE", Set.of("ANSWER_SET")),
            Map.entry("FILL_IN_THE_BLANKS_TYPE_IN", Set.of("TOKEN_MATCH")),
            Map.entry("HIGHLIGHT_CORRECT_SUMMARY", Set.of("SUMMARY_MATCH")),
            Map.entry("SELECT_MISSING_WORD", Set.of("CONTEXT_MATCH")),
            Map.entry("HIGHLIGHT_INCORRECT_WORDS", Set.of("TOKEN_MATCH")),
            Map.entry("WRITE_FROM_DICTATION", Set.of("TOKEN_MATCH")));

    /** Server-owned task evidence contract; the editor cannot weaken these requirements. */
    private static final Map<String, Set<String>> TASK_REQUIRED_EVIDENCE = Map.ofEntries(
            Map.entry("PERSONAL_INTRODUCTION", Set.of("PROMPT")),
            Map.entry("READ_ALOUD", Set.of("PROMPT_TEXT", "RESPONSE_AUDIO")),
            Map.entry("REPEAT_SENTENCE", Set.of("SOURCE_AUDIO", "RESPONSE_AUDIO", "RESPONSE_TRANSCRIPT")),
            Map.entry("DESCRIBE_IMAGE", Set.of("IMAGE", "IMAGE_ANNOTATIONS", "RESPONSE_TRANSCRIPT", "RESPONSE_AUDIO")),
            Map.entry("RE_TELL_LECTURE", Set.of("LECTURE_SOURCE", "RESPONSE_TRANSCRIPT", "RESPONSE_AUDIO")),
            Map.entry("ANSWER_SHORT_QUESTION", Set.of("PROMPT_SOURCE", "ACCEPTED_VARIANTS", "RESPONSE_AUDIO", "RESPONSE_TRANSCRIPT")),
            Map.entry("RESPOND_TO_A_SITUATION", Set.of("SITUATION_REQUIREMENTS", "RESPONSE_TRANSCRIPT", "RESPONSE_AUDIO")),
            Map.entry("SUMMARIZE_GROUP_DISCUSSION", Set.of("DISCUSSION_SOURCE", "SPEAKER_EVIDENCE", "RESPONSE_TRANSCRIPT", "RESPONSE_AUDIO")),
            Map.entry("SUMMARIZE_WRITTEN_TEXT", Set.of("PASSAGE", "RESPONSE_TEXT", "FORM_RESULT")),
            Map.entry("WRITE_ESSAY", Set.of("PROMPT", "RESPONSE_TEXT", "FORM_RESULT")),
            Map.entry("MC_READING_SINGLE", Set.of("PASSAGE", "OPTION_SET", "ANSWER_KEY", "DETERMINISTIC_RESULT")),
            Map.entry("MC_READING_MULTIPLE", Set.of("PASSAGE", "OPTION_SET", "ANSWER_KEY", "DETERMINISTIC_RESULT")),
            Map.entry("RE_ORDER_PARAGRAPHS", Set.of("PARAGRAPH_BLOCKS", "ANSWER_ORDER", "DETERMINISTIC_RESULT")),
            Map.entry("FILL_IN_THE_BLANKS_DRAG_AND_DROP", Set.of("PASSAGE", "BLANKS", "WORD_BANK", "ANSWER_KEY", "DETERMINISTIC_RESULT")),
            Map.entry("FILL_IN_THE_BLANKS_DROPDOWN", Set.of("PASSAGE", "BLANK_GROUPS", "OPTION_SET", "ANSWER_KEY", "DETERMINISTIC_RESULT")),
            Map.entry("SUMMARIZE_SPOKEN_TEXT", Set.of("AUDIO", "LECTURE_TRANSCRIPT", "RESPONSE_TEXT", "FORM_RESULT")),
            Map.entry("MC_LISTENING_SINGLE", Set.of("AUDIO", "TRANSCRIPT_OR_SOURCE", "OPTION_SET", "ANSWER_KEY", "DETERMINISTIC_RESULT")),
            Map.entry("MC_LISTENING_MULTIPLE", Set.of("AUDIO", "TRANSCRIPT_OR_SOURCE", "OPTION_SET", "ANSWER_KEY", "DETERMINISTIC_RESULT")),
            Map.entry("FILL_IN_THE_BLANKS_TYPE_IN", Set.of("AUDIO", "PASSAGE", "BLANK_IDS", "NORMALIZATION_POLICY", "ANSWER_KEY")),
            Map.entry("HIGHLIGHT_CORRECT_SUMMARY", Set.of("AUDIO", "SOURCE", "SUMMARY_OPTIONS", "ANSWER_KEY")),
            Map.entry("SELECT_MISSING_WORD", Set.of("AUDIO", "SOURCE", "CANDIDATE_ENDINGS", "ANSWER_KEY")),
            Map.entry("HIGHLIGHT_INCORRECT_WORDS", Set.of("AUDIO", "TRANSCRIPT_TOKENS", "TOKEN_IDS", "ANSWER_KEY")),
            Map.entry("WRITE_FROM_DICTATION", Set.of("AUDIO", "SOURCE_TRANSCRIPT", "NORMALIZATION_POLICY", "ANSWER_KEY")));

    private final ObjectMapper objectMapper;

    public RubricDocumentValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode parseAndValidate(String documentJson) {
        if (documentJson == null || documentJson.isBlank()
                || documentJson.length() > RubricConstants.MAX_DOCUMENT_LENGTH) {
            throw new RubricValidationException(RubricConstants.RUBRIC_INVALID_DOCUMENT);
        }
        try {
            JsonNode root = objectMapper.readTree(documentJson);
            JsonNode criteria = root == null ? null : root.get("criteria");
            if (criteria == null || !criteria.isArray()) {
                throw new RubricValidationException(RubricConstants.RUBRIC_INVALID_CRITERION);
            }
            Set<String> criterionIds = new HashSet<>();
            for (JsonNode criterion : criteria) {
                String criterionId = text(criterion, "criterionId");
                if (criterionId == null || !criterionIds.add(criterionId)) {
                    throw new RubricValidationException(RubricConstants.RUBRIC_INVALID_CRITERION);
                }
                JsonNode levels = criterion.get("levels");
                if (levels == null || !levels.isArray() || levels.isEmpty()) {
                    throw new RubricValidationException(RubricConstants.RUBRIC_INVALID_LEVEL);
                }
                for (JsonNode level : levels) {
                    String levelKey = level.isTextual() ? level.asText() : text(level, "key");
                    if (levelKey == null || !ALLOWED_LEVELS.contains(levelKey)) {
                        throw new RubricValidationException(RubricConstants.RUBRIC_INVALID_LEVEL);
                    }
                }
            }
            return root;
        } catch (RubricValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RubricValidationException(RubricConstants.RUBRIC_INVALID_DOCUMENT);
        }
    }

    /** Applies the full publication readiness contract after the basic JSON shape check. */
    public void validatePublicationReadiness(String documentJson, String taskTypeKey) {
        validatePublicationReadiness(documentJson, taskTypeKey, true);
    }

    public void validatePublicationReadiness(String documentJson, String taskTypeKey, boolean requireApprovedAnchors) {
        JsonNode root = parseAndValidate(documentJson);
        String normalizedTask = taskTypeKey == null ? "" : taskTypeKey.trim().toUpperCase(java.util.Locale.ROOT);
        Set<String> allowedCriteria = TASK_CRITERIA.get(normalizedTask);
        if (allowedCriteria == null || !normalizedTask.equals(text(root, "taskTypeKey"))) {
            throw new RubricValidationException(RubricConstants.RUBRIC_INVALID_TASK_TYPE);
        }
        if (!"1.0".equals(text(root, "schemaVersion"))) {
            throw new RubricValidationException(RubricConstants.RUBRIC_INVALID_DOCUMENT);
        }
        if (root.has("score") || root.has("numericScore") || root.has("officialScore")) {
            throw new RubricValidationException(RubricConstants.RUBRIC_INVALID_DOCUMENT);
        }
        JsonNode runtime = root.get("runtime");
        if (runtime == null || !runtime.isObject() || !matchesRuntimeContract(runtime, normalizedTask)) {
            throw new RubricValidationException(RubricConstants.RUBRIC_RUNTIME_INCOMPATIBLE);
        }
        JsonNode criteria = root.get("criteria");
        Set<String> actualCriteria = new HashSet<>();
        Set<Integer> criterionOrders = new HashSet<>();
        Set<String> declaredEvidenceTypes = new HashSet<>();
        for (JsonNode criterion : criteria) {
            String id = text(criterion, "criterionId");
            if (id == null || !allowedCriteria.contains(id) || !actualCriteria.add(id)
                    || !criterion.has("order") || !criterion.path("order").canConvertToInt()
                    || !criterionOrders.add(criterion.path("order").asInt())
                    || criterion.path("order").asInt() < 1 || !criterion.has("required")) {
                throw new RubricValidationException(RubricConstants.RUBRIC_INVALID_CRITERION);
            }
            JsonNode allowedLevels = criterion.get("allowedLevels");
            JsonNode levels = criterion.get("levels");
            JsonNode evidencePolicy = criterion.get("evidencePolicy");
            if (allowedLevels == null || !allowedLevels.isArray() || allowedLevels.isEmpty()
                    || levels == null || !levels.isArray() || levels.isEmpty()
                    || evidencePolicy == null || !evidencePolicy.isObject()
                    || !evidencePolicy.has("requiredTypes") || !evidencePolicy.get("requiredTypes").isArray()
                    || !evidencePolicy.has("allowNotAssessable") || !evidencePolicy.has("minimumCoverage")) {
                throw new RubricValidationException(RubricConstants.RUBRIC_INVALID_CRITERION);
            }
            Set<String> declaredLevels = new HashSet<>();
            for (JsonNode level : allowedLevels) {
                String key = level.isTextual() ? level.asText() : text(level, "key");
                if (key == null || !ALLOWED_LEVELS.contains(key) || !declaredLevels.add(key)) {
                    throw new RubricValidationException(RubricConstants.RUBRIC_INVALID_LEVEL);
                }
            }
            Set<String> levelDefinitions = new HashSet<>();
            for (JsonNode level : levels) {
                String key = level.isTextual() ? level.asText() : text(level, "key");
                if (key == null || !declaredLevels.contains(key) || !levelDefinitions.add(key)) {
                    throw new RubricValidationException(RubricConstants.RUBRIC_INVALID_LEVEL);
                }
                if (level.isObject() && (text(level, "labelVi") == null || text(level, "labelEn") == null
                        || text(level, "descriptor") == null)) {
                    throw new RubricValidationException(RubricConstants.RUBRIC_INVALID_LEVEL);
                }
            }
            if (!declaredLevels.equals(levelDefinitions)) {
                throw new RubricValidationException(RubricConstants.RUBRIC_INVALID_LEVEL);
            }
            for (JsonNode requiredType : evidencePolicy.get("requiredTypes")) {
                if (!requiredType.isTextual() || requiredType.asText().isBlank()) {
                    throw new RubricValidationException(RubricConstants.RUBRIC_INVALID_CRITERION);
                }
                declaredEvidenceTypes.add(requiredType.asText().trim().toUpperCase(java.util.Locale.ROOT));
            }
            if (!evidencePolicy.path("minimumCoverage").isTextual()
                    || !ALLOWED_COVERAGE.contains(evidencePolicy.path("minimumCoverage").asText().trim().toUpperCase(java.util.Locale.ROOT))
                    || !evidencePolicy.path("allowNotAssessable").isBoolean()
                    || (evidencePolicy.path("allowNotAssessable").asBoolean()
                            && !declaredLevels.contains("NOT_ASSESSABLE"))
                    || (!evidencePolicy.path("allowNotAssessable").asBoolean()
                            && declaredLevels.contains("NOT_ASSESSABLE"))) {
                throw new RubricValidationException(RubricConstants.RUBRIC_INVALID_CRITERION);
            }
        }
        if (!actualCriteria.equals(allowedCriteria)) {
            throw new RubricValidationException(RubricConstants.RUBRIC_INVALID_CRITERION);
        }
        Set<String> requiredEvidence = TASK_REQUIRED_EVIDENCE.get(normalizedTask);
        if (!allowedCriteria.isEmpty() && requiredEvidence != null && !declaredEvidenceTypes.containsAll(requiredEvidence)) {
            throw new RubricValidationException(RubricConstants.RUBRIC_INVALID_CRITERION);
        }
        JsonNode policies = root.get("policies");
        JsonNode references = root.get("references");
        JsonNode anchors = root.get("anchors");
        JsonNode provenance = root.get("provenance");
        boolean coachingOnly = allowedCriteria.isEmpty();
        if (policies == null || !policies.isObject()
                || text(policies, "emptyResponse") == null || text(policies, "invalidResponse") == null
                || text(policies, "offTopicResponse") == null || text(policies, "unsupportedNumericScore") == null
                || text(policies, "officialScoreClaim") == null
                || references == null || !references.isArray() || references.isEmpty()
                || anchors == null || !anchors.isArray() || (!coachingOnly && anchors.isEmpty()) || provenance == null || !provenance.isObject()
                || text(provenance, "sourceDocument") == null || text(provenance, "sourceRevision") == null
                || !provenance.path("academicApprovalRequired").asBoolean(false)
                || !"reject".equalsIgnoreCase(text(policies, "unsupportedNumericScore"))
                || !"reject".equalsIgnoreCase(text(policies, "officialScoreClaim"))) {
            throw new RubricValidationException(RubricConstants.RUBRIC_MISSING_ANCHOR);
        }
        for (JsonNode reference : references) {
            if (text(reference, "referenceKey") == null || text(reference, "sourceArtifact") == null
                    || text(reference, "sourceRevision") == null || !isSha256(text(reference, "contentHash"))) {
                throw new RubricValidationException(RubricConstants.RUBRIC_INVALID_DOCUMENT);
            }
        }
        for (JsonNode anchor : anchors) {
            String anchorCriterion = text(anchor, "criterionId");
            if (anchorCriterion == null || !actualCriteria.contains(anchorCriterion)
                    || text(anchor, "level") == null || !ALLOWED_LEVELS.contains(text(anchor, "level"))
                    || text(anchor, "approvalStatus") == null
                    || (requireApprovedAnchors && !"APPROVED".equals(text(anchor, "approvalStatus")))
                    || text(anchor, "exampleKey") == null || text(anchor, "sanitizedResponse") == null
                    || text(anchor, "evidenceNote") == null) {
                throw new RubricValidationException(RubricConstants.RUBRIC_MISSING_ANCHOR);
            }
            JsonNode criterion = criteriaFor(criteria, anchorCriterion);
            if (criterion == null || !containsAllowedLevel(criterion, text(anchor, "level"))) {
                throw new RubricValidationException(RubricConstants.RUBRIC_MISSING_ANCHOR);
            }
        }
    }

    private JsonNode criteriaFor(JsonNode criteria, String criterionId) {
        for (JsonNode criterion : criteria) {
            if (criterionId.equals(text(criterion, "criterionId"))) {
                return criterion;
            }
        }
        return null;
    }

    private boolean containsAllowedLevel(JsonNode criterion, String level) {
        for (JsonNode allowed : criterion.path("allowedLevels")) {
            String key = allowed.isTextual() ? allowed.asText() : text(allowed, "key");
            if (level.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /** Server-owned anchor state transition; authors cannot approve their own examples. */
    public String withAnchorApprovalStatus(String documentJson, String approvalStatus) {
        try {
            JsonNode parsed = objectMapper.readTree(documentJson);
            JsonNode copied = parsed == null ? null : parsed.deepCopy();
            if (copied == null || !copied.isObject()) {
                throw new RubricValidationException(RubricConstants.RUBRIC_INVALID_DOCUMENT);
            }
            JsonNode anchors = copied.get("anchors");
            if (anchors != null && anchors.isArray()) {
                for (JsonNode anchor : anchors) {
                    if (anchor.isObject()) {
                        ((tools.jackson.databind.node.ObjectNode) anchor).put("approvalStatus", approvalStatus);
                    }
                }
            }
            return objectMapper.writeValueAsString(copied);
        } catch (RubricValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RubricValidationException(RubricConstants.RUBRIC_INVALID_DOCUMENT);
        }
    }

    public String extractArrayJson(String documentJson, String field) {
        try {
            JsonNode value = objectMapper.readTree(documentJson).get(field);
            return value == null || !value.isArray() ? null : objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private void requireText(JsonNode root, String field) {
        if (text(root, field) == null) {
            throw new RubricValidationException(RubricConstants.RUBRIC_INVALID_DOCUMENT);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isTextual() || value.asText().isBlank() ? null : value.asText().trim();
    }

    private boolean matchesRuntimeContract(JsonNode runtime, String taskTypeKey) {
        TaskRuntimeProfileDescriptor expected;
        try {
            expected = TaskRuntimeProfileRegistry.descriptorFor(taskTypeKey);
        } catch (RuntimeException ex) {
            return false;
        }
        return expected != null
                && !runtime.has("runtimeContractVersion")
                && expected.profileKey().equals(text(runtime, "profileKey"))
                && expected.profileVersion() == integer(runtime, "profileVersion")
                && expected.behaviorKey().equals(text(runtime, "behaviorKey"))
                && expected.rendererKey().equals(text(runtime, "rendererKey"))
                && expected.answerSchemaVersion() == integer(runtime, "answerSchemaVersion")
                && expected.scoringProfileKey().equals(text(runtime, "scoringProfileKey"))
                && expected.scoringProfileVersion() == integer(runtime, "scoringProfileVersion")
                && expected.screenKey().equals(text(runtime, "screenKey"))
                && expected.contractVersion() == integer(runtime, "contractVersion")
                && expected.scoringMode().equals(text(runtime, "scoringMode"))
                && expected.minSupportedAppVersion().equals(text(runtime, "minSupportedAppVersion"))
                && expected.authoringContractKey().equals(text(runtime, "authoringContractKey"))
                && expected.authoringContractVersion() == integer(runtime, "authoringContractVersion")
                && expected.requiredClientCapabilities().equals(arrayTexts(runtime.get("requiredClientCapabilities")));
    }

    private int integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.canConvertToInt() ? value.asInt() : Integer.MIN_VALUE;
    }

    private java.util.List<String> arrayTexts(JsonNode value) {
        if (value == null || !value.isArray()) return java.util.List.of();
        java.util.List<String> result = new java.util.ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual()) return java.util.List.of();
            result.add(item.asText());
        }
        return result;
    }

    private boolean isSha256(String value) {
        return value != null && value.matches("(?i)[0-9a-f]{64}");
    }
}
