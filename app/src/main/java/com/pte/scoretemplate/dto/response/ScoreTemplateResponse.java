package com.pte.scoretemplate.dto.response;

import java.util.List;
import java.util.UUID;

public record ScoreTemplateResponse(
        UUID publicId,
        String code,
        int version,
        String name,
        String status,
        String rejectionReason,
        List<ScoreTemplateItemResponse> items,
        String templatePolicy) {

    /** Source-compatible constructor for existing trusted module callers. */
    public ScoreTemplateResponse(UUID publicId, String code, int version, String name, String status,
            List<ScoreTemplateItemResponse> items) {
        this(publicId, code, version, name, status, null, items);
    }

    public ScoreTemplateResponse(UUID publicId, String code, int version, String name, String status,
            String rejectionReason, List<ScoreTemplateItemResponse> items) {
        this(publicId, code, version, name, status, rejectionReason, items, "STANDARD_PTE");
    }
}
