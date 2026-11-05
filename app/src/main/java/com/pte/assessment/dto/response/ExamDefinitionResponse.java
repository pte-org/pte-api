package com.pte.assessment.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code tenantId} is null for a platform default template; {@code baseDefinitionPublicId} is set on host
 * templates and names the default they were cloned from. {@code authorName} is filled only where the caller
 * resolves it (the platform template detail); the other fields are always set from the entity.
 * {@code rubricVersionPublicId} is the scoring rubric version pinned when the template was created; null only for
 * legacy rows and for callers that build a response without an entity.
 */
public record ExamDefinitionResponse(
        UUID publicId,
        String code,
        int version,
        String name,
        String status,
        String rejectionReason,
        List<ExamDefinitionItemResponse> items,
        String templatePolicy,
        UUID tenantId,
        UUID baseDefinitionPublicId,
        Instant createdAt,
        Instant updatedAt,
        UUID authorUserPublicId,
        String authorName,
        ExamTimeFrameResponse timeFrame,
        Boolean pearsonStandard,
        List<String> standardDeviations,
        List<CefrTargetResponse> cefrTargets,
        UUID rubricVersionPublicId) {

    /** Constructor for callers that do not carry the CEFR mix or a rubric pin. */
    public ExamDefinitionResponse(UUID publicId, String code, int version, String name, String status,
            String rejectionReason, List<ExamDefinitionItemResponse> items, String templatePolicy, UUID tenantId,
            UUID baseDefinitionPublicId, Instant createdAt, Instant updatedAt, UUID authorUserPublicId,
            String authorName, ExamTimeFrameResponse timeFrame, Boolean pearsonStandard,
            List<String> standardDeviations) {
        this(publicId, code, version, name, status, rejectionReason, items, templatePolicy, tenantId,
                baseDefinitionPublicId, createdAt, updatedAt, authorUserPublicId, authorName, timeFrame,
                pearsonStandard, standardDeviations, List.of(), null);
    }

    /** Constructor for callers that do not carry the Pearson-standard label or a rubric pin. */
    public ExamDefinitionResponse(UUID publicId, String code, int version, String name, String status,
            String rejectionReason, List<ExamDefinitionItemResponse> items, String templatePolicy, UUID tenantId,
            UUID baseDefinitionPublicId, Instant createdAt, Instant updatedAt, UUID authorUserPublicId,
            String authorName, ExamTimeFrameResponse timeFrame) {
        this(publicId, code, version, name, status, rejectionReason, items, templatePolicy, tenantId,
                baseDefinitionPublicId, createdAt, updatedAt, authorUserPublicId, authorName, timeFrame, null,
                List.of(), List.of(), null);
    }

    /** Compatibility constructor for callers that do not carry audit fields. */
    public ExamDefinitionResponse(UUID publicId, String code, int version, String name, String status,
            String rejectionReason, List<ExamDefinitionItemResponse> items, String templatePolicy, UUID tenantId,
            UUID baseDefinitionPublicId) {
        this(publicId, code, version, name, status, rejectionReason, items, templatePolicy, tenantId,
                baseDefinitionPublicId, null, null, null, null, null, null, List.of());
    }

    public ExamDefinitionResponse withAuthorName(String resolvedName) {
        return new ExamDefinitionResponse(publicId, code, version, name, status, rejectionReason, items,
                templatePolicy, tenantId, baseDefinitionPublicId, createdAt, updatedAt, authorUserPublicId,
                resolvedName, timeFrame, pearsonStandard, standardDeviations, cefrTargets, rubricVersionPublicId);
    }

    /** Adds whether a host template still follows the platform template it was cloned from, and why not. */
    public ExamDefinitionResponse withStandardLabel(boolean standard, List<String> deviations) {
        return new ExamDefinitionResponse(publicId, code, version, name, status, rejectionReason, items,
                templatePolicy, tenantId, baseDefinitionPublicId, createdAt, updatedAt, authorUserPublicId,
                authorName, timeFrame, standard, deviations, cefrTargets, rubricVersionPublicId);
    }

    /** Source-compatible constructor for existing trusted module callers. */
    public ExamDefinitionResponse(UUID publicId, String code, int version, String name, String status,
            List<ExamDefinitionItemResponse> items) {
        this(publicId, code, version, name, status, null, items);
    }

    public ExamDefinitionResponse(UUID publicId, String code, int version, String name, String status,
            String rejectionReason, List<ExamDefinitionItemResponse> items) {
        this(publicId, code, version, name, status, rejectionReason, items, "STANDARD_PTE");
    }

    public ExamDefinitionResponse(UUID publicId, String code, int version, String name, String status,
            String rejectionReason, List<ExamDefinitionItemResponse> items, String templatePolicy) {
        this(publicId, code, version, name, status, rejectionReason, items, templatePolicy, null, null);
    }
}
