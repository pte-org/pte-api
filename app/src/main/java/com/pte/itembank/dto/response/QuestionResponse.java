package com.pte.itembank.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuestionResponse(
        UUID publicId,
        String pteTaskType,
        String section,
        UUID tenantId,
        String status,
        String promptText,
        UUID audioPromptRef,
        UUID imagePromptRef,
        String referenceAnswerText,
        String correctAnswerText,
        Integer minWordCount,
        Integer maxWordCount,
        List<OptionResponse> options,
        UUID revisionGroupPublicId,
        int revisionNumber,
        UUID supersedesPublicId,
        boolean current,
        long version,
        String rejectionReason,
        String taskTypeKey,
        boolean canDeleteDraft,
        boolean canArchive,
        String deleteBlockReason,
        String pool,
        boolean availableForPractice,
        boolean availableForExam,
        /** True when this question or an earlier revision in its group was ever published. */
        boolean previouslyPublished,
        /** When the question last changed; tracking screens show how long a question has waited. */
        Instant updatedAt,
        /** CEFR level (A1 to C2) the question is written for; null when not yet classified. */
        String cefrLevel,
        /** The academic who wrote the question; null for legacy rows without an owner. */
        UUID authorUserPublicId,
        /** AI provenance and review sheet; null for hand-written questions. Only exposed to authoring callers. */
        GenerationMetaResponse generationMeta,
        /** Student-facing hints on how to approach the task; never the answer. */
        String answerHintVi,
        String answerHintEn) {

    /** Shape of a response without answer hints. */
    public QuestionResponse(UUID publicId, String pteTaskType, String section, UUID tenantId, String status,
            String promptText, UUID audioPromptRef, UUID imagePromptRef, String referenceAnswerText,
            String correctAnswerText, Integer minWordCount, Integer maxWordCount, List<OptionResponse> options,
            UUID revisionGroupPublicId, int revisionNumber, UUID supersedesPublicId, boolean current, long version,
            String rejectionReason, String taskTypeKey, boolean canDeleteDraft, boolean canArchive,
            String deleteBlockReason, String pool, boolean availableForPractice, boolean availableForExam,
            boolean previouslyPublished, Instant updatedAt, String cefrLevel, UUID authorUserPublicId,
            GenerationMetaResponse generationMeta) {
        this(publicId, pteTaskType, section, tenantId, status, promptText, audioPromptRef, imagePromptRef,
                referenceAnswerText, correctAnswerText, minWordCount, maxWordCount, options, revisionGroupPublicId,
                revisionNumber, supersedesPublicId, current, version, rejectionReason, taskTypeKey, canDeleteDraft,
                canArchive, deleteBlockReason, pool, availableForPractice, availableForExam, previouslyPublished,
                updatedAt, cefrLevel, authorUserPublicId, generationMeta, null, null);
    }

    /** Shape of a response that does not say who wrote the question or generation metadata. */
    public QuestionResponse(UUID publicId, String pteTaskType, String section, UUID tenantId, String status,
            String promptText, UUID audioPromptRef, UUID imagePromptRef, String referenceAnswerText,
            String correctAnswerText, Integer minWordCount, Integer maxWordCount, List<OptionResponse> options,
            UUID revisionGroupPublicId, int revisionNumber, UUID supersedesPublicId, boolean current, long version,
            String rejectionReason, String taskTypeKey, boolean canDeleteDraft, boolean canArchive,
            String deleteBlockReason, String pool, boolean availableForPractice, boolean availableForExam,
            boolean previouslyPublished, Instant updatedAt, String cefrLevel) {
        this(publicId, pteTaskType, section, tenantId, status, promptText, audioPromptRef, imagePromptRef,
                referenceAnswerText, correctAnswerText, minWordCount, maxWordCount, options, revisionGroupPublicId,
                revisionNumber, supersedesPublicId, current, version, rejectionReason, taskTypeKey, canDeleteDraft,
                canArchive, deleteBlockReason, pool, availableForPractice, availableForExam, previouslyPublished,
                updatedAt, cefrLevel, null, null);
    }
}
