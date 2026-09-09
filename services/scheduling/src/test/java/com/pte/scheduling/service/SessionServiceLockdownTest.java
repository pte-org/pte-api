package com.pte.scheduling.service;

import com.pte.common.security.CurrentUser;
import com.pte.scheduling.domain.ExamPolicy;
import com.pte.scheduling.domain.ExamSession;
import com.pte.scheduling.domain.SnapshotRef;
import com.pte.scheduling.domain.enums.ExamMode;
import com.pte.scheduling.domain.enums.LockdownMode;
import com.pte.scheduling.dto.request.CreateSessionRequest;
import com.pte.scheduling.dto.request.PatchExamPolicyRequest;
import com.pte.scheduling.dto.response.ExamPolicyResponse;
import com.pte.scheduling.dto.response.SessionResponse;
import com.pte.scheduling.mapper.SessionMapper;
import com.pte.scheduling.messaging.outbox.OutboxWriter;
import com.pte.scheduling.repository.ExamSessionRepository;
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
import static org.mockito.Mockito.when;

/**
 * Tests for LockdownMode propagation through SessionService and SessionMapper.
 * Covers the lockdown feature (Phase 01-02): ExamPolicy.lockdownMode field,
 * PATCH /sessions/{id}/policy with LockdownMode, and the SessionMapper.toPolicy
 * wire-format contract that Flutter's LockdownMode.fromString depends on.
 */
@ExtendWith(MockitoExtension.class)
class SessionServiceLockdownTest {

    @Mock
    private ExamSessionRepository sessionRepository;

    @Mock
    private SnapshotRefService snapshotRefService;

    @Mock
    private OutboxWriter outboxWriter;

    private SessionService sessionService;

