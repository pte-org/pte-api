package com.pte.scoring.internal.service;

import com.pte.assessment.AssessmentService;
import com.pte.assessment.dto.response.ExaminerQuestionPromptView;
import com.pte.identity.IdentityService;
import com.pte.identity.dto.response.ExaminerIdentityView;
import com.pte.media.MediaService;
import com.pte.media.dto.response.PresignedDownloadResponse;
import com.pte.scoring.domain.ExaminerAnswerScore;
import com.pte.scoring.domain.ExaminerAttemptAssignment;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.ScoringSessionState;
import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.domain.enums.ScoreSource;
import com.pte.scoring.domain.enums.ExaminerAnswerScoreStatus;
import com.pte.scoring.dto.request.SubmitExaminerScoreRequest;
import com.pte.scoring.dto.response.ExaminerAttemptDetailResponse;
import com.pte.scoring.dto.response.ExaminerQueueItemResponse;
import com.pte.scoring.internal.dto.response.AnswerPayloadKind;
import com.pte.scoring.internal.dto.response.DecodedAnswerPayload;
import com.pte.scoring.internal.exception.ExaminerScoreConflictException;
import com.pte.scoring.internal.exception.ExaminerWorkNotFoundException;
import com.pte.scoring.internal.exception.InvalidExaminerScoreException;
import com.pte.scoring.internal.repository.ExaminerAnswerScoreRepository;
import com.pte.scoring.internal.repository.ExaminerAttemptAssignmentRepository;
import com.pte.scoring.internal.repository.ExaminerWorkQueueRepository;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import com.pte.scoring.internal.repository.ScoringSessionStateRepository;
import com.pte.shared.security.CurrentUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExaminerScoringServiceTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

    @Mock
    private ExaminerWorkQueueRepository workQueueRepository;
    @Mock
    private ExaminerAttemptAssignmentRepository assignmentRepository;
    @Mock
    private ScoringAnswerRepository scoringAnswerRepository;
    @Mock
    private ExaminerAnswerScoreRepository examinerAnswerScoreRepository;
    @Mock
    private ScoringSessionStateRepository sessionStateRepository;
    @Mock
    private ScoringEligibilityQueryService eligibilityQueryService;
    @Mock
    private AnswerPayloadDecoder answerPayloadDecoder;
    @Mock
    private IdentityService identityService;
    @Mock
    private AssessmentService assessmentService;
    @Mock
    private MediaService mediaService;
    @Mock
    private EntityManager entityManager;

    private ExaminerScoringService service;

    @BeforeEach
    void setUp() {
        service = new ExaminerScoringService(workQueueRepository, assignmentRepository, scoringAnswerRepository,
                examinerAnswerScoreRepository, sessionStateRepository, eligibilityQueryService, answerPayloadDecoder,
                identityService, assessmentService, mediaService, entityManager);
    }

    @Test
    void nonExaminerRoleIsRejectedBeforeAnyWorkOrMediaLookup() {
        CurrentUser host = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST_ADMIN"));

        assertThatThrownBy(() -> service.getAttempt(UUID.randomUUID(), UUID.randomUUID(), host))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(identityService, workQueueRepository, scoringAnswerRepository, assessmentService,
                answerPayloadDecoder, mediaService);
    }

    @Test
    void inactiveExaminerIsRejectedBeforeAssignmentAndMediaLookup() {
        CurrentUser examiner = examiner();
        when(identityService.findActiveExaminers(examiner.tenantId(), List.of(examiner.userId())))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.getAttempt(UUID.randomUUID(), UUID.randomUUID(), examiner))
                .isInstanceOf(ExaminerWorkNotFoundException.class);

        verifyNoInteractions(workQueueRepository, scoringAnswerRepository, assessmentService,
                answerPayloadDecoder, mediaService);
    }

    @Test
    void unassignedAttemptIsRejectedBeforeReadingAnswersOrPresigningMedia() {
        CurrentUser examiner = examiner();
        UUID sessionId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        givenActiveExaminer(examiner);
        when(workQueueRepository.findOwnedAttempt(examiner.tenantId(), sessionId, attemptId, examiner.userId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAttempt(sessionId, attemptId, examiner))
                .isInstanceOf(ExaminerWorkNotFoundException.class);

        verify(workQueueRepository).findOwnedAttempt(examiner.tenantId(), sessionId, attemptId, examiner.userId());
        verifyNoInteractions(scoringAnswerRepository, assessmentService, answerPayloadDecoder, mediaService);
    }

    @Test
    void assignedAttemptIsTenantScopedAndMediaIsPresignedOnlyAfterAuthorizationAndPromptLookup() {
        CurrentUser examiner = examiner();
        UUID sessionId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();
        UUID answerMediaId = UUID.randomUUID();
        UUID promptMediaId = UUID.randomUUID();
        ScoringAnswer answer = answer(examiner.tenantId(), sessionId, attemptId, answerId);
        ExaminerAttemptAssignment assignment = new ExaminerAttemptAssignment(UUID.randomUUID(), examiner.tenantId(),
                sessionId, attemptId, examiner.userId(), 1, UUID.randomUUID(), Instant.parse("2026-01-01T00:00:00Z"));
        ExaminerQuestionPromptView prompt = new ExaminerQuestionPromptView(1, "SPEAKING", "READ_ALOUD", "Prompt",
                "Read the passage", promptMediaId, null, null, null, List.of());
        givenActiveExaminer(examiner);
        when(workQueueRepository.findOwnedAttempt(examiner.tenantId(), sessionId, attemptId, examiner.userId()))
                .thenReturn(Optional.of(assignment));
        when(scoringAnswerRepository.findBySessionPublicIdAndTenantIdAndAttemptPublicIdIn(sessionId,
                examiner.tenantId(), List.of(attemptId))).thenReturn(List.of(answer));
        when(eligibilityQueryService.isAiEligible(answer)).thenReturn(true);
        when(examinerAnswerScoreRepository.findByTenantIdAndSessionPublicIdAndAttemptPublicId(
                examiner.tenantId(), sessionId, attemptId))
                .thenReturn(List.of());
        when(assessmentService.getExaminerPrompts(List.of(answer.getPinnedItemPublicId()), examiner.tenantId()))
                .thenReturn(Map.of(answer.getPinnedItemPublicId(), prompt));
        when(answerPayloadDecoder.decode(answer)).thenReturn(new DecodedAnswerPayload(
                AnswerPayloadKind.AUDIO, null, answerMediaId, null, null, null, null));
        when(mediaService.presignGet(any(UUID.class), anyLong(), eq(examiner.tenantId())))
                .thenAnswer(invocation -> new PresignedDownloadResponse(
                        "https://media.test/" + invocation.getArgument(0), 60, null));

        ExaminerAttemptDetailResponse detail = service.getAttempt(sessionId, attemptId, examiner);

        assertThat(detail.answers()).hasSize(1);
        assertThat(detail.answers().getFirst().response().mediaUrl()).contains(answerMediaId.toString());
        InOrder authorizedReadOrder = inOrder(identityService, workQueueRepository, scoringAnswerRepository,
                eligibilityQueryService, assessmentService, answerPayloadDecoder, mediaService);
        authorizedReadOrder.verify(identityService)
                .findActiveExaminers(examiner.tenantId(), List.of(examiner.userId()));
        authorizedReadOrder.verify(workQueueRepository)
                .findOwnedAttempt(examiner.tenantId(), sessionId, attemptId, examiner.userId());
        authorizedReadOrder.verify(scoringAnswerRepository)
                .findBySessionPublicIdAndTenantIdAndAttemptPublicIdIn(sessionId, examiner.tenantId(), List.of(attemptId));
        authorizedReadOrder.verify(eligibilityQueryService).isAiEligible(answer);
        authorizedReadOrder.verify(assessmentService)
                .getExaminerPrompts(List.of(answer.getPinnedItemPublicId()), examiner.tenantId());
        authorizedReadOrder.verify(answerPayloadDecoder).decode(answer);
        authorizedReadOrder.verify(mediaService).presignGet(answerMediaId,
                com.pte.scoring.internal.constant.ExaminerScoringConstants.MEDIA_URL_TTL_SECONDS,
                examiner.tenantId());
        authorizedReadOrder.verify(mediaService).presignGet(promptMediaId,
                com.pte.scoring.internal.constant.ExaminerScoringConstants.MEDIA_URL_TTL_SECONDS,
                examiner.tenantId());
    }

    @Test
    void mediaSigningFailurePropagatesSoAttemptDetailCanBeRetried() {
        CurrentUser examiner = examiner();
        UUID sessionId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();
        UUID answerMediaId = UUID.randomUUID();
        ScoringAnswer answer = answer(examiner.tenantId(), sessionId, attemptId, answerId);
        ExaminerAttemptAssignment assignment = new ExaminerAttemptAssignment(UUID.randomUUID(), examiner.tenantId(),
                sessionId, attemptId, examiner.userId(), 1, UUID.randomUUID(), Instant.now());
        ExaminerQuestionPromptView prompt = new ExaminerQuestionPromptView(1, "SPEAKING", "READ_ALOUD", "Prompt",
                "Read the passage", null, null, null, null, List.of());
        givenActiveExaminer(examiner);
        when(workQueueRepository.findOwnedAttempt(examiner.tenantId(), sessionId, attemptId, examiner.userId()))
                .thenReturn(Optional.of(assignment));
        when(scoringAnswerRepository.findBySessionPublicIdAndTenantIdAndAttemptPublicIdIn(sessionId,
                examiner.tenantId(), List.of(attemptId))).thenReturn(List.of(answer));
        when(eligibilityQueryService.isAiEligible(answer)).thenReturn(true);
        when(examinerAnswerScoreRepository.findByTenantIdAndSessionPublicIdAndAttemptPublicId(
                examiner.tenantId(), sessionId, attemptId)).thenReturn(List.of());
        when(assessmentService.getExaminerPrompts(List.of(answer.getPinnedItemPublicId()), examiner.tenantId()))
                .thenReturn(Map.of(answer.getPinnedItemPublicId(), prompt));
        when(answerPayloadDecoder.decode(answer)).thenReturn(new DecodedAnswerPayload(
                AnswerPayloadKind.AUDIO, null, answerMediaId, null, null, null, null));
        when(mediaService.presignGet(answerMediaId,
                com.pte.scoring.internal.constant.ExaminerScoringConstants.MEDIA_URL_TTL_SECONDS,
                examiner.tenantId())).thenThrow(new IllegalStateException("temporary signing failure"));

        assertThatThrownBy(() -> service.getAttempt(sessionId, attemptId, examiner))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("temporary signing failure");
    }

    @Test
    void blindAttemptAndQueueDtoSerializationOmitsOtherScoreSourcesAndProvenance() throws Exception {
        CurrentUser examiner = examiner();
        UUID sessionId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        ScoringAnswer answer = answer(examiner.tenantId(), sessionId, attemptId, UUID.randomUUID());
        answer.markAiScored(94, AiProviderCategory.REAL, "private-ai-provider", "private-ai-model", "private-v1");
        answer.setTeacherScore(98);
        ExaminerAnswerScore selectedExaminerScore = new ExaminerAnswerScore(answer.getAnswerPublicId(), attemptId,
                sessionId, examiner.tenantId(), examiner.userId(), 87, Instant.parse("2026-01-01T00:00:00Z"));
        selectedExaminerScore.setId(1L);
        selectedExaminerScore.setPublicId(UUID.randomUUID());
        answer.selectScoreSource(ScoreSource.EXAMINER, UUID.randomUUID(), Instant.parse("2026-01-02T00:00:00Z"),
                selectedExaminerScore);
        givenActiveExaminer(examiner);
        when(workQueueRepository.findOwnedAttempt(examiner.tenantId(), sessionId, attemptId, examiner.userId()))
                .thenReturn(Optional.of(new ExaminerAttemptAssignment(UUID.randomUUID(), examiner.tenantId(),
                        sessionId, attemptId, examiner.userId(), 1, UUID.randomUUID(), Instant.now())));
        when(scoringAnswerRepository.findBySessionPublicIdAndTenantIdAndAttemptPublicIdIn(sessionId,
                examiner.tenantId(), List.of(attemptId))).thenReturn(List.of(answer));
        when(eligibilityQueryService.isAiEligible(answer)).thenReturn(true);
        when(examinerAnswerScoreRepository.findByTenantIdAndSessionPublicIdAndAttemptPublicId(
                examiner.tenantId(), sessionId, attemptId))
                .thenReturn(List.of());
        when(assessmentService.getExaminerPrompts(List.of(answer.getPinnedItemPublicId()), examiner.tenantId()))
                .thenReturn(Map.of(answer.getPinnedItemPublicId(), new ExaminerQuestionPromptView(1, "SPEAKING",
                        "READ_ALOUD", "Prompt", "Text", null, null, null, null, List.of())));
        when(answerPayloadDecoder.decode(answer)).thenReturn(new DecodedAnswerPayload(
                AnswerPayloadKind.TEXT, "student response", null, null, null, null, null));
        ExaminerAttemptDetailResponse detail = service.getAttempt(sessionId, attemptId, examiner);

        ExaminerQueueItemResponse queueItem = new ExaminerQueueItemResponse(attemptId, sessionId,
                Instant.parse("2026-01-01T00:00:00Z"), 1, 0, "PENDING");
        when(workQueueRepository.findQueue(eq(examiner.tenantId()), eq(examiner.userId()), eq(sessionId), eq("ALL"),
                any())).thenReturn(new PageImpl<>(List.of(queueItem), PageRequest.of(0, 20), 1));
        String queueJson = jsonMapper.writeValueAsString(service.listWork("ALL", sessionId, 0, 20, examiner));
        String detailJson = jsonMapper.writeValueAsString(detail);

        assertThat(queueJson).doesNotContain("rawScore", "teacherScore", "selectedScoreSource", "aiProvider");
        assertThat(detailJson).doesNotContain("rawScore", "teacherScore", "selectedScoreSource",
                "selectedScoreSourceByPublicId", "selectedExaminerScorePublicId", "aiProvider", "aiModel",
                "aiProviderVersion", "provenance", "referenceAnswerText", "correctAnswerText");
        assertThat(detailJson).doesNotContain("private-ai-provider", "private-ai-model", "private-v1");
        assertThat(detailJson).doesNotContain("\"rawScore\":94", "\"teacherScore\":98");
    }

    @Test
    void nullScoreIsRejectedBeforeAnswerRead() {
        CurrentUser examiner = examiner();
        givenActiveExaminer(examiner);

        assertThatThrownBy(() -> service.submitScore(UUID.randomUUID(), new SubmitExaminerScoreRequest(null), examiner))
                .isInstanceOf(InvalidExaminerScoreException.class);

        verifyNoInteractions(scoringAnswerRepository, assignmentRepository, sessionStateRepository,
                examinerAnswerScoreRepository, mediaService);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 101})
    void scoreOutsideZeroToOneHundredIsRejectedBeforeAnswerRead(int score) {
        CurrentUser examiner = examiner();
        givenActiveExaminer(examiner);

        assertThatThrownBy(() -> service.submitScore(UUID.randomUUID(), new SubmitExaminerScoreRequest(score), examiner))
                .isInstanceOf(InvalidExaminerScoreException.class);

        verifyNoInteractions(scoringAnswerRepository, assignmentRepository, sessionStateRepository,
                examinerAnswerScoreRepository, mediaService);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 100})
    void acceptsInclusiveScoreBoundariesAndKeepsOtherScoresUntouched(int score) {
        CurrentUser examiner = examiner();
        SubmissionFixture fixture = prepareNewSubmission(examiner, score);

        var submitted = service.submitScore(fixture.answer().getAnswerPublicId(), new SubmitExaminerScoreRequest(score),
                examiner);

        assertThat(submitted.myScore()).isEqualTo(score);
        assertThat(fixture.answer().getRawScore()).isEqualTo(92);
        assertThat(fixture.answer().getTeacherScore()).isEqualTo(85);
        verify(examinerAnswerScoreRepository).saveAndFlush(any(ExaminerAnswerScore.class));
    }

    @Test
    void identicalScoreRetryReturnsPersistedScoreWithoutWritingAgain() {
        CurrentUser examiner = examiner();
        SubmissionFixture fixture = prepareSubmissionWithExistingScore(examiner, examiner.userId(), 76, null);

        var retried = service.submitScore(fixture.answer().getAnswerPublicId(), new SubmitExaminerScoreRequest(76),
                examiner);

        assertThat(retried.myScore()).isEqualTo(76);
        assertThat(retried.submittedAt()).isEqualTo(fixture.existingScore().getSubmittedAt());
        verify(examinerAnswerScoreRepository, never()).saveAndFlush(any());
    }

    @Test
    void conflictingScoreRetryIsRejectedWithoutChangingPersistedScore() {
        CurrentUser examiner = examiner();
        SubmissionFixture fixture = prepareSubmissionWithExistingScore(examiner, examiner.userId(), 76, null);

        assertThatThrownBy(() -> service.submitScore(fixture.answer().getAnswerPublicId(),
                new SubmitExaminerScoreRequest(77), examiner)).isInstanceOf(ExaminerScoreConflictException.class);

        verify(examinerAnswerScoreRepository, never()).saveAndFlush(any());
    }

    @Test
    void retryByAnotherExaminerIsRejectedEvenWhenScoreMatches() {
        CurrentUser examiner = examiner();
        SubmissionFixture fixture = prepareSubmissionWithExistingScore(examiner, UUID.randomUUID(), 76, null);

        assertThatThrownBy(() -> service.submitScore(fixture.answer().getAnswerPublicId(),
                new SubmitExaminerScoreRequest(76), examiner)).isInstanceOf(ExaminerScoreConflictException.class);

        verify(examinerAnswerScoreRepository, never()).saveAndFlush(any());
    }

    @Test
    void publicationLockRejectsScoreWriteBeforeExistingScoreLookupOrSave() {
        CurrentUser examiner = examiner();
        ScoringSessionState publicationLock = new ScoringSessionState(examiner.tenantId(), UUID.randomUUID());
        publicationLock.lockForPublication(UUID.randomUUID(), Instant.parse("2026-01-03T00:00:00Z"));
        SubmissionFixture fixture = prepareNewSubmission(examiner, 80, publicationLock);

        assertThatThrownBy(() -> service.submitScore(fixture.answer().getAnswerPublicId(),
                new SubmitExaminerScoreRequest(80), examiner)).isInstanceOf(ExaminerScoreConflictException.class);

        verify(entityManager).lock(publicationLock, LockModeType.PESSIMISTIC_WRITE);
        verify(entityManager).refresh(publicationLock, LockModeType.PESSIMISTIC_WRITE);
        verify(entityManager, never()).lock(fixture.answer(), LockModeType.PESSIMISTIC_WRITE);
        verify(examinerAnswerScoreRepository, never()).findByAnswerPublicIdAndTenantId(any(), any());
        verify(examinerAnswerScoreRepository, never()).saveAndFlush(any());
    }

    @Test
    void foreignTenantAnswerIsRejectedBeforeAssignmentAndPublicationLookups() {
        CurrentUser examiner = examiner();
        UUID answerId = UUID.randomUUID();
        ScoringAnswer foreignTenantAnswer = answer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), answerId);
        givenActiveExaminer(examiner);
        when(scoringAnswerRepository.findByAnswerPublicId(answerId)).thenReturn(Optional.of(foreignTenantAnswer));

        assertThatThrownBy(() -> service.submitScore(answerId, new SubmitExaminerScoreRequest(50), examiner))
                .isInstanceOf(ExaminerWorkNotFoundException.class);

        verify(assignmentRepository, never()).existsByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicId(
                any(), any(), any(), any());
        verifyNoInteractions(sessionStateRepository, examinerAnswerScoreRepository, mediaService);
    }

    private SubmissionFixture prepareNewSubmission(CurrentUser examiner, int score) {
        return prepareNewSubmission(examiner, score, null);
    }

    private SubmissionFixture prepareNewSubmission(CurrentUser examiner, int score,
            ScoringSessionState publicationLock) {
        ScoringAnswer answer = answer(examiner.tenantId(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        answer.markAiScored(92, AiProviderCategory.REAL, "ai", "model", "v1");
        answer.setTeacherScore(85);
        givenActiveExaminer(examiner);
        when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId())).thenReturn(Optional.of(answer));
        when(assignmentRepository.existsByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicId(
                examiner.tenantId(), answer.getSessionPublicId(), answer.getAttemptPublicId(), examiner.userId()))
                .thenReturn(true);
        when(eligibilityQueryService.isAiEligible(answer)).thenReturn(true);
        when(sessionStateRepository.findByTenantIdAndSessionPublicId(examiner.tenantId(), answer.getSessionPublicId()))
                .thenReturn(Optional.ofNullable(publicationLock));
        if (publicationLock == null) {
            when(examinerAnswerScoreRepository.saveAndFlush(any(ExaminerAnswerScore.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
        }
        return new SubmissionFixture(answer, null);
    }

    private SubmissionFixture prepareSubmissionWithExistingScore(CurrentUser examiner, UUID scoreOwner, int score,
            ScoringSessionState publicationLock) {
        ScoringAnswer answer = answer(examiner.tenantId(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ExaminerAnswerScore existing = new ExaminerAnswerScore(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getSessionPublicId(), examiner.tenantId(), scoreOwner, score,
                Instant.parse("2026-01-04T00:00:00Z"));
        givenActiveExaminer(examiner);
        when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId())).thenReturn(Optional.of(answer));
        when(assignmentRepository.existsByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicId(
                examiner.tenantId(), answer.getSessionPublicId(), answer.getAttemptPublicId(), examiner.userId()))
                .thenReturn(true);
        when(eligibilityQueryService.isAiEligible(answer)).thenReturn(true);
        when(sessionStateRepository.findByTenantIdAndSessionPublicId(examiner.tenantId(), answer.getSessionPublicId()))
                .thenReturn(Optional.ofNullable(publicationLock));
        when(examinerAnswerScoreRepository.findByAnswerPublicIdAndTenantId(answer.getAnswerPublicId(),
                examiner.tenantId())).thenReturn(Optional.of(existing));
        return new SubmissionFixture(answer, existing);
    }

    private void givenActiveExaminer(CurrentUser examiner) {
        when(identityService.findActiveExaminers(examiner.tenantId(), List.of(examiner.userId())))
                .thenReturn(List.of(new ExaminerIdentityView(examiner.userId(), "Examiner", "examiner@example.test")));
    }

    private CurrentUser examiner() {
        return new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("EXAMINER"));
    }

    private ScoringAnswer answer(UUID tenantId, UUID sessionId, UUID attemptId, UUID answerId) {
        ScoringAnswer answer = new ScoringAnswer();
        answer.setAnswerPublicId(answerId);
        answer.setAttemptPublicId(attemptId);
        answer.setSessionPublicId(sessionId);
        answer.setTenantId(tenantId);
        answer.setPinnedItemPublicId(UUID.randomUUID());
        answer.setScoreTemplatePublicId(UUID.randomUUID());
        answer.setTaskType("READ_ALOUD");
        answer.setPayload("student answer");
        return answer;
    }

    private record SubmissionFixture(ScoringAnswer answer, ExaminerAnswerScore existingScore) {
    }
}
