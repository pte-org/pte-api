package com.pte.admin.dto.response;

import java.util.List;
import java.util.UUID;

public record MergeClassesResponse(
        UUID targetClassPublicId,
        List<UUID> sourceClassPublicIds,
        List<UUID> movedStudentPublicIds) {
}
