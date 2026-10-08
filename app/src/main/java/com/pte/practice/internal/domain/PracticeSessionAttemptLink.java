package com.pte.practice.internal.domain;

import com.pte.practice.internal.domain.enums.PracticeSessionSourceType;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Additive bridge to the reused attempt execution record. The row is created
 * only after practice preflight can attach an execution attempt; no official
 * session or enrollment is required.
 */
@Entity
@Table(name = "practice_session_attempts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_practice_session_attempt", columnNames = {
                "practice_session_id", "exam_attempt_public_id"
        })
}, indexes = {
        @Index(name = "idx_practice_session_attempts_session", columnList = "practice_session_id"),
        @Index(name = "idx_practice_session_attempts_attempt", columnList = "exam_attempt_public_id")
})
@Getter
@Setter
@NoArgsConstructor
public class PracticeSessionAttemptLink extends BaseEntity {

    @Column(name = "practice_session_id", nullable = false)
    private Long practiceSessionId;

    @Column(name = "exam_attempt_public_id", nullable = false)
    private UUID examAttemptPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private PracticeSessionSourceType sourceType = PracticeSessionSourceType.PRACTICE;
}