    private UUID tenantId;
    private CurrentUser hostAdmin;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(sessionRepository, snapshotRefService, outboxWriter);
        tenantId = UUID.randomUUID();
        hostAdmin = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
    }

    // --- SessionService.create() lockdown mode derivation ---

    @Test
    void create_setsLockdownModeToNone_whenExamModeIsPractice() {
        when(snapshotRefService.resolve(any())).thenReturn(snapshotRef());
        when(sessionRepository.save(any())).thenAnswer(invocation -> {
            ExamSession s = invocation.getArgument(0);
            s.setPublicId(UUID.randomUUID());
            return s;
        });

        SessionResponse response = sessionService.create(
                new CreateSessionRequest(
                        "Practice Session",
                        UUID.randomUUID(),
                        Instant.now().plusSeconds(3600),
                        Instant.now().plusSeconds(7200),
                        ExamMode.PRACTICE,
                        null),  // null = use default (NONE)
                hostAdmin);

        assertThat(response.policy().lockdownMode()).isEqualTo("NONE");
    }

    @Test
    void create_setsLockdownModeToStandard_whenExamModeIsMockTest() {
        when(snapshotRefService.resolve(any())).thenReturn(snapshotRef());
        when(sessionRepository.save(any())).thenAnswer(invocation -> {
            ExamSession s = invocation.getArgument(0);
            s.setPublicId(UUID.randomUUID());
            return s;
        });

        SessionResponse response = sessionService.create(
                new CreateSessionRequest(
                        "Mock Test Session",
                        UUID.randomUUID(),
                        Instant.now().plusSeconds(3600),
                        Instant.now().plusSeconds(7200),
                        ExamMode.MOCK_TEST,
                        null),  // null = use default (STANDARD)
                hostAdmin);

        assertThat(response.policy().lockdownMode()).isEqualTo("STANDARD");
    }

    @Test
    void create_setsLockdownModeToStrict_whenExamModeIsRealExam() {
        when(snapshotRefService.resolve(any())).thenReturn(snapshotRef());
        when(sessionRepository.save(any())).thenAnswer(invocation -> {
            ExamSession s = invocation.getArgument(0);
            s.setPublicId(UUID.randomUUID());
            return s;
        });

        SessionResponse response = sessionService.create(
                new CreateSessionRequest(
                        "Real Exam Session",
                        UUID.randomUUID(),
                        Instant.now().plusSeconds(3600),
                        Instant.now().plusSeconds(7200),
                        ExamMode.REAL_EXAM,
                        null),  // null = use default (STRICT)
                hostAdmin);

        assertThat(response.policy().lockdownMode()).isEqualTo("STRICT");
    }

    // --- Teacher override tests ---

    @Test
    void create_withTeacherOverride_lockdownModeOverridesDefault() {
        // PRACTICE normally defaults to NONE; teacher overrides to STANDARD.
        when(snapshotRefService.resolve(any())).thenReturn(snapshotRef());
        when(sessionRepository.save(any())).thenAnswer(invocation -> {
            ExamSession s = invocation.getArgument(0);
            s.setPublicId(UUID.randomUUID());
            return s;
        });

        SessionResponse response = sessionService.create(
                new CreateSessionRequest(
                        "Override Test",
                        UUID.randomUUID(),
                        Instant.now().plusSeconds(3600),
                        Instant.now().plusSeconds(7200),
                        ExamMode.PRACTICE,
                        LockdownMode.STANDARD),  // override NONE → STANDARD
                hostAdmin);

        assertThat(response.policy().lockdownMode()).isEqualTo("STANDARD");
    }

    @Test
    void create_withTeacherOverride_strictOnRealExam_usesStrict() {
        // REAL_EXAM defaults to STRICT; teacher confirms with explicit STRICT.
        when(snapshotRefService.resolve(any())).thenReturn(snapshotRef());
        when(sessionRepository.save(any())).thenAnswer(invocation -> {
            ExamSession s = invocation.getArgument(0);
            s.setPublicId(UUID.randomUUID());
            return s;
        });

        SessionResponse response = sessionService.create(
                new CreateSessionRequest(
                        "Explicit Strict",
                        UUID.randomUUID(),
                        Instant.now().plusSeconds(3600),
                        Instant.now().plusSeconds(7200),
                        ExamMode.REAL_EXAM,
                        LockdownMode.STRICT),
                hostAdmin);

        assertThat(response.policy().lockdownMode()).isEqualTo("STRICT");
    }

    @Test
    void create_withTeacherOverride_strictOnPractice_rejected() {
        // STRICT + PRACTICE is invalid — validation throws after snapshot resolve.
        when(snapshotRefService.resolve(any())).thenReturn(snapshotRef());

        assertThatThrownBy(() -> sessionService.create(
                new CreateSessionRequest(
                        "Invalid Combo",
                        UUID.randomUUID(),
                        Instant.now().plusSeconds(3600),
                        Instant.now().plusSeconds(7200),
                        ExamMode.PRACTICE,
                        LockdownMode.STRICT),
                hostAdmin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("STRICT")
                .hasMessageContaining("PRACTICE");
    }

    // --- SessionService.patchPolicy() lockdown mode ---

    @Test
    void patchPolicy_updatesLockdownMode_toStrict() {
        UUID sessionPublicId = UUID.randomUUID();
        ExamSession session = existingSession(sessionPublicId);
        // Default mock-test policy has STANDARD lockdownMode.
        when(sessionRepository.findWithLockByPublicIdAndTenantId(sessionPublicId, tenantId))
                .thenReturn(java.util.Optional.of(session));

        ExamPolicyResponse response = sessionService.patchPolicy(
                sessionPublicId,
                new PatchExamPolicyRequest(null, null, null, null, null, LockdownMode.STRICT),
                hostAdmin);

        assertThat(response.lockdownMode()).isEqualTo("STRICT");
    }

    @Test
    void patchPolicy_updatesLockdownMode_toNone() {
        UUID sessionPublicId = UUID.randomUUID();
        ExamSession session = existingSession(sessionPublicId);
        // Set STRICT first.
        session.getPolicy().setLockdownMode(LockdownMode.STRICT);
        when(sessionRepository.findWithLockByPublicIdAndTenantId(sessionPublicId, tenantId))
                .thenReturn(java.util.Optional.of(session));

        ExamPolicyResponse response = sessionService.patchPolicy(
                sessionPublicId,
                new PatchExamPolicyRequest(null, null, null, null, null, LockdownMode.NONE),
                hostAdmin);

        assertThat(response.lockdownMode()).isEqualTo("NONE");
    }

    @Test
    void patchPolicy_doesNotChangeLockdownMode_whenRequestLockdownModeIsNull() {
        UUID sessionPublicId = UUID.randomUUID();
        ExamSession session = existingSession(sessionPublicId);
        session.getPolicy().setLockdownMode(LockdownMode.STRICT);
        when(sessionRepository.findWithLockByPublicIdAndTenantId(sessionPublicId, tenantId))
                .thenReturn(java.util.Optional.of(session));

        ExamPolicyResponse response = sessionService.patchPolicy(
                sessionPublicId,
                // No lockdownMode in the patch request — only replayPolicyType changed.
                new PatchExamPolicyRequest(
                        com.pte.scheduling.domain.enums.ReplayPolicyType.LIMITED,
                        1,
                        null, null, null, null),
                hostAdmin);

        // Should remain STRICT (unchanged).
        assertThat(response.lockdownMode()).isEqualTo("STRICT");
    }

    // --- SessionMapper.toPolicy() wire-format contract ---

    @Test
    void toPolicy_emitsLockdownModeAsUppercaseString_matchingFlutterContract() {
        ExamPolicy policy = ExamPolicy.realExamDefault(); // Sets STRICT.

        ExamPolicyResponse response = SessionMapper.toPolicy(policy);

        // Flutter's LockdownMode.fromString normalizes to lowercase via
        // .toLowerCase() then compares against enum.name (also lowercase).
        // The wire value must be uppercase so the exact string on the wire
        // is "STRICT" / "STANDARD" / "NONE" — Flutter receives this and
        // normalizes it before matching.
        assertThat(response.lockdownMode()).isEqualTo("STRICT");
        assertThat(response.lockdownMode()).matches("^[A-Z_]+$");
    }

    @Test
    void toPolicy_emitsNullLockdownMode_whenPolicyLockdownModeIsNull() {
        ExamPolicy policy = new ExamPolicy();
        policy.setReplayPolicyType(com.pte.scheduling.domain.enums.ReplayPolicyType.UNLIMITED);
        policy.setReplayPolicyLimit(null);
        policy.setDeviceCheckRequired(false);
        policy.setProctorRequired(false);
        policy.setAnswerIntegrityLevel(com.pte.scheduling.domain.enums.AnswerIntegrityLevel.STANDARD);
        // lockdownMode intentionally left null.

        ExamPolicyResponse response = SessionMapper.toPolicy(policy);

        // Flutter treats null lockdownMode as LockdownMode.none.
        assertThat(response.lockdownMode()).isNull();
    }

    @Test
    void toPolicy_throwsOnIncompletePolicy_notSilentFallback() {
        ExamPolicy incomplete = new ExamPolicy();
        // replayPolicyType null => @PostLoad backfill won't fire.

        assertThatThrownBy(() -> SessionMapper.toPolicy(incomplete))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("incomplete");
    }

    // --- Helpers ---

    private SnapshotRef snapshotRef() {
        SnapshotRef ref = new SnapshotRef();
        ref.setSnapshotPublicId(UUID.randomUUID());
        return ref;
    }

    private ExamSession existingSession(UUID publicId) {
        ExamSession session = new ExamSession();
        session.setId(1L);
        session.setPublicId(publicId);
        session.setTenantId(tenantId);
        session.setName("Test Session");
        session.setSnapshotPublicId(UUID.randomUUID());
        session.setOpensAt(Instant.now());
        session.setClosesAt(Instant.now().plusSeconds(3600));
        session.setPolicy(ExamPolicy.mockTestDefault()); // STANDARD lockdown.
        return session;
    }
}
