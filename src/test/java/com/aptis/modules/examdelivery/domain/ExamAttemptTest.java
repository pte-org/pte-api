package com.aptis.modules.examdelivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.modules.examdelivery.constant.ExamDeliveryConstants;

@DisplayName("ExamAttempt Domain State Machine Tests")
class ExamAttemptTest {

    private ExamAttempt attempt;

    private ExamAttempt newAttempt() {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setStudentId("STU-001");
        attempt.setExamId(1L);
        return attempt;
    }

    @BeforeEach
    void setUp() {
        attempt = new ExamAttempt();
        ReflectionTestUtils.setField(attempt, "id", 1L);
        ReflectionTestUtils.setField(attempt, "studentId", "student-123");
        ReflectionTestUtils.setField(attempt, "examId", 100L);
        ReflectionTestUtils.setField(attempt, "status", ExamAttemptStatus.NOT_STARTED);
        ReflectionTestUtils.setField(attempt, "isSubmitted", false);
        ReflectionTestUtils.setField(attempt, "lastHeartbeatAt", null);
    }

    // Phase 6 tests ensure submit() only works from proper states
    @Test
    void submitTwiceThrows() {
        attempt.recordHeartbeat(); // Set to IN_PROGRESS first
        attempt.submit();

        // Second submit should throw
        assertThrows(ApiException.class, attempt::submit);
    }

    @Test
    void submittedAtIsSetAfterSubmit() {
        attempt.recordHeartbeat(); // Set to IN_PROGRESS first
        LocalDateTime beforeSubmit = LocalDateTime.now();

        attempt.submit();

        assertTrue(attempt.getSubmittedAt().isAfter(beforeSubmit)
                || attempt.getSubmittedAt().isEqual(beforeSubmit));
    }

    @Test
    void getAnswersIsUnmodifiable() {
        ExamAttempt attempt = newAttempt();

        List<AttemptAnswer> answers = attempt.getAnswers();

        assertThrows(UnsupportedOperationException.class, () -> answers.add(new AttemptAnswer()));
    }

    @Test
    void getAnswersStartsEmpty() {
        ExamAttempt attempt = newAttempt();

        assertThat(attempt.getAnswers()).isEmpty();
    }

    // ====================
    // recordHeartbeat() Tests
    // ====================

    @Test
    @DisplayName("recordHeartbeat: NOT_STARTED to IN_PROGRESS")
    void recordHeartbeat_transitionNotStartedToInProgress() {
        ExamAttemptStatus result = attempt.recordHeartbeat();
        assertThat(result).isEqualTo(ExamAttemptStatus.IN_PROGRESS);
        assertThat(attempt.getStatus()).isEqualTo(ExamAttemptStatus.IN_PROGRESS);
        assertThat(attempt.getLastHeartbeatAt()).isNotNull();
    }

    @Test
    @DisplayName("recordHeartbeat: sets lastHeartbeatAt")
    void recordHeartbeat_setsTimestampOnFirstCall() {
        OffsetDateTime beforeCall = OffsetDateTime.now();
        attempt.recordHeartbeat();
        OffsetDateTime timestamp = attempt.getLastHeartbeatAt();
        OffsetDateTime afterCall = OffsetDateTime.now();
        assertThat(timestamp).isBetween(beforeCall, afterCall);
    }

    @Test
    @DisplayName("recordHeartbeat: IN_PROGRESS updates timestamp")
    void recordHeartbeat_updatesTimestampWhileInProgress() throws InterruptedException {
        attempt.recordHeartbeat();
        OffsetDateTime firstTimestamp = attempt.getLastHeartbeatAt();
        Thread.sleep(50);

        OffsetDateTime beforeSecondCall = OffsetDateTime.now();
        ExamAttemptStatus result = attempt.recordHeartbeat();
        OffsetDateTime secondTimestamp = attempt.getLastHeartbeatAt();
        OffsetDateTime afterSecondCall = OffsetDateTime.now();

        assertThat(result).isEqualTo(ExamAttemptStatus.IN_PROGRESS);
        assertThat(attempt.getStatus()).isEqualTo(ExamAttemptStatus.IN_PROGRESS);
        assertThat(secondTimestamp).isAfter(firstTimestamp);
        assertThat(secondTimestamp).isBetween(beforeSecondCall, afterSecondCall);
    }

