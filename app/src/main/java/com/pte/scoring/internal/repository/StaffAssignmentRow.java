package com.pte.scoring.internal.repository;

import java.util.UUID;
import java.time.Instant;

/** Closed scalar projection: does not retain managed scoring entities or answer payloads. */
public interface StaffAssignmentRow {
    UUID getAttemptPublicId();
    UUID getSessionPublicId();
    UUID getExaminerPublicId();
    UUID getBatchPublicId();
    int getEligibleAnswerCount();
    Instant getAssignedAt();
}
