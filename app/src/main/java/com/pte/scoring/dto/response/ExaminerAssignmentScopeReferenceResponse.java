package com.pte.scoring.dto.response;

import com.pte.scoring.domain.enums.AssignmentScopeType;

import java.util.UUID;

public record ExaminerAssignmentScopeReferenceResponse(AssignmentScopeType type, UUID scopePublicId) {
}