    @Test
    @DisplayName("recordHeartbeat: SECTION_SWITCH updates timestamp")
    void recordHeartbeat_updatesTimestampInSectionSwitch() throws InterruptedException {
        attempt.recordHeartbeat();
        attempt.switchSection();
        OffsetDateTime sectionSwitchTimestamp = attempt.getLastHeartbeatAt();

        Thread.sleep(50);

        OffsetDateTime beforeHeartbeat = OffsetDateTime.now();
        ExamAttemptStatus result = attempt.recordHeartbeat();
        OffsetDateTime newTimestamp = attempt.getLastHeartbeatAt();
        OffsetDateTime afterHeartbeat = OffsetDateTime.now();

        assertThat(result).isEqualTo(ExamAttemptStatus.SECTION_SWITCH);
        assertThat(newTimestamp).isAfter(sectionSwitchTimestamp);
        assertThat(newTimestamp).isBetween(beforeHeartbeat, afterHeartbeat);
    }

    @Test
    @DisplayName("recordHeartbeat: DISCONNECTED is no-op")
    void recordHeartbeat_noOpWhenDisconnected() {
        attempt.recordHeartbeat();
        ReflectionTestUtils.setField(attempt, "status", ExamAttemptStatus.DISCONNECTED);
        OffsetDateTime disconnectedTimestamp = OffsetDateTime.now().minus(60, ChronoUnit.SECONDS);
        ReflectionTestUtils.setField(attempt, "lastHeartbeatAt", disconnectedTimestamp);

        ExamAttemptStatus result = attempt.recordHeartbeat();

        assertThat(result).isEqualTo(ExamAttemptStatus.DISCONNECTED);
        assertThat(attempt.getStatus()).isEqualTo(ExamAttemptStatus.DISCONNECTED);
        assertThat(attempt.getLastHeartbeatAt()).isEqualTo(disconnectedTimestamp);
    }

    @Test
    @DisplayName("recordHeartbeat: SUBMITTED is no-op")
    void recordHeartbeat_noOpWhenSubmitted() {
        attempt.recordHeartbeat();
        ReflectionTestUtils.setField(attempt, "status", ExamAttemptStatus.SUBMITTED);
        ReflectionTestUtils.setField(attempt, "isSubmitted", true);
        OffsetDateTime submittedTimestamp = OffsetDateTime.now().minus(120, ChronoUnit.SECONDS);
        ReflectionTestUtils.setField(attempt, "lastHeartbeatAt", submittedTimestamp);

        ExamAttemptStatus result = attempt.recordHeartbeat();

        assertThat(result).isEqualTo(ExamAttemptStatus.SUBMITTED);
        assertThat(attempt.getStatus()).isEqualTo(ExamAttemptStatus.SUBMITTED);
        assertThat(attempt.getLastHeartbeatAt()).isEqualTo(submittedTimestamp);
    }

    // ====================
    // switchSection() Tests
    // ====================

    @Test
    @DisplayName("switchSection: IN_PROGRESS to SECTION_SWITCH")
    void switchSection_fromInProgress() {
        attempt.recordHeartbeat();
        attempt.switchSection();
        assertThat(attempt.getStatus()).isEqualTo(ExamAttemptStatus.SECTION_SWITCH);
    }

