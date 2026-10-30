package com.pte.scoring.internal.repository;

import java.util.UUID;

/** Closed scalar projection: does not retain managed scoring entities or answer payloads. */
public interface StaffAnswerRow {
    UUID getAnswerPublicId();
    UUID getAttemptPublicId();
    UUID getSessionPublicId();
    UUID getExamDefinitionPublicId();
    String getTaskType();
    com.pte.scoring.domain.enums.ScoringMethod getScoringMethod();
}
