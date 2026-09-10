package com.pte.admin.dto.response;

import java.util.UUID;

public record ClassStudentCountResponse(UUID classPublicId, String className, long studentCount) {
}
