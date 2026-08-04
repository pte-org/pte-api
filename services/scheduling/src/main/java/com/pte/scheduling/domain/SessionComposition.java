package com.pte.scheduling.domain;

import com.pte.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One included task slot in a session's delivery order — a host-chosen subset
 * (or all) of the referenced snapshot's {@link SnapshotRefItem}s. This IS the
 * "practice vs full mock" mechanism: composition is config over an existing
 * immutable snapshot, never new content (ADR-001).
 * {@code timingOverrideSeconds} is optional (null = use the task type's default
 * prep/response timing, applied later by exam-delivery).
 */
@Entity
@Table(name = "session_compositions", indexes = {
        @Index(name = "idx_compositions_session", columnList = "session_id")
})
@Getter
@Setter
@NoArgsConstructor
public class SessionComposition extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ExamSession session;

    @Column(nullable = false)
    private String taskType;

    @Column(nullable = false)
    private String section;

    @Column(nullable = false)
    private int orderIndex;

    @Column
    private Integer timingOverrideSeconds;
}
