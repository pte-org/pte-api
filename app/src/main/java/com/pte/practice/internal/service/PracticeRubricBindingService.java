package com.pte.practice.internal.service;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeSessionItem;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.rubric.RubricPublishedProjection;
import com.pte.rubric.RubricService;
import com.pte.rubric.RubricUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Resolves and pins the published rubric without exposing it in student delivery. */
@Service
public class PracticeRubricBindingService {

    private final RubricService rubricService;
    private final JsonMapper jsonMapper;

    public PracticeRubricBindingService(RubricService rubricService) {
        this(rubricService, JsonMapper.builder().build());
    }

    @Autowired
    public PracticeRubricBindingService(RubricService rubricService, JsonMapper jsonMapper) {
        this.rubricService = rubricService;
        this.jsonMapper = jsonMapper;
    }

    public void pin(PracticeSessionItem item, String taskCode) {
        try {
            if (item.getRendererKey() == null
                    || !PracticeConstants.PRACTICE_CLIENT_SUPPORTED_RENDERER_KEYS.contains(item.getRendererKey())) {
                throw new IllegalArgumentException("Practice client does not support this renderer");
            }
            RubricPublishedProjection projection = rubricService.resolvePublishedForTask(taskCode);
            validateRuntimeCompatibility(projection, item, taskCode);
            item.setRubricRevisionPublicId(projection.publicId());
            item.setRubricRevisionNumber(projection.revision());
            item.setRubricCanonicalHash(projection.canonicalHash());
            item.setRubricSnapshotJson(projection.documentJson());
            item.setRuntimeCompatibilityHash(projection.runtimeCompatibilityHash());
        } catch (RubricUnavailableException | IllegalArgumentException exception) {
            throw new com.pte.practice.internal.exception.PracticeSessionException(
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    PracticeConstants.PRACTICE_CONTENT_NOT_READY,
                    PracticeConstants.PRACTICE_CONTENT_NOT_READY_MESSAGE);
        }
    }

    private void validateRuntimeCompatibility(RubricPublishedProjection projection, PracticeSessionItem item,
            String taskCode) {
        try {
            TaskRuntimeProfileDescriptor expected = TaskRuntimeProfileRegistry.descriptorFor(taskCode);
            if (expected == null || projection.runtimeCompatibilityHash() == null
                    || !projection.runtimeCompatibilityHash().equals(rubricService.runtimeCompatibilityHashForTask(taskCode))) {
                throw new IllegalArgumentException("Practice rubric runtime hash does not match the platform contract");
            }
            JsonNode root = jsonMapper.readTree(projection.documentJson());
            JsonNode runtime = root == null ? null : root.get("runtime");
            if (runtime == null || !runtime.isObject()
                    || !taskCode.equalsIgnoreCase(root.path("taskTypeKey").asText(""))
                    || !matchesDescriptor(runtime, expected)
                    || !matchesNumber(runtime, "answerSchemaVersion", item.getAnswerSchemaVersion())
                    || !matchesOptionalText(runtime, "rendererKey", item.getRendererKey())
                    || !matchesNumber(runtime, "contractVersion", item.getContractVersion())) {
                throw new IllegalArgumentException("Practice rubric runtime contract does not match the item");
            }
        } catch (Exception exception) {
            throw new IllegalArgumentException("Practice rubric runtime contract is incompatible", exception);
        }
    }

    private boolean matchesDescriptor(JsonNode runtime, TaskRuntimeProfileDescriptor expected) {
        return expected.profileKey().equals(runtime.path("profileKey").asText())
                && expected.profileVersion() == runtime.path("profileVersion").asInt(Integer.MIN_VALUE)
                && expected.behaviorKey().equals(runtime.path("behaviorKey").asText())
                && expected.rendererKey().equals(runtime.path("rendererKey").asText())
                && expected.answerSchemaVersion() == runtime.path("answerSchemaVersion").asInt(Integer.MIN_VALUE)
                && expected.scoringProfileKey().equals(runtime.path("scoringProfileKey").asText())
                && expected.scoringProfileVersion() == runtime.path("scoringProfileVersion").asInt(Integer.MIN_VALUE)
                && expected.screenKey().equals(runtime.path("screenKey").asText())
                && expected.contractVersion() == runtime.path("contractVersion").asInt(Integer.MIN_VALUE)
                && expected.scoringMode().equals(runtime.path("scoringMode").asText())
                && expected.minSupportedAppVersion().equals(runtime.path("minSupportedAppVersion").asText())
                && expected.authoringContractKey().equals(runtime.path("authoringContractKey").asText())
                && expected.authoringContractVersion() == runtime.path("authoringContractVersion").asInt(Integer.MIN_VALUE)
                && expected.requiredClientCapabilities().equals(arrayText(runtime.get("requiredClientCapabilities")));
    }

    private java.util.List<String> arrayText(JsonNode node) {
        if (node == null || !node.isArray()) return java.util.List.of();
        java.util.List<String> values = new java.util.ArrayList<>();
        node.forEach(value -> values.add(value.asText()));
        return values;
    }

    private boolean matchesNumber(JsonNode node, String field, Integer expected) {
        return expected != null && node.has(field) && node.path(field).asInt(Integer.MIN_VALUE) == expected;
    }

    private boolean matchesOptionalText(JsonNode node, String field, String expected) {
        return !node.has(field) || (expected != null && expected.equals(node.path(field).asText()));
    }
}
