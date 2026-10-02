package com.pte.scoring.dto.response;

import java.util.UUID;

public record ExaminerAssignmentLoadResponse(UUID examinerPublicId, long attemptCount, long eligibleAnswerCount) {
}
