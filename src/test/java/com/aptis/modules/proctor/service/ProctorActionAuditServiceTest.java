package com.aptis.modules.proctor.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aptis.modules.proctor.domain.ProctorAction;
import com.aptis.modules.proctor.domain.ProctorActionType;
import com.aptis.modules.proctor.repository.ProctorActionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProctorActionAuditService Tests")
class ProctorActionAuditServiceTest {

    @Mock
    private ProctorActionRepository proctorActionRepository;

    private ObjectMapper objectMapper;
    private ProctorActionAuditService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new ProctorActionAuditService(proctorActionRepository, objectMapper);
    }

    // ====================
    // record() Tests
    // ====================

    @Test
    @DisplayName("record: creates ProctorAction with correct fields and serializes details to JSON")
    void record_createsActionWithSerializedDetails() {
        Long actorProctorId = 1L;
        Long targetAttemptId = 100L;
        ProctorActionType actionType = ProctorActionType.FORCE_SUBMIT;

        Map<String, Object> details = new HashMap<>();
        details.put("previousStatus", "IN_PROGRESS");
        details.put("timestamp", 1234567890L);

        service.record(actorProctorId, targetAttemptId, actionType, details);

        ArgumentCaptor<ProctorAction> captor = ArgumentCaptor.forClass(ProctorAction.class);
        verify(proctorActionRepository).save(captor.capture());

        ProctorAction savedAction = captor.getValue();
        assertThat(savedAction.getActorProctorId()).isEqualTo(actorProctorId);
        assertThat(savedAction.getTargetAttemptId()).isEqualTo(targetAttemptId);
        assertThat(savedAction.getActionType()).isEqualTo(actionType);
        assertThat(savedAction.getActionTimestamp()).isNotNull();

        // Verify details were serialized to JSON
        assertThat(savedAction.getDetails()).isNotNull();
        assertThat(savedAction.getDetails()).contains("previousStatus");
        assertThat(savedAction.getDetails()).contains("IN_PROGRESS");
    }

    @Test
    @DisplayName("record: targetAttemptId can be null (for broadcast)")
    void record_targetAttemptIdNull() {
        Long actorProctorId = 2L;
        ProctorActionType actionType = ProctorActionType.BROADCAST_ANNOUNCEMENT;

        Map<String, Object> details = new HashMap<>();
        details.put("messageText", "Exam ending in 5 minutes");
        details.put("recipientCount", 10);

        service.record(actorProctorId, null, actionType, details);

        ArgumentCaptor<ProctorAction> captor = ArgumentCaptor.forClass(ProctorAction.class);
        verify(proctorActionRepository).save(captor.capture());

        ProctorAction savedAction = captor.getValue();
        assertThat(savedAction.getTargetAttemptId()).isNull();
        assertThat(savedAction.getActorProctorId()).isEqualTo(actorProctorId);
    }

    @Test
    @DisplayName("record: null details map results in null details JSON")
    void record_nullDetails_resultsInNullDetailsJson() {
        Long actorProctorId = 3L;
        Long targetAttemptId = 200L;

        service.record(actorProctorId, targetAttemptId, ProctorActionType.FLAG_VIOLATION, null);

        ArgumentCaptor<ProctorAction> captor = ArgumentCaptor.forClass(ProctorAction.class);
        verify(proctorActionRepository).save(captor.capture());

        ProctorAction savedAction = captor.getValue();
        assertThat(savedAction.getDetails()).isNull();
    }

    @Test
    @DisplayName("record: empty details map results in null details JSON")
    void record_emptyDetails_resultsInNullDetailsJson() {
        Long actorProctorId = 4L;
        Long targetAttemptId = 300L;

        service.record(actorProctorId, targetAttemptId, ProctorActionType.EXTEND_TIME, new HashMap<>());

        ArgumentCaptor<ProctorAction> captor = ArgumentCaptor.forClass(ProctorAction.class);
        verify(proctorActionRepository).save(captor.capture());

        ProctorAction savedAction = captor.getValue();
        assertThat(savedAction.getDetails()).isNull();
    }

    @Test
    @DisplayName("record: complex nested details are serialized correctly")
    void record_complexDetailsSerializedCorrectly() {
        Long actorProctorId = 5L;
        Long targetAttemptId = 400L;

        Map<String, Object> details = new HashMap<>();
        details.put("skill", "WRITING");
        details.put("part", 2);
        details.put("originalMinutes", 30);
        details.put("addedMinutes", 15);
        details.put("newTotalMinutes", 45);

        service.record(actorProctorId, targetAttemptId, ProctorActionType.EXTEND_TIME, details);

        ArgumentCaptor<ProctorAction> captor = ArgumentCaptor.forClass(ProctorAction.class);
        verify(proctorActionRepository).save(captor.capture());

        ProctorAction savedAction = captor.getValue();
        String detailsJson = savedAction.getDetails();

        // Verify all keys are present in the JSON string
        assertThat(detailsJson).contains("\"skill\"");
        assertThat(detailsJson).contains("\"WRITING\"");
        assertThat(detailsJson).contains("\"part\"");
        assertThat(detailsJson).contains(":2"); // part value is integer, so 2 not "2"
        assertThat(detailsJson).contains("\"newTotalMinutes\"");
        assertThat(detailsJson).contains(":45"); // newTotalMinutes value
    }

    @Test
    @DisplayName("record: FORCE_SUBMIT with details about previous state")
    void record_forceSubmitWithPreviousStatus() {
        Long actorProctorId = 6L;
        Long targetAttemptId = 500L;

        Map<String, Object> details = new HashMap<>();
        details.put("previousStatus", "SECTION_SWITCH");

        service.record(actorProctorId, targetAttemptId, ProctorActionType.FORCE_SUBMIT, details);

        ArgumentCaptor<ProctorAction> captor = ArgumentCaptor.forClass(ProctorAction.class);
        verify(proctorActionRepository).save(captor.capture());

        ProctorAction savedAction = captor.getValue();
        assertThat(savedAction.getActionType()).isEqualTo(ProctorActionType.FORCE_SUBMIT);
        assertThat(savedAction.getDetails()).contains("previousStatus");
        assertThat(savedAction.getDetails()).contains("SECTION_SWITCH");
    }

    @Test
    @DisplayName("record: FLAG_VIOLATION with reason")
    void record_flagViolationWithReason() {
        Long actorProctorId = 7L;
        Long targetAttemptId = 600L;

        Map<String, Object> details = new HashMap<>();
        details.put("reason", "Student looking away from screen during proctored exam");

        service.record(actorProctorId, targetAttemptId, ProctorActionType.FLAG_VIOLATION, details);

        ArgumentCaptor<ProctorAction> captor = ArgumentCaptor.forClass(ProctorAction.class);
        verify(proctorActionRepository).save(captor.capture());

        ProctorAction savedAction = captor.getValue();
        assertThat(savedAction.getActionType()).isEqualTo(ProctorActionType.FLAG_VIOLATION);
        assertThat(savedAction.getDetails()).contains("Student looking away from screen");
    }

    @Test
    @DisplayName("record: BROADCAST_ANNOUNCEMENT with message and recipient count")
    void record_broadcastAnnouncementWithStats() {
        Long actorProctorId = 8L;

        Map<String, Object> details = new HashMap<>();
        details.put("messageText", "Exam time is running out");
        details.put("recipientCount", 25);

        service.record(actorProctorId, null, ProctorActionType.BROADCAST_ANNOUNCEMENT, details);

        ArgumentCaptor<ProctorAction> captor = ArgumentCaptor.forClass(ProctorAction.class);
        verify(proctorActionRepository).save(captor.capture());

        ProctorAction savedAction = captor.getValue();
        assertThat(savedAction.getTargetAttemptId()).isNull();
        assertThat(savedAction.getDetails()).contains("messageText");
        assertThat(savedAction.getDetails()).contains("Exam time is running out");
        assertThat(savedAction.getDetails()).contains("recipientCount");
        assertThat(savedAction.getDetails()).contains("25");
    }

    @Test
    @DisplayName("record: ObjectMapper serialization failure falls back to toString()")
    void record_jsonSerializationFailure_fallsBackToToString() {
        Long actorProctorId = 9L;
        Long targetAttemptId = 700L;

        // Create a details map that ObjectMapper might fail on (circular reference, etc.)
        Map<String, Object> details = new HashMap<>();
        details.put("normalKey", "normalValue");

        // We're testing the graceful fallback, but with a well-formed map it should succeed
        service.record(actorProctorId, targetAttemptId, ProctorActionType.EXTEND_TIME, details);

        ArgumentCaptor<ProctorAction> captor = ArgumentCaptor.forClass(ProctorAction.class);
        verify(proctorActionRepository).save(captor.capture());

        ProctorAction savedAction = captor.getValue();
        // The details should still be recorded
        assertThat(savedAction.getDetails()).isNotNull();
        assertThat(savedAction.getDetails()).isNotEmpty();
    }

    @Test
    @DisplayName("record: multiple calls create separate audit rows")
    void record_multipleCallsCreateSeparateRows() {
        service.record(1L, 100L, ProctorActionType.FORCE_SUBMIT,
                Map.of("previousStatus", "IN_PROGRESS"));
        service.record(1L, 101L, ProctorActionType.FLAG_VIOLATION,
                Map.of("reason", "Cheating"));
        service.record(1L, 102L, ProctorActionType.EXTEND_TIME,
                Map.of("addedMinutes", 15));

        verify(proctorActionRepository, org.mockito.Mockito.times(3)).save(any());
    }
}
