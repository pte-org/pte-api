package com.pte.assessment.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * {@code tenantId} is null for a platform default template; {@code baseDefinitionPublicId} is set on host
 * templates and names the default they were cloned from.
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
        UUID baseDefinitionPublicId) {

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
