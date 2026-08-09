package com.pte.examdelivery.domain;

import com.pte.common.domain.BaseEntity;
import com.pte.examdelivery.domain.enums.AnswerStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A student's response to one pinned task. {@code payload} is a free-form
 * answer string (selected option ids as CSV for MCQ, essay text for Write
 * Essay) — scoring (Phase 7) interprets it against {@code PinnedItem}'s
 * correct-answer data. {@code expired=true} marks an auto-finalized empty
 * answer whose response window elapsed without a submission.
 */
@Entity
@Table(name = "attempt_answers", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"attempt_id", "pinned_item_id"})
}, indexes = {
        @Index(name = "idx_answers_attempt", columnList = "attempt_id")
})
@Getter
@Setter
@NoArgsConstructor
public class AttemptAnswer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    private ExamAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pinned_item_id", nullable = false)
    private PinnedItem pinnedItem;

    @Column(columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnswerStatus status = AnswerStatus.SUBMITTED;

    @Column(nullable = false)
    private boolean expired = false;
}
