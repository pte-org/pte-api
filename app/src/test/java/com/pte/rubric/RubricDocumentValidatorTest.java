package com.pte.rubric;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.rubric.internal.exception.RubricValidationException;
import com.pte.rubric.internal.service.RubricDocumentValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RubricDocumentValidatorTest {

    @Test
    void rejectsObjectiveRubricThatOmitsDeterministicEvidence() throws Exception {
        ObjectMapper mapper = JsonMapper.builder().build();
        ObjectNode root = objectiveDocument(mapper, true);
        RubricDocumentValidator validator = new RubricDocumentValidator(mapper);
        assertThatThrownBy(() -> validator.validatePublicationReadiness(root.toString(), "MC_READING_SINGLE", false))
                .isInstanceOf(RubricValidationException.class);
    }

    @Test
    void rejectsNotAssessableLevelWhenPolicyDisallowsIt() throws Exception {
        ObjectMapper mapper = JsonMapper.builder().build();
        ObjectNode root = objectiveDocument(mapper, false);
        RubricDocumentValidator validator = new RubricDocumentValidator(mapper);
        assertThatThrownBy(() -> validator.validatePublicationReadiness(root.toString(), "MC_READING_SINGLE", false))
                .isInstanceOf(RubricValidationException.class);
    }

    private ObjectNode objectiveDocument(ObjectMapper mapper, boolean allowNotAssessable) {
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", "1.0");
        root.put("taskTypeKey", "MC_READING_SINGLE");
        root.set("runtime", runtime(mapper, "MC_READING_SINGLE"));
        ArrayNode criteria = root.putArray("criteria");
        ObjectNode criterion = criteria.addObject();
        criterion.put("criterionId", "ANSWER_MATCH");
        criterion.put("order", 1);
        criterion.put("required", true);
        criterion.putArray("allowedLevels").add("NOT_ASSESSABLE");
        ArrayNode levels = criterion.putArray("levels");
        levels.addObject().put("key", "NOT_ASSESSABLE").put("labelVi", "Chua danh gia")
                .put("labelEn", "Not assessable").put("descriptor", "Evidence is unavailable.");
        ObjectNode evidencePolicy = criterion.putObject("evidencePolicy");
        evidencePolicy.putArray("requiredTypes").add("RESPONSE");
        evidencePolicy.put("allowNotAssessable", allowNotAssessable);
        evidencePolicy.put("minimumCoverage", "NOT_ASSESSABLE");
        root.putObject("policies").put("emptyResponse", "return_not_assessable_without_inference")
                .put("invalidResponse", "return_not_assessable_and_explain_input_issue")
                .put("offTopicResponse", "use_task_specific_policy_and_quote_only_verified_evidence")
                .put("unsupportedNumericScore", "reject").put("officialScoreClaim", "reject");
        root.putArray("references").addObject().put("referenceKey", "source").put("sourceArtifact", "matrix")
                .put("sourceRevision", "1").put("contentHash", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        root.putArray("anchors").addObject().put("criterionId", "ANSWER_MATCH").put("level", "NOT_ASSESSABLE")
                .put("approvalStatus", "PENDING").put("exampleKey", "example-1")
                .put("sanitizedResponse", "example").put("evidenceNote", "example evidence");
        root.putObject("provenance").put("sourceDocument", "practice-rubric-v1.md")
                .put("sourceRevision", "1.0").put("academicApprovalRequired", true);
        return root;
    }

    private ObjectNode runtime(ObjectMapper mapper, String taskType) {
        TaskRuntimeProfileDescriptor profile = TaskRuntimeProfileRegistry.descriptorFor(taskType);
        ObjectNode runtime = mapper.createObjectNode();
        runtime.put("profileKey", profile.profileKey()).put("profileVersion", profile.profileVersion())
                .put("behaviorKey", profile.behaviorKey()).put("rendererKey", profile.rendererKey())
                .put("answerSchemaVersion", profile.answerSchemaVersion()).put("scoringProfileKey", profile.scoringProfileKey())
                .put("scoringProfileVersion", profile.scoringProfileVersion()).put("screenKey", profile.screenKey())
                .put("contractVersion", profile.contractVersion()).put("scoringMode", profile.scoringMode())
                .put("minSupportedAppVersion", profile.minSupportedAppVersion())
                .put("authoringContractKey", profile.authoringContractKey())
                .put("authoringContractVersion", profile.authoringContractVersion());
        ArrayNode capabilities = runtime.putArray("requiredClientCapabilities");
        profile.requiredClientCapabilities().forEach(capabilities::add);
        return runtime;
    }
}
