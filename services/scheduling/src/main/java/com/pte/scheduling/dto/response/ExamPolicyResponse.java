package com.pte.scheduling.dto.response;

public record ExamPolicyResponse(
        String replayPolicyType,
        Integer replayPolicyLimit,
        Boolean deviceCheckRequired,
        Boolean proctorRequired,
        String answerIntegrityLevel) {
}