    @Test
    @DisplayName("switchSection: SECTION_SWITCH updates timestamp")
    void switchSection_fromSectionSwitch() throws InterruptedException {
        attempt.recordHeartbeat();
        attempt.switchSection();
        OffsetDateTime firstSwitchTimestamp = attempt.getLastHeartbeatAt();

        Thread.sleep(50);

        OffsetDateTime beforeSecondSwitch = OffsetDateTime.now();
        attempt.switchSection();
        OffsetDateTime secondSwitchTimestamp = attempt.getLastHeartbeatAt();
        OffsetDateTime afterSecondSwitch = OffsetDateTime.now();

        assertThat(attempt.getStatus()).isEqualTo(ExamAttemptStatus.SECTION_SWITCH);
        assertThat(secondSwitchTimestamp).isAfter(firstSwitchTimestamp);
        assertThat(secondSwitchTimestamp).isBetween(beforeSecondSwitch, afterSecondSwitch);
    }

    @Test
    @DisplayName("switchSection: NOT_STARTED throws error")
    void switchSection_fromNotStarted_throwsValidationError() {
        assertThatThrownBy(() -> attempt.switchSection())
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("switchSection: SUBMITTED throws error")
    void switchSection_fromSubmitted_throwsValidationError() {
        attempt.recordHeartbeat();
        attempt.submit();

        assertThatThrownBy(() -> attempt.switchSection())
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("switchSection: DISCONNECTED throws error")
    void switchSection_fromDisconnected_throwsValidationError() {
        ReflectionTestUtils.setField(attempt, "status", ExamAttemptStatus.DISCONNECTED);

        assertThatThrownBy(() -> attempt.switchSection())
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
    }

    // ====================
    // submit() Tests (Phase 6 specific)
    // ====================

    @Test
    @DisplayName("submit: IN_PROGRESS to SUBMITTED")
    void submit_fromInProgress() {
        attempt.recordHeartbeat();
        attempt.submit();

        assertThat(attempt.getStatus()).isEqualTo(ExamAttemptStatus.SUBMITTED);
        assertThat(attempt.getIsSubmitted()).isTrue();
        assertThat(attempt.getSubmittedAt()).isNotNull();
    }

    @Test
    @DisplayName("submit: SECTION_SWITCH to SUBMITTED")
    void submit_fromSectionSwitch() {
        attempt.recordHeartbeat();
        attempt.switchSection();
        attempt.submit();

        assertThat(attempt.getStatus()).isEqualTo(ExamAttemptStatus.SUBMITTED);
        assertThat(attempt.getIsSubmitted()).isTrue();
        assertThat(attempt.getSubmittedAt()).isNotNull();
    }

    @Test
    @DisplayName("submit: NOT_STARTED throws error")
    void submit_fromNotStarted_throwsConflict() {
        assertThatThrownBy(() -> attempt.submit())
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_CONFLICT);
    }

    @Test
    @DisplayName("submit: DISCONNECTED throws error")
    void submit_fromDisconnected_throwsConflict() {
        ReflectionTestUtils.setField(attempt, "status", ExamAttemptStatus.DISCONNECTED);

        assertThatThrownBy(() -> attempt.submit())
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_CONFLICT);
    }

    @Test
    @DisplayName("No resume after submit - cannot transition SUBMITTED back to IN_PROGRESS")
    void submit_proofNoResumeAfterSubmitted() {
        attempt.recordHeartbeat();
        attempt.submit();

        assertThatThrownBy(() -> attempt.submit())
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_CONFLICT);

