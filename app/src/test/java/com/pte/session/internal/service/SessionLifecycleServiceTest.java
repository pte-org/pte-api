package com.pte.session.internal.service;

import com.pte.assessment.AssessmentService;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.session.domain.ExamPolicy;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.AnswerIntegrityLevel;
import com.pte.session.domain.enums.ExamMode;
import com.pte.session.domain.enums.LockdownMode;
import com.pte.session.domain.enums.ReplayPolicyType;
import com.pte.session.dto.response.ExamPolicyResponse;
import com.pte.session.internal.dto.request.CreateSessionRequest;
import com.pte.session.internal.dto.request.PatchExamPolicyRequest;
import com.pte.session.internal.dto.response.SessionResponse;
import com.pte.session.internal.mapper.SessionMapper;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Ported from services/scheduling's own SessionServiceLockdownTest (plans/modular-monolith
 * Phase 06) — same LockdownMode-propagation coverage, with
 * {@code SnapshotRefService} replaced by an in-process {@link AssessmentService}
 * call (no cache/ref table in the monolith).
 */
@ExtendWith(MockitoExtension.class)
class SessionLifecycleServiceTest {

    @Mock
    private ExamSessionRepository sessionRepository;

    @Mock
    private AssessmentService assessmentService;

    private SessionLifecycleService sessionLifecycleService;

    private UUID tenantId;
    private CurrentUser hostAdmin;

    @BeforeEach
    void setUp() {
        sessionLifecycleService = new SessionLifecycleService(sessionRepository, assessmentService);
        tenantId = UUID.randomUUID();
        hostAdmin = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
    }

    private SnapshotResponse snapshotSummary() {
        return new SnapshotResponse(UUID.randomUUID(), "Mock Test A", 1, UUID.randomUUID(), UUID.randomUUID(), 1, null, List.of());
    }

    @Test
    void create_setsLockdownModeToNone_whenExamModeIsPractice() {
        when(assessmentService.generateAndPublish(any(), any(), any())).thenReturn(snapshotSummary());
        when(sessionRepository.save(any())).thenAnswer(invocation -> {
            ExamSession s = invocation.getArgument(0);
            s.setPublicId(UUID.randomUUID());
            return s;
        });

        SessionResponse response = sessionLifecycleService.create(
                new CreateSessionRequest(
                        "Practice Session",
                        Set.of("SPEAKING"),
                        Instant.now().plusSeconds(3600),
                        Instant.now().plusSeconds(7200),
                        ExamMode.PRACTICE,
                        null,
                        null),
                hostAdmin);

        assertThat(response.policy().lockdownMode()).isEqualTo("NONE");
    }

    @Test
    void create_setsLockdownModeToStrict_whenExamModeIsRealExam() {
        when(assessmentService.generateAndPublish(any(), any(), any())).thenReturn(snapshotSummary());
        when(sessionRepository.save(any())).thenAnswer(invocation -> {
            ExamSession s = invocation.getArgument(0);
            s.setPublicId(UUID.randomUUID());
            return s;
        });

        SessionResponse response = sessionLifecycleService.create(
                new CreateSessionRequest(
                        "Real Exam Session",
                        Set.of("SPEAKING"),
                        Instant.now().plusSeconds(3600),
                        Instant.now().plusSeconds(7200),
                        ExamMode.REAL_EXAM,
                        null,
                        null),
                hostAdmin);

        assertThat(response.policy().lockdownMode()).isEqualTo("STRICT");
    }

