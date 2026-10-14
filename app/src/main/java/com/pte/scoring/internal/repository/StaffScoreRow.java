package com.pte.scoring.internal.repository;

import java.util.UUID;
import java.time.Instant;
import com.pte.scoring.domain.enums.ExaminerAnswerScoreStatus;

/** Closed scalar projection: does not retain managed scoring entities or answer payloads. */
public interface StaffScoreRow {
    UUID getAnswerPublicId();
    UUID getAttemptPublicId();
    UUID getSessionPublicId();
    UUID getExaminerPublicId();
    ExaminerAnswerScoreStatus getStatus();
    Instant getSubmittedAt();
    int getScore();
}