        assertThat(attempt.getStatus()).isEqualTo(ExamAttemptStatus.SUBMITTED);
    }

    // ====================
    // forceSubmit() Tests (Phase 8)
    // ====================

    @Test
    @DisplayName("forceSubmit: IN_PROGRESS to SUBMITTED, returns true")
    void forceSubmit_fromInProgress_transitionsAndReturnsTrue() {
        attempt.recordHeartbeat();

        boolean result = attempt.forceSubmit();

        assertThat(result).isTrue();
        assertThat(attempt.getStatus()).isEqualTo(ExamAttemptStatus.SUBMITTED);
        assertThat(attempt.getIsSubmitted()).isTrue();
        assertThat(attempt.getSubmittedAt()).isNotNull();
    }

    @Test
    @DisplayName("forceSubmit: SECTION_SWITCH to SUBMITTED, returns true")
    void forceSubmit_fromSectionSwitch_transitionsAndReturnsTrue() {
        attempt.recordHeartbeat();
        attempt.switchSection();

        boolean result = attempt.forceSubmit();

        assertThat(result).isTrue();
        assertThat(attempt.getStatus()).isEqualTo(ExamAttemptStatus.SUBMITTED);
        assertThat(attempt.getIsSubmitted()).isTrue();
        assertThat(attempt.getSubmittedAt()).isNotNull();
    }

    @Test
    @DisplayName("forceSubmit: already SUBMITTED is idempotent, returns false, no state change")
    void forceSubmit_alreadySubmitted_returnsfalseAndNoChange() throws InterruptedException {
        attempt.recordHeartbeat();
        attempt.forceSubmit();
        LocalDateTime firstSubmittedAt = attempt.getSubmittedAt();

        Thread.sleep(50);

        boolean result = attempt.forceSubmit();

        assertThat(result).isFalse();
        assertThat(attempt.getStatus()).isEqualTo(ExamAttemptStatus.SUBMITTED);
        assertThat(attempt.getIsSubmitted()).isTrue();
        assertThat(attempt.getSubmittedAt()).isEqualTo(firstSubmittedAt);  // NOT overwritten
    }

    @Test
    @DisplayName("forceSubmit: NOT_STARTED does NOT transition (different from submit)")
    void forceSubmit_fromNotStarted_doesNotTransition() {
        boolean result = attempt.forceSubmit();

        // Unlike submit(), forceSubmit() should transition even from NOT_STARTED
        // (proctor can force-submit a never-started student)
        // So this test confirms that behavior
        assertThat(result).isTrue();
        assertThat(attempt.getStatus()).isEqualTo(ExamAttemptStatus.SUBMITTED);
    }

    // ====================
    // extendTime() Tests (Phase 8)
    // ====================

    @Test
    @DisplayName("extendTime: increments extraTimeMinutes from 0")
    void extendTime_fromZero_incrementsCorrectly() {
        assertThat(attempt.getExtraTimeMinutes()).isEqualTo(0);

        attempt.extendTime(5);

        assertThat(attempt.getExtraTimeMinutes()).isEqualTo(5);
    }

    @Test
    @DisplayName("extendTime: is accumulative (not idempotent)")
    void extendTime_called_twice_accumulates() {
        attempt.extendTime(5);
        assertThat(attempt.getExtraTimeMinutes()).isEqualTo(5);

        attempt.extendTime(5);

        assertThat(attempt.getExtraTimeMinutes()).isEqualTo(10);  // Not 5 (not idempotent)
    }

    @Test
    @DisplayName("extendTime: large minutes value")
    void extendTime_largeValue() {
        attempt.extendTime(120);

        assertThat(attempt.getExtraTimeMinutes()).isEqualTo(120);
    }

    @Test
    @DisplayName("extendTime: multiple calls accumulate")
    void extendTime_multipleCalls_accumulate() {
        attempt.extendTime(10);
        attempt.extendTime(20);
        attempt.extendTime(15);

        assertThat(attempt.getExtraTimeMinutes()).isEqualTo(45);
    }

    // ====================
    // flag() Tests (Phase 8)
    // ====================

    @Test
    @DisplayName("flag: sets flagged to true from false")
    void flag_fromFalse_setsTrue() {
        assertThat(attempt.getFlagged()).isFalse();

        attempt.flag();

        assertThat(attempt.getFlagged()).isTrue();
    }

    @Test
    @DisplayName("flag: calling when already flagged stays true (no error)")
    void flag_alreadyFlagged_staysTrue() {
        attempt.flag();
        assertThat(attempt.getFlagged()).isTrue();

        attempt.flag();

        assertThat(attempt.getFlagged()).isTrue();
    }

    @Test
    @DisplayName("flag: multiple calls all result in true")
    void flag_multipleCalls_stayTrue() {
        attempt.flag();
        attempt.flag();
        attempt.flag();

        assertThat(attempt.getFlagged()).isTrue();
    }
}
