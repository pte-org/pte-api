package com.pte.scoretemplate.dto.response;

import java.util.List;
import java.util.UUID;

public record ScoreTemplateResponse(
        UUID publicId,
        String code,
        int version,
        String name,
        String status,
        List<ScoreTemplateItemResponse> items) {
}
