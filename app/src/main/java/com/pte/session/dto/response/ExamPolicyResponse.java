package com.pte.session.dto.response;

public record ExamPolicyResponse(
        String replayPolicyType,
        Integer replayPolicyLimit,
        Boolean deviceCheckRequired,
        Boolean proctorRequired,
        String answerIntegrityLevel,
        String lockdownMode) {
}
