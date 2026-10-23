package com.pte.rubric.internal.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.pte.rubric.domain.RubricCriterion;
import com.pte.rubric.domain.RubricCriterionLevel;
import com.pte.rubric.domain.RubricRevision;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

/** Rebuilds normalized read projections from the canonical rubric document. */
@Component
public class RubricDocumentProjectionService {

    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    public RubricDocumentProjectionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void replaceChildren(RubricRevision revision, String documentJson) {
        try {
            JsonNode root = objectMapper.readTree(documentJson);
            if (!revision.getCriteria().isEmpty()) {
                revision.getCriteria().clear();
                // Flush orphan removals before adding replacement rows. The
                // criterion key is unique per revision, so inserts must not
                // run before Hibernate deletes the previous projection.
                entityManager.flush();
            }
            int sequence = 1;
            for (JsonNode node : root.path("criteria")) {
                RubricCriterion criterion = new RubricCriterion();
                criterion.setCriterionId(node.path("criterionId").asText());
                criterion.setSequence(node.path("order").asInt(sequence));
                criterion.setRequired(node.path("required").asBoolean(true));
                criterion.setApplicability(node.path("applicability").asText("ALWAYS"));
                JsonNode policy = node.get("evidencePolicy");
                criterion.setEvidencePolicyJson(policy == null ? "{}" : objectMapper.writeValueAsString(policy));
                revision.addCriterion(criterion);
                int levelOrder = 1;
                for (JsonNode levelNode : node.path("levels")) {
                    RubricCriterionLevel level = new RubricCriterionLevel();
                    String levelKey = levelNode.isTextual() ? levelNode.asText() : levelNode.path("key").asText();
                    level.setLevelKey(levelKey);
                    level.setLevelOrder(levelOrder++);
                    level.setLabelVi(levelNode.path("labelVi").asText(levelKey));
                    level.setLabelEn(levelNode.path("labelEn").asText(levelKey));
                    level.setDescriptor(levelNode.path("descriptor").asText(""));
                    criterion.addLevel(level);
                }
                sequence++;
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not build normalized rubric projection", ex);
        }
    }
}
