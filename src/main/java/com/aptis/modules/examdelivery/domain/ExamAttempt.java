package com.aptis.modules.examdelivery.domain;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import com.aptis.modules.examoperations.domain.Exam;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.modules.examdelivery.constant.ExamDeliveryConstants;

@Entity
@Table(name = "exam_attempt")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "answers")
public class ExamAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String studentId;

    @Column(nullable = false, name = "exam_id")
    private Long examId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", insertable = false, updatable = false)
    private Exam exam;

    @Setter(AccessLevel.NONE) // only submit() may change this — invariant (BR-003)
    @Column(nullable = false)
    private Boolean isSubmitted = false;

    @Setter(AccessLevel.NONE) // only submit() may set this
    private LocalDateTime submittedAt;

    @Setter(AccessLevel.NONE) // collection mutated only via internal logic, not direct setter
    @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AttemptAnswer> answers = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Setter(AccessLevel.NONE) // only recordHeartbeat()/switchSection()/submit() may change this
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExamAttemptStatus status = ExamAttemptStatus.NOT_STARTED;

    @Setter(AccessLevel.NONE) // only recordHeartbeat() may advance this
    @Column(name = "last_heartbeat_at")
    private OffsetDateTime lastHeartbeatAt;

    @Setter(AccessLevel.NONE) // only extendTime() may change this — accumulated proctor time grants
    @Column(name = "extra_time_minutes", nullable = false)
    private Integer extraTimeMinutes = 0;

    @Setter(AccessLevel.NONE) // only flag() may set this
    @Column(nullable = false)
    private Boolean flagged = false;

    public void submit() {
        if (this.isSubmitted
                || this.status == ExamAttemptStatus.SUBMITTED
                || this.status == ExamAttemptStatus.DISCONNECTED
                || this.status == ExamAttemptStatus.NOT_STARTED) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT,
                    ExamDeliveryConstants.ATTEMPT_ALREADY_SUBMITTED_DOMAIN);
        }
        this.isSubmitted = true;
        this.submittedAt = LocalDateTime.now();
        this.status = ExamAttemptStatus.SUBMITTED;
    }

    /**
     * A client heartbeat both starts the attempt (first ping, NOT_STARTED → IN_PROGRESS)
     * and keeps it alive (IN_PROGRESS/SECTION_SWITCH). Heartbeats received while SUBMITTED
     * or DISCONNECTED are a no-op — per Design Constraint, DISCONNECTED cannot resume to
     * IN_PROGRESS, and a submitted attempt is immutable.
     */
    public ExamAttemptStatus recordHeartbeat() {
        if (this.status == ExamAttemptStatus.NOT_STARTED) {
            this.status = ExamAttemptStatus.IN_PROGRESS;
        }
        if (this.status == ExamAttemptStatus.IN_PROGRESS || this.status == ExamAttemptStatus.SECTION_SWITCH) {
            this.lastHeartbeatAt = OffsetDateTime.now();
        }
        return this.status;
    }

    public void switchSection() {
        if (this.status != ExamAttemptStatus.IN_PROGRESS && this.status != ExamAttemptStatus.SECTION_SWITCH) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ExamDeliveryConstants.INVALID_STATUS_TRANSITION);
        }
        this.status = ExamAttemptStatus.SECTION_SWITCH;
        this.lastHeartbeatAt = OffsetDateTime.now();
    }

    public List<AttemptAnswer> getAnswers() {
        return Collections.unmodifiableList(answers);
    }

    /**
     * Proctor force-submit is idempotent by design (Design Constraint): a double-click must
     * not be treated as a second transition. Unlike {@link #submit()} (student-initiated,
     * throws on re-submit), this returns {@code false} for an already-SUBMITTED attempt so
     * the caller can skip writing a duplicate audit row, rather than throwing.
     */
    public boolean forceSubmit() {
        if (this.status == ExamAttemptStatus.SUBMITTED) {
            return false;
        }
        this.isSubmitted = true;
        this.submittedAt = LocalDateTime.now();
        this.status = ExamAttemptStatus.SUBMITTED;
        return true;
    }

    /** Proctor time grant, additive — each call is a distinct, legitimate event (not idempotent). */
    public void extendTime(int minutes) {
        this.extraTimeMinutes = this.extraTimeMinutes + minutes;
    }

    /** Proctor integrity flag. The reason itself lives in the PROCTOR_ACTION audit row, not here. */
    public void flag() {
        this.flagged = true;
    }
}
