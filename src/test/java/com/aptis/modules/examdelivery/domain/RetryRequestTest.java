package com.aptis.modules.examdelivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;

@DisplayName("RetryRequest Domain Tests")
class RetryRequestTest {

    private RetryRequest request;

    @BeforeEach
    void setUp() {
        request = RetryRequest.create("student-123", 100L, 50L);
    }

    // ====================
    // create() Tests
    // ====================

    @Test
    @DisplayName("create: sets PENDING status")
    void create_setPendingStatus() {
        assertThat(request.getStatus()).isEqualTo(RetryRequestStatus.PENDING);
    }

    @Test
    @DisplayName("create: sets studentId")
    void create_setStudentId() {
        assertThat(request.getStudentId()).isEqualTo("student-123");
    }

    @Test
    @DisplayName("create: sets attemptId")
    void create_setAttemptId() {
        assertThat(request.getAttemptId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("create: sets examId")
    void create_setExamId() {
        assertThat(request.getExamId()).isEqualTo(50L);
    }

    @Test
    @DisplayName("create: sets requestedAt to current time")
    void create_setsRequestedAtToCurrentTime() {
        OffsetDateTime beforeCreate = OffsetDateTime.now();
        RetryRequest newRequest = RetryRequest.create("stu-1", 1L, 2L);
        OffsetDateTime afterCreate = OffsetDateTime.now();

        assertThat(newRequest.getRequestedAt())
                .isBetween(beforeCreate, afterCreate);
    }

    @Test
    @DisplayName("create: reviewedAt is null initially")
    void create_reviewedAtIsNull() {
        assertThat(request.getReviewedAt()).isNull();
    }

    @Test
    @DisplayName("create: reviewedBy is null initially")
    void create_reviewedByIsNull() {
        assertThat(request.getReviewedBy()).isNull();
    }

    // ====================
    // approve() Tests
    // ====================

    @Test
    @DisplayName("approve: PENDING → APPROVED")
    void approve_fromPending_transitions() {
        request.approve(1L);

        assertThat(request.getStatus()).isEqualTo(RetryRequestStatus.APPROVED);
    }

    @Test
    @DisplayName("approve: sets reviewedAt")
    void approve_setsReviewedAt() {
        OffsetDateTime beforeApprove = OffsetDateTime.now();
        request.approve(1L);
        OffsetDateTime afterApprove = OffsetDateTime.now();

        assertThat(request.getReviewedAt())
                .isBetween(beforeApprove, afterApprove);
    }

    @Test
    @DisplayName("approve: sets reviewedBy to reviewer ID")
    void approve_setsReviewedBy() {
        request.approve(999L);

        assertThat(request.getReviewedBy()).isEqualTo(999L);
    }

    @Test
    @DisplayName("approve: from REJECTED throws RESOURCE_CONFLICT")
    void approve_fromRejected_throws() {
        request.reject(1L);

        assertThatThrownBy(() -> request.approve(2L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_CONFLICT);
    }

    @Test
    @DisplayName("approve: from APPROVED throws RESOURCE_CONFLICT")
    void approve_fromApproved_throws() {
        request.approve(1L);

        assertThatThrownBy(() -> request.approve(2L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_CONFLICT);
    }

    @Test
    @DisplayName("approve: from CONSUMED throws RESOURCE_CONFLICT")
    void approve_fromConsumed_throws() {
        request.approve(1L);
        request.consume();

        assertThatThrownBy(() -> request.approve(2L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_CONFLICT);
    }

    // ====================
    // reject() Tests
    // ====================

    @Test
    @DisplayName("reject: PENDING → REJECTED")
    void reject_fromPending_transitions() {
        request.reject(1L);

        assertThat(request.getStatus()).isEqualTo(RetryRequestStatus.REJECTED);
    }

    @Test
    @DisplayName("reject: sets reviewedAt")
    void reject_setsReviewedAt() {
        OffsetDateTime beforeReject = OffsetDateTime.now();
        request.reject(1L);
        OffsetDateTime afterReject = OffsetDateTime.now();

        assertThat(request.getReviewedAt())
                .isBetween(beforeReject, afterReject);
    }

    @Test
    @DisplayName("reject: sets reviewedBy to reviewer ID")
    void reject_setsReviewedBy() {
        request.reject(888L);

        assertThat(request.getReviewedBy()).isEqualTo(888L);
    }

    @Test
    @DisplayName("reject: from APPROVED throws RESOURCE_CONFLICT")
    void reject_fromApproved_throws() {
        request.approve(1L);

        assertThatThrownBy(() -> request.reject(2L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_CONFLICT);
    }

    @Test
    @DisplayName("reject: from REJECTED throws RESOURCE_CONFLICT")
    void reject_fromRejected_throws() {
        request.reject(1L);

        assertThatThrownBy(() -> request.reject(2L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_CONFLICT);
    }

    @Test
    @DisplayName("reject: from CONSUMED throws RESOURCE_CONFLICT")
    void reject_fromConsumed_throws() {
        request.approve(1L);
        request.consume();

        assertThatThrownBy(() -> request.reject(2L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_CONFLICT);
    }

    // ====================
    // consume() Tests
    // ====================

    @Test
    @DisplayName("consume: APPROVED → CONSUMED")
    void consume_fromApproved_transitions() {
        request.approve(1L);
        request.consume();

        assertThat(request.getStatus()).isEqualTo(RetryRequestStatus.CONSUMED);
    }

    @Test
    @DisplayName("consume: from PENDING throws RESOURCE_CONFLICT")
    void consume_fromPending_throws() {
        assertThatThrownBy(() -> request.consume())
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_CONFLICT);
    }

    @Test
    @DisplayName("consume: from REJECTED throws RESOURCE_CONFLICT")
    void consume_fromRejected_throws() {
        request.reject(1L);

        assertThatThrownBy(() -> request.consume())
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_CONFLICT);
    }

    @Test
    @DisplayName("consume: from CONSUMED throws RESOURCE_CONFLICT")
    void consume_fromConsumed_throws() {
        request.approve(1L);
        request.consume();

        assertThatThrownBy(() -> request.consume())
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_CONFLICT);
    }

    // ====================
    // State Transition Tests (End-to-end flow)
    // ====================

    @Test
    @DisplayName("Flow: create → approve → consume")
    void flow_createApproveConsume() {
        assertThat(request.getStatus()).isEqualTo(RetryRequestStatus.PENDING);

        request.approve(1L);
        assertThat(request.getStatus()).isEqualTo(RetryRequestStatus.APPROVED);
        assertThat(request.getReviewedBy()).isEqualTo(1L);

        request.consume();
        assertThat(request.getStatus()).isEqualTo(RetryRequestStatus.CONSUMED);
    }

    @Test
    @DisplayName("Flow: create → reject (blocks further transitions)")
    void flow_createReject() {
        assertThat(request.getStatus()).isEqualTo(RetryRequestStatus.PENDING);

        request.reject(1L);
        assertThat(request.getStatus()).isEqualTo(RetryRequestStatus.REJECTED);
        assertThat(request.getReviewedBy()).isEqualTo(1L);

        // Cannot approve a rejected request
        assertThatThrownBy(() -> request.approve(2L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_CONFLICT);

        // Cannot consume a rejected request
        assertThatThrownBy(() -> request.consume())
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_CONFLICT);
    }

    @Test
    @DisplayName("approve and reject use different reviewers correctly")
    void approve_reject_differentReviewers() {
        Long reviewer1 = 111L;
        Long reviewer2 = 222L;

        request.approve(reviewer1);
        assertThat(request.getReviewedBy()).isEqualTo(reviewer1);

        // Create another request with different flow
        RetryRequest request2 = RetryRequest.create("stu-2", 2L, 3L);
        request2.reject(reviewer2);
        assertThat(request2.getReviewedBy()).isEqualTo(reviewer2);
    }
}
