package com.pte.attempt.internal.service;

import com.pte.assessment.AssessmentService;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.PinnedExamSnapshot;
import com.pte.attempt.domain.PinnedItem;
import com.pte.attempt.internal.config.CapabilityProperties;
import com.pte.attempt.internal.dto.request.AttemptPreflightRequest;
import com.pte.attempt.internal.dto.request.ClientCapabilityManifest;
import com.pte.attempt.internal.dto.response.AttemptPreflightResponse;
import com.pte.attempt.internal.exception.ExamCapabilityException;
import com.pte.attempt.internal.exception.AttemptNotFoundException;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.itembank.TaskRuntimeContractConstants;
import com.pte.session.SessionService;
import com.pte.session.dto.response.EntitlementResponse;
import com.pte.session.dto.response.ExamPolicyResponse;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CapabilityNegotiationServiceTest {

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID SNAPSHOT_ID = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock
    private SessionService sessionService;
    @Mock
    private AssessmentService assessmentService;
    @Mock
    private AuditLogService auditLogService;

    private CapabilityProperties properties;
    private CapabilityNegotiationService service;
    private CurrentUser caller;

    @BeforeEach
    void setUp() {
        properties = new CapabilityProperties();
        service = new CapabilityNegotiationService(sessionService, assessmentService, properties, auditLogService);
        caller = new CurrentUser(STUDENT_ID, TENANT_ID, List.of("STUDENT"));
        lenient().when(sessionService.checkEntitlement(SESSION_ID, STUDENT_ID)).thenReturn(entitlement());
    }

    @Test
    void preflight_reportsMissingCapabilityWithoutReadingFullQuestionContent() {
        stubSummary(runtime("READ_ALOUD"));

        AttemptPreflightResponse response = service.preflight(
                new AttemptPreflightRequest(SESSION_ID,
                        new ClientCapabilityManifest(List.of("TEXT_INPUT"))), caller);

        assertThat(response.canStart()).isFalse();
        assertThat(response.missingCapabilities()).containsExactly("AUDIO_RECORDING");
        assertThat(response.code()).isEqualTo("EXAM_REQUIRES_APP_UPDATE");
        assertThat(response.userMessage()).contains("update");
        verify(assessmentService, never()).getFullContent(any());
        verify(auditLogService, never()).recordFailure(any(), any(), any(), any(), any());
    }

    @Test
    void preflight_rejectsCallerFromAnotherTenantBeforeReadingSnapshot() {
        CurrentUser foreignTenantCaller = new CurrentUser(STUDENT_ID, UUID.randomUUID(), List.of("STUDENT"));

        assertThatThrownBy(() -> service.preflight(
                new AttemptPreflightRequest(SESSION_ID, new ClientCapabilityManifest(List.of())),
                foreignTenantCaller)).isInstanceOf(AttemptNotFoundException.class);

        verify(assessmentService, never()).getSummary(any());
    }

    @Test
    void authorizeStart_returnsCanonicalAllowlistedFingerprint() {
        stubSummary(runtime("READ_ALOUD"));

        String fingerprint = service.authorizeStart(SESSION_ID, STUDENT_ID,
                new ClientCapabilityManifest(List.of("unknown", "AUDIO_RECORDING", "AUDIO_RECORDING")), caller);

        assertThat(fingerprint).isEqualTo("AUDIO_RECORDING@1");
    }

    @Test
    void authorizeStart_missingCapabilityAuditsAndFailsBeforeAttemptCreation() {
        stubSummary(runtime("READ_ALOUD"));

        assertThatThrownBy(() -> service.authorizeStart(SESSION_ID, STUDENT_ID,
                new ClientCapabilityManifest(List.of("TEXT_INPUT")), caller))
                .isInstanceOfSatisfying(ExamCapabilityException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("EXAM_REQUIRES_APP_UPDATE");
                    assertThat(ex.getData().toString()).contains("AUDIO_RECORDING");
                });

        verify(auditLogService).recordFailure(eq(caller), eq("EXAM_SNAPSHOT"), eq(SNAPSHOT_ID.toString()),
                eq("RUNTIME_CONTRACT_FAILURE"), any());
    }

    @Test
    void preflight_blocksExplicitlyIncompatibleHistoricalMapping() {
        TaskRuntimeProfileDescriptor runtime = runtime("READ_ALOUD");
        when(assessmentService.getSummary(SNAPSHOT_ID)).thenReturn(new SnapshotResponse(
                SNAPSHOT_ID, "snapshot", 1, UUID.randomUUID(), UUID.randomUUID(), 1, TENANT_ID,
                List.of(new SnapshotResponse.Item(0, "SPEAKING", runtime.taskTypeCode(), "title",
                        runtime.taskTypeCode(), runtime, TaskRuntimeContractConstants.MAPPING_VERSION_LEGACY,
                        TaskRuntimeContractConstants.MAPPING_STATUS_INCOMPATIBLE))));

        AttemptPreflightResponse response = service.preflight(
                new AttemptPreflightRequest(SESSION_ID, new ClientCapabilityManifest(List.of("AUDIO_RECORDING"))),
                caller);

        assertThat(response.canStart()).isFalse();
        assertThat(response.code()).isEqualTo("EXAM_CONFIGURATION_NOT_COMPATIBLE");
    }

    @Test
    void storedFingerprint_isCheckedBeforeReturningPinnedTask() {
        ExamAttempt attempt = attemptWithRuntime(runtime("READ_ALOUD"));

        assertThatThrownBy(() -> service.assertStoredCapabilities(attempt))
                .isInstanceOfSatisfying(ExamCapabilityException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("EXAM_REQUIRES_APP_UPDATE"));
    }

    @Test
    void legacySnapshot_canUseExplicitGrandfatheredMissingManifest() {
        ExamAttempt attempt = new ExamAttempt();
        PinnedExamSnapshot snapshot = new PinnedExamSnapshot();
        PinnedItem item = new PinnedItem();
        item.setSection("SPEAKING");
        item.setTaskType("READ_ALOUD");
        item.setTaskTypeCode("READ_ALOUD");
        snapshot.addItem(item);
        attempt.setPinnedSnapshot(snapshot);

        service.assertStoredCapabilities(attempt);
    }

    @Test
    void legacySnapshot_isBlockedWhenGrandfatherFlagIsDisabled() {
        properties.setAllowLegacyMissingManifest(false);
        service = new CapabilityNegotiationService(sessionService, assessmentService, properties, auditLogService);

        ExamAttempt attempt = new ExamAttempt();
        PinnedExamSnapshot snapshot = new PinnedExamSnapshot();
        PinnedItem item = new PinnedItem();
        item.setSection("SPEAKING");
        item.setTaskType("READ_ALOUD");
        item.setTaskTypeCode("READ_ALOUD");
        snapshot.addItem(item);
        attempt.setPinnedSnapshot(snapshot);

        assertThatThrownBy(() -> service.assertStoredCapabilities(attempt))
                .isInstanceOfSatisfying(ExamCapabilityException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("EXAM_CONFIGURATION_NOT_COMPATIBLE"));
    }

    private void stubSummary(TaskRuntimeProfileDescriptor runtime) {
        when(assessmentService.getSummary(SNAPSHOT_ID)).thenReturn(new SnapshotResponse(
                SNAPSHOT_ID, "snapshot", 1, UUID.randomUUID(), UUID.randomUUID(), 1, TENANT_ID,
                List.of(new SnapshotResponse.Item(0, "SPEAKING", runtime.taskTypeCode(), "title",
                        runtime.taskTypeCode(), runtime, TaskRuntimeContractConstants.MAPPING_VERSION_CANONICAL,
                        TaskRuntimeContractConstants.MAPPING_STATUS_RESOLVED_CANONICAL))));
    }

    private TaskRuntimeProfileDescriptor runtime(String taskType) {
        return TaskRuntimeProfileRegistry.descriptorFor(taskType);
    }

    private ExamAttempt attemptWithRuntime(TaskRuntimeProfileDescriptor runtime) {
        ExamAttempt attempt = new ExamAttempt();
        PinnedExamSnapshot snapshot = new PinnedExamSnapshot();
        PinnedItem item = new PinnedItem();
        item.setSection("SPEAKING");
        item.setTaskType(runtime.taskTypeCode());
        item.setTaskTypeCode(runtime.taskTypeCode());
        item.pinRuntimeProfile(runtime, "CANONICAL_V1", "RESOLVED_CANONICAL");
        snapshot.addItem(item);
        attempt.setPinnedSnapshot(snapshot);
        attempt.setCapabilityFingerprint("");
        return attempt;
    }

    private EntitlementResponse entitlement() {
        return new EntitlementResponse(SESSION_ID, SNAPSHOT_ID, TENANT_ID, Instant.now(),
                Instant.now().plusSeconds(3600),
                new ExamPolicyResponse("UNLIMITED", null, false, false, "STANDARD", "NONE"));
    }
}
