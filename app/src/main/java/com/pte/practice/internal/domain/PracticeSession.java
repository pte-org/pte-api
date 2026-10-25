package com.pte.practice.internal.domain;

import com.pte.practice.internal.domain.enums.PracticeSessionKind;
import com.pte.practice.internal.domain.enums.PracticeSessionSourceType;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.domain.enums.PracticeTimingMode;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Product-level practice aggregate. It intentionally does not point at an
 * official ExamSession or enrollment; a later execution bridge may attach a
 * pinned attempt through the explicit PRACTICE source link.
 */
@Entity
@Table(name = "practice_sessions", indexes = {
        @Index(name = "idx_practice_sessions_student", columnList = "student_public_id"),
        @Index(name = "idx_practice_sessions_tenant_status", columnList = "tenant_id,status")
})
@Getter
@Setter
@NoArgsConstructor
public class PracticeSession extends BaseEntity {

    @Column(name = "student_public_id", nullable = false)
    private UUID studentPublicId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "product_code", nullable = false, length = 64)
    private String productCode;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(name = "time_limit_seconds", nullable = false)
    private int timeLimitSeconds;

    @Column(name = "catalog_version", nullable = false, length = 32)
    private String catalogVersion;

    @Column(name = "selected_task_types", columnDefinition = "text")
    private String selectedTaskTypes;

    @Column(name = "client_capabilities", columnDefinition = "text")
    private String clientCapabilities;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private PracticeSessionSourceType sourceType = PracticeSessionSourceType.PRACTICE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PracticeSessionStatus status = PracticeSessionStatus.OVERVIEW;

    @Column(name = "start_idempotency_key", nullable = false, length = 128)
    private String startIdempotencyKey;

    @Column(name = "start_request_hash", nullable = false, length = 64)
    private String startRequestHash;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "deadline_at")
    private Instant deadlineAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "discarded_at")
    private Instant discardedAt;

    @Column(name = "last_activity_at")
    private Instant lastActivityAt;

    /** Null on sessions created before V133; read through {@link #effectiveTimingMode()}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "timing_mode", length = 16)
    private PracticeTimingMode timingMode;

    /** Questions per task type the student asked for; null on sessions created before V133 (one per type). */
    @Column(name = "question_count")
    private Integer questionCount;

    /** Null on sessions created before V134; read through {@link #effectiveKind()}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 16)
    private PracticeSessionKind kind;

    /** The skill a SKILL session covers (SPEAKING, WRITING, READING, LISTENING); null otherwise. */
    @Column(name = "skill_code", length = 16)
    private String skillCode;

    /** Internal id of the pinned mock test a MOCK_TEST session runs; null otherwise. */
    @Column(name = "mock_test_id")
    private Long mockTestId;

    @Version
    private Long version;

    public PracticeSessionKind effectiveKind() {
        return kind == null ? PracticeSessionKind.TASK : kind;
    }

    public PracticeTimingMode effectiveTimingMode() {
        return timingMode == null ? PracticeTimingMode.UNTIMED : timingMode;
    }
}