    @Test
    void create_withTeacherOverride_strictOnPractice_rejected() {
        when(assessmentService.generateAndPublish(any(), any(), any())).thenReturn(snapshotSummary());

        assertThatThrownBy(() -> sessionLifecycleService.create(
                new CreateSessionRequest(
                        "Invalid Combo",
                        Set.of("SPEAKING"),
                        Instant.now().plusSeconds(3600),
                        Instant.now().plusSeconds(7200),
                        ExamMode.PRACTICE,
                        LockdownMode.STRICT,
                        null),
                hostAdmin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("STRICT")
                .hasMessageContaining("PRACTICE");
    }

    @Test
    void create_delegatesToAssessmentGenerateAndPublish_usesReturnedSnapshotPublicId() {
        UUID canonicalSnapshotId = UUID.randomUUID();
        Set<String> skills = Set.of("SPEAKING", "WRITING");
        when(assessmentService.generateAndPublish("Session", skills, hostAdmin))
                .thenReturn(new SnapshotResponse(canonicalSnapshotId, "Mock Test A", 1, UUID.randomUUID(), UUID.randomUUID(), 1, null, List.of()));
        when(sessionRepository.save(any())).thenAnswer(invocation -> {
            ExamSession s = invocation.getArgument(0);
            s.setPublicId(UUID.randomUUID());
            return s;
        });

        SessionResponse response = sessionLifecycleService.create(
                new CreateSessionRequest(
                        "Session",
                        skills,
                        Instant.now().plusSeconds(3600),
                        Instant.now().plusSeconds(7200),
                        ExamMode.MOCK_TEST,
                        null,
                        null),
                hostAdmin);

        assertThat(response.snapshotPublicId()).isEqualTo(canonicalSnapshotId);
    }

    @Test
    void create_generationFails_noSessionSaved() {
        when(assessmentService.generateAndPublish(any(), any(), any()))
                .thenThrow(new RuntimeException("insufficient question bank"));

        assertThatThrownBy(() -> sessionLifecycleService.create(
                new CreateSessionRequest(
                        "Session",
                        Set.of("SPEAKING"),
                        Instant.now().plusSeconds(3600),
                        Instant.now().plusSeconds(7200),
                        ExamMode.MOCK_TEST,
                        null,
                        null),
                hostAdmin))
                .isInstanceOf(RuntimeException.class);

        verifyNoInteractions(sessionRepository);
    }

    @Test
    void create_invalidSkillCount_rejectedByValidation() {
        jakarta.validation.Validator validator = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();

        CreateSessionRequest empty = new CreateSessionRequest("Session", Set.of(),
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), ExamMode.MOCK_TEST, null, null);
        CreateSessionRequest tooMany = new CreateSessionRequest("Session",
                Set.of("SPEAKING", "WRITING", "READING", "LISTENING", "EXTRA"),
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), ExamMode.MOCK_TEST, null, null);

        assertThat(validator.validate(empty)).isNotEmpty();
        assertThat(validator.validate(tooMany)).isNotEmpty();
    }

    @Test
    void patchPolicy_updatesLockdownMode_toStrict() {
        UUID sessionPublicId = UUID.randomUUID();
        ExamSession session = existingSession(sessionPublicId);
        when(sessionRepository.findWithLockByPublicIdAndTenantId(sessionPublicId, tenantId))
                .thenReturn(Optional.of(session));

        ExamPolicyResponse response = sessionLifecycleService.patchPolicy(
                sessionPublicId,
                new PatchExamPolicyRequest(null, null, null, null, null, LockdownMode.STRICT),
                hostAdmin);

        assertThat(response.lockdownMode()).isEqualTo("STRICT");
    }

    @Test
    void patchPolicy_doesNotChangeLockdownMode_whenRequestLockdownModeIsNull() {
        UUID sessionPublicId = UUID.randomUUID();
        ExamSession session = existingSession(sessionPublicId);
        session.getPolicy().setLockdownMode(LockdownMode.STRICT);
        when(sessionRepository.findWithLockByPublicIdAndTenantId(sessionPublicId, tenantId))
                .thenReturn(Optional.of(session));

        ExamPolicyResponse response = sessionLifecycleService.patchPolicy(
                sessionPublicId,
                new PatchExamPolicyRequest(ReplayPolicyType.LIMITED, 1, null, null, null, null),
                hostAdmin);

        assertThat(response.lockdownMode()).isEqualTo("STRICT");
    }

    @Test
    void toPolicy_emitsLockdownModeAsUppercaseString() {
        ExamPolicy policy = ExamPolicy.realExamDefault();

        ExamPolicyResponse response = SessionMapper.toPolicy(policy);

        assertThat(response.lockdownMode()).isEqualTo("STRICT");
        assertThat(response.lockdownMode()).matches("^[A-Z_]+$");
    }

    @Test
    void toPolicy_throwsOnIncompletePolicy_notSilentFallback() {
        ExamPolicy incomplete = new ExamPolicy();

        assertThatThrownBy(() -> SessionMapper.toPolicy(incomplete))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("incomplete");
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
        session.setPolicy(ExamPolicy.mockTestDefault());
        return session;
    }
}
