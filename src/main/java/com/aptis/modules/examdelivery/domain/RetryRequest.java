package com.aptis.modules.examdelivery.domain;

import java.time.OffsetDateTime;

import com.aptis.common.domain.BaseEntity;
import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Standalone approval flow, independent of any AI Writing/Speaking scoring-confirmation
 * queue (Design Constraint). No auto-grant: every retry needs an explicit Host/Grader
 * decision regardless of the session's max_retry_count budget.
 */
@Entity
@Table(name = "retry_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(callSuper = true)
public class RetryRequest extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private String studentId;

    @Column(name = "attempt_id", nullable = false)
    private Long attemptId;

    @Column(name = "exam_id", nullable = false)
    private Long examId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RetryRequestStatus status;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    public static RetryRequest create(String studentId, Long attemptId, Long examId) {
        RetryRequest request = new RetryRequest();
        request.studentId = studentId;
        request.attemptId = attemptId;
        request.examId = examId;
        request.status = RetryRequestStatus.PENDING;
        request.requestedAt = OffsetDateTime.now();
        return request;
    }

    public void approve(Long reviewerId) {
        requireStatus(RetryRequestStatus.PENDING);
        this.status = RetryRequestStatus.APPROVED;
        this.reviewedAt = OffsetDateTime.now();
        this.reviewedBy = reviewerId;
    }

    public void reject(Long reviewerId) {
        requireStatus(RetryRequestStatus.PENDING);
        this.status = RetryRequestStatus.REJECTED;
        this.reviewedAt = OffsetDateTime.now();
        this.reviewedBy = reviewerId;
    }

    /** Once consumed, this request can never again be read as pending-approvable (Risk mitigation). */
    public void consume() {
        requireStatus(RetryRequestStatus.APPROVED);
        this.status = RetryRequestStatus.CONSUMED;
    }

    private void requireStatus(RetryRequestStatus required) {
        if (this.status != required) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT,
                    "RetryRequest is not in " + required + " status (current: " + this.status + ")");
        }
    }
}
