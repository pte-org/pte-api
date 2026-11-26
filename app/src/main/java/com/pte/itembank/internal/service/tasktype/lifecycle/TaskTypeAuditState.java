package com.pte.itembank.internal.service.tasktype.lifecycle;

import com.pte.itembank.domain.QuestionType;
import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.shared.audit.AuditLogChangeWriter;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.audit.AuditStateChange;
import com.pte.shared.security.CurrentUser;

/**
 * Business snapshot of a task type for the audit trail. Unlike a template item, a task type owns its runtime
 * binding (screen, profile, scoring), so those fields are part of what an editor changes and are kept here.
 */
record TaskTypeAuditState(
        String code,
        String taskTypeKey,
        String displayName,
        String shortName,
        String instruction,
        String section,
        boolean scored,
        boolean active,
        int displayOrder,
        String lifecycleStatus,
        Requirements requirements,
        Runtime runtime) {

    private static final int SUMMARY_MAX_LENGTH = 500;

    record Requirements(boolean audioPrompt, boolean imagePrompt, boolean promptText, boolean options,
            boolean correctAnswer, boolean wordCount, boolean referenceAnswer, boolean singleCorrectOption,
            boolean optionOrderAsCorrectPosition, boolean stimulusBeforeTimer, int stimulusEstimateSeconds) {
    }

    record Runtime(String screenKey, String profileKey, Integer profileVersion, String behaviorKey,
            String rendererKey, Integer answerSchemaVersion, String scoringProfileKey, Integer scoringProfileVersion,
            String scoringMode, String minAppVersion, String authoringContractKey, Integer authoringContractVersion) {
    }

    static TaskTypeAuditState of(QuestionType type) {
        return new TaskTypeAuditState(type.getCode(), type.getTaskTypeKey(), type.getDisplayName(),
                type.getShortName(), type.getInstruction(),
                type.getSection() == null ? null : type.getSection().name(), type.isScored(), type.isActive(),
                type.getDisplayOrder(), type.getLifecycleStatus(),
                new Requirements(type.isRequiresAudioPrompt(), type.isRequiresImagePrompt(),
                        type.isRequiresPromptText(), type.isRequiresOptions(), type.isRequiresCorrectAnswer(),
                        type.isRequiresWordCount(), type.isRequiresReferenceAnswer(),
                        type.isRequiresSingleCorrectOption(), type.isUsesOptionOrderAsCorrectPosition(),
                        type.isStimulusBeforeTimer(), type.getStimulusEstimateSeconds()),
                new Runtime(type.getScreenKey(), type.getRuntimeProfileKey(), type.getRuntimeProfileVersion(),
                        type.getRuntimeBehaviorKey(), type.getRuntimeRendererKey(),
                        type.getRuntimeAnswerSchemaVersion(), type.getRuntimeScoringProfileKey(),
                        type.getRuntimeScoringProfileVersion(), type.getRuntimeScoringMode(),
                        type.getRuntimeMinAppVersion(), type.getAuthoringContractKey(),
                        type.getAuthoringContractVersion()));
    }

    /**
     * Writes the command with before/after snapshots ({@code after} null for a deletion), or only the summary when
     * no change writer is wired (the unit-test constructors pass null).
     */
    static void write(AuditLogChangeWriter writer, AuditLogService fallback, CurrentUser caller, String action,
            QuestionType type, String summary, TaskTypeAuditState before, TaskTypeAuditState after) {
        if (caller == null) {
            return;
        }
        String aggregateId = String.valueOf(type.getPublicId());
        String text = summary.length() <= SUMMARY_MAX_LENGTH ? summary : summary.substring(0, SUMMARY_MAX_LENGTH);
        if (writer != null) {
            writer.recordState(caller, new AuditStateChange(ItembankConstants.TASK_TYPE_AUDIT_AGGREGATE,
                    aggregateId, type.getDisplayName(), type.getCode(), action, text, before, after));
        } else if (fallback != null) {
            fallback.record(caller, ItembankConstants.TASK_TYPE_AUDIT_AGGREGATE, aggregateId, action, text);
        }
    }
}
