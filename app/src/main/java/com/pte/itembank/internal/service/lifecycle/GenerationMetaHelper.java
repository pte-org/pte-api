package com.pte.itembank.internal.service.lifecycle;

import com.pte.itembank.domain.Question;
import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.shared.audit.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper for managing generation metadata on questions.
 * Handles marking AI-generated questions as edited by human.
 */
class GenerationMetaHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerationMetaHelper.class);
    private final AuditLogService auditLogService;

    GenerationMetaHelper(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * Mark an AI-generated question as edited by human when its content is updated.
     * Injects the `editedByHuman: true` flag into the existing generation metadata JSON.
     */
    void markAsEditedByHuman(Question question) {
        if (question.getGenerationMeta() == null) {
            return;
        }
        try {
            String meta = question.getGenerationMeta();
            if (!meta.contains("\"editedByHuman\"")) {
                // Insert before closing brace
                String updated = meta.replaceFirst("\\}\\s*$", ",\"editedByHuman\":true}");
                question.setGenerationMeta(updated);
            }
        } catch (Exception e) {
            // If parsing fails, log but continue - the question is still valid
            if (auditLogService != null) {
                auditLogService.recordFailure(null, ItembankConstants.QUESTION_AGGREGATE,
                        question.getPublicId().toString(),
                        "GENERATION_META_UPDATE_FAILED",
                        "Failed to mark generation_meta as editedByHuman");
            }
        }
    }
}
