package com.pte.scoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptExaminerPromptView;
import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.identity.IdentityService;
import com.pte.identity.dto.response.ExaminerIdentityView;
import com.pte.media.MediaService;
import com.pte.scoring.domain.ExaminerEvaluation;
import com.pte.scoring.domain.GradingAssignment;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.domain.enums.ExaminerAnswerScoreStatus;
import com.pte.scoring.dto.request.SubmitExaminerScoreRequest;
import com.pte.scoring.dto.response.ExaminerAttemptDetailResponse;
import com.pte.scoring.dto.response.ExaminerQueueItemResponse;
import com.pte.scoring.internal.dto.response.AnswerPayloadKind;
import com.pte.scoring.internal.dto.response.DecodedAnswerPayload;
import com.pte.scoring.internal.exception.ExaminerScoreConflictException;
import com.pte.scoring.internal.exception.ExaminerWorkNotFoundException;
import com.pte.scoring.internal.exception.InvalidExaminerScoreException;
import com.pte.scoring.internal.repository.ExaminerEvaluationRepository;
import com.pte.scoring.support.EvaluationFixtures;
import com.pte.scoring.support.SubmittedAnswerFixtures;
import com.pte.scoring.internal.repository.GradingAssignmentRepository;
import com.pte.scoring.internal.repository.ExaminerWorkQueueRepository;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExaminerScoringServiceTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

    @Mock
    private ExaminerWorkQueueRepository workQueueRepository;
    @Mock
    private GradingAssignmentRepository assignmentRepository;
    @Mock
    private AiEvaluationRepository scoringAnswerRepository;
    @Mock
    private ExaminerEvaluationRepository examinerAnswerScoreRepository;
    @Mock
    private SessionGradingGateway gateway;
    @Mock
    private ScoringEligibilityQueryService eligibilityQueryService;
    @Mock
    private AnswerPayloadDecoder answerPayloadDecoder;
    @Mock
    private AnswerContentReader contentReader;
    @Mock
    private IdentityService identityService;
    @Mock
    private AttemptService attemptService;
    @Mock
    private MediaService mediaService;
    @Mock
    private EntityManager entityManager;

    private ExaminerScoringService service;

    @BeforeEach
    void setUp() {
        service = new ExaminerScoringService(workQueueRepository, assignmentRepository, scoringAnswerRepository,
                examinerAnswerScoreRepository, gateway, eligibilityQueryService, answerPayloadDecoder,
                contentReader, identityService, attemptService, mediaService, entityManager, null,
                new ExaminerReviewVersionService(), null, null, null, null, null, null);
    }

    @Test
    void nonExaminerRoleIsRejectedBeforeAnyWorkOrMediaLookup() {
        CurrentUser host = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST_ADMIN"));

        assertThatThrownBy(() -> service.getAttempt(UUID.randomUUID(), UUID.randomUUID(), host))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(identityService, workQueueRepository, scoringAnswerRepository, attemptService,
                answerPayloadDecoder, mediaService, contentReader);
    }

    @Test
    void publishedSessionDoesNotExposeExaminerAttemptOrMedia() {
        CurrentUser examiner = examiner();
        UUID sessionPublicId = UUID.randomUUID();
        UUID attemptPublicId = UUID.randomUUID();
        givenActiveExaminer(examiner);
        GradingAssignment assignment = new GradingAssignment(examiner.tenantId(), sessionPublicId, attemptPublicId, examiner.userId(), 5, UUID.randomUUID(), Instant.now());
        when(workQueueRepository.findOwnedAttempt(examiner.tenantId(), sessionPublicId, attemptPublicId, examiner.userId()))
                .thenReturn(Optional.of(assignment));
        when(gateway.isPublished(examiner.tenantId(), sessionPublicId)).thenReturn(true);

        assertThatThrownBy(() -> service.getAttempt(sessionPublicId, attemptPublicId, examiner))
                .isInstanceOf(ExaminerWorkNotFoundException.class);

        verifyNoInteractions(attemptService, mediaService, answerPayloadDecoder, contentReader);
    }

    @Test
    void inactiveExaminerIsRejectedBeforeAssignmentAndMediaLookup() {
        CurrentUser examiner = examiner();
        when(identityService.findActiveExaminers(examiner.tenantId(), List.of(examiner.userId())))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.getAttempt(UUID.randomUUID(), UUID.randomUUID(), examiner))
                .isInstanceOf(ExaminerWorkNotFoundException.class);

        verifyNoInteractions(workQueueRepository, scoringAnswerRepository, attemptService,
                answerPayloadDecoder, mediaService, contentReader);
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
        verifyNoInteractions(scoringAnswerRepository, attemptService, answerPayloadDecoder, mediaService, contentReader);
    }

    @Test
    void assignedAttemptIsTenantScopedAndMediaIsPresignedOnlyAfterAuthorizationAndPromptLookup() {
        CurrentUser examiner = examiner();
        UUID sessionId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();
        UUID answerMediaId = UUID.randomUUID();
        UUID promptMediaId = UUID.randomUUID();
        AiEvaluation answer = answer(examiner.tenantId(), sessionId, attemptId, answerId);
        GradingAssignment assignment = new GradingAssignment(examiner.tenantId(),
                sessionId, attemptId, examiner.userId(), 1, UUID.randomUUID(), Instant.parse("2026-01-01T00:00:00Z"));
        AttemptExaminerPromptView prompt = new AttemptExaminerPromptView(answer.getItemPublicId(), 1, "SPEAKING", "READ_ALOUD", "Prompt",
                "Read the passage", promptMediaId, null, null, null, List.of());
        givenActiveExaminer(examiner);
        when(workQueueRepository.findOwnedAttempt(examiner.tenantId(), sessionId, attemptId, examiner.userId()))
                .thenReturn(Optional.of(assignment));
        when(scoringAnswerRepository.findBySessionPublicIdAndTenantIdAndAttemptPublicIdIn(sessionId,
                examiner.tenantId(), List.of(attemptId))).thenReturn(List.of(answer));
        when(eligibilityQueryService.isAiEligible(answer)).thenReturn(true);
        when(examinerAnswerScoreRepository.findSubmittedByAttempt(examiner.tenantId(), sessionId, attemptId))
                .thenReturn(List.of());
        stubContent(examiner, attemptId, answer);
        when(attemptService.getExaminerPrompts(attemptId, sessionId, examiner.tenantId(),
                List.of(answer.getItemPublicId())))
                .thenReturn(Map.of(answer.getItemPublicId(), prompt));
        when(answerPayloadDecoder.decode(content(answer))).thenReturn(new DecodedAnswerPayload(
                AnswerPayloadKind.AUDIO, null, answerMediaId, null, null, null, null));
        when(mediaService.presignGetAll(any(), anyLong(), eq(examiner.tenantId())))
                .thenReturn(Map.of(answerMediaId,
                        new com.pte.media.dto.response.PresignedDownloadResponse(
                                "https://media.test/" + answerMediaId, 60, null),
                        promptMediaId,
                        new com.pte.media.dto.response.PresignedDownloadResponse(
                                "https://media.test/" + promptMediaId, 60, null)));

        ExaminerAttemptDetailResponse detail = service.getAttempt(sessionId, attemptId, examiner);

        assertThat(detail.answers()).hasSize(1);
        assertThat(detail.answers().getFirst().response().mediaUrl()).contains(answerMediaId.toString());
        InOrder authorizedReadOrder = inOrder(identityService, workQueueRepository, scoringAnswerRepository,
                eligibilityQueryService, attemptService, answerPayloadDecoder, mediaService, contentReader);
        authorizedReadOrder.verify(identityService)
                .findActiveExaminers(examiner.tenantId(), List.of(examiner.userId()));
        authorizedReadOrder.verify(workQueueRepository)
                .findOwnedAttempt(examiner.tenantId(), sessionId, attemptId, examiner.userId());
        authorizedReadOrder.verify(scoringAnswerRepository)
                .findBySessionPublicIdAndTenantIdAndAttemptPublicIdIn(sessionId, examiner.tenantId(), List.of(attemptId));
        authorizedReadOrder.verify(eligibilityQueryService).isAiEligible(answer);
        authorizedReadOrder.verify(attemptService)
                .getExaminerPrompts(attemptId, sessionId, examiner.tenantId(),
                        List.of(answer.getItemPublicId()));
        authorizedReadOrder.verify(answerPayloadDecoder).decode(content(answer));
        authorizedReadOrder.verify(mediaService).presignGetAll(eq(Set.of(answerMediaId, promptMediaId)),
                eq(com.pte.scoring.internal.constant.ExaminerScoringConstants.MEDIA_URL_TTL_SECONDS),
                eq(examiner.tenantId()));
    }

    @Test
    void loadsSeveralAnswersThroughOneBulkPromptCallWithDuplicatePinnedItemsDeduplicated() {
        CurrentUser examiner = examiner();
        UUID sessionId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID firstItemId = UUID.randomUUID();
        UUID secondItemId = UUID.randomUUID();
        AiEvaluation firstAnswer = answer(examiner.tenantId(), sessionId, attemptId, UUID.randomUUID(), firstItemId);
        AiEvaluation secondAnswer = answer(examiner.tenantId(), sessionId, attemptId, UUID.randomUUID(), secondItemId);
        AiEvaluation repeatedItemAnswer = answer(examiner.tenantId(), sessionId, attemptId, UUID.randomUUID(),
                firstItemId);
        givenActiveExaminer(examiner);
        when(workQueueRepository.findOwnedAttempt(examiner.tenantId(), sessionId, attemptId, examiner.userId()))
                .thenReturn(Optional.of(new GradingAssignment(examiner.tenantId(),
                        sessionId, attemptId, examiner.userId(), 3, UUID.randomUUID(), Instant.now())));
        when(scoringAnswerRepository.findBySessionPublicIdAndTenantIdAndAttemptPublicIdIn(sessionId,
                examiner.tenantId(), List.of(attemptId))).thenReturn(List.of(firstAnswer, secondAnswer, repeatedItemAnswer));
        when(eligibilityQueryService.isAiEligible(firstAnswer)).thenReturn(true);
        when(eligibilityQueryService.isAiEligible(secondAnswer)).thenReturn(true);
        when(eligibilityQueryService.isAiEligible(repeatedItemAnswer)).thenReturn(true);
        when(examinerAnswerScoreRepository.findSubmittedByAttempt(examiner.tenantId(), sessionId, attemptId))
                .thenReturn(List.of());
        stubContent(examiner, attemptId, firstAnswer, secondAnswer, repeatedItemAnswer);
        AttemptExaminerPromptView firstPrompt = new AttemptExaminerPromptView(firstItemId, 1, "SPEAKING", "READ_ALOUD",
                "Prompt one", "First prompt text", null, null, null, null, List.of());
        AttemptExaminerPromptView secondPrompt = new AttemptExaminerPromptView(secondItemId, 2, "WRITING", "WRITE_ESSAY",
                "Prompt two", "Second prompt text", null, null, null, null, List.of());
        when(attemptService.getExaminerPrompts(attemptId, sessionId, examiner.tenantId(),
                List.of(firstItemId, secondItemId)))
                .thenReturn(Map.of(firstItemId, firstPrompt, secondItemId, secondPrompt));
        when(answerPayloadDecoder.decode(content(firstAnswer))).thenReturn(textAnswer("first response"));
        when(answerPayloadDecoder.decode(content(secondAnswer))).thenReturn(textAnswer("second response"));
        when(answerPayloadDecoder.decode(content(repeatedItemAnswer))).thenReturn(textAnswer("repeated-item response"));

        ExaminerAttemptDetailResponse detail = service.getAttempt(sessionId, attemptId, examiner);

        assertThat(detail.answers()).hasSize(3);
        assertThat(detail.answers()).extracting(answer -> answer.prompt().title())
                .containsExactly("Prompt one", "Prompt one", "Prompt two");
        verify(attemptService, times(1))
                .getExaminerPrompts(attemptId, sessionId, examiner.tenantId(), List.of(firstItemId, secondItemId));
    }

    @Test
    void mediaSigningFailurePropagatesSoAttemptDetailCanBeRetried() {
        CurrentUser examiner = examiner();
        UUID sessionId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();
        UUID answerMediaId = UUID.randomUUID();
        AiEvaluation answer = answer(examiner.tenantId(), sessionId, attemptId, answerId);
        GradingAssignment assignment = new GradingAssignment(examiner.tenantId(),
                sessionId, attemptId, examiner.userId(), 1, UUID.randomUUID(), Instant.now());
        AttemptExaminerPromptView prompt = new AttemptExaminerPromptView(answer.getItemPublicId(), 1, "SPEAKING", "READ_ALOUD", "Prompt",
                "Read the passage", null, null, null, null, List.of());
        givenActiveExaminer(examiner);
        when(workQueueRepository.findOwnedAttempt(examiner.tenantId(), sessionId, attemptId, examiner.userId()))
                .thenReturn(Optional.of(assignment));
        when(scoringAnswerRepository.findBySessionPublicIdAndTenantIdAndAttemptPublicIdIn(sessionId,
                examiner.tenantId(), List.of(attemptId))).thenReturn(List.of(answer));
        when(eligibilityQueryService.isAiEligible(answer)).thenReturn(true);
        when(examinerAnswerScoreRepository.findSubmittedByAttempt(examiner.tenantId(), sessionId, attemptId))
                .thenReturn(List.of());
        stubContent(examiner, attemptId, answer);
        when(attemptService.getExaminerPrompts(attemptId, sessionId, examiner.tenantId(),
                List.of(answer.getItemPublicId())))
                .thenReturn(Map.of(answer.getItemPublicId(), prompt));
        when(answerPayloadDecoder.decode(content(answer))).thenReturn(new DecodedAnswerPayload(
                AnswerPayloadKind.AUDIO, null, answerMediaId, null, null, null, null));
        when(mediaService.presignGetAll(eq(Set.of(answerMediaId)),
                eq(com.pte.scoring.internal.constant.ExaminerScoringConstants.MEDIA_URL_TTL_SECONDS),
                eq(examiner.tenantId()))).thenThrow(new IllegalStateException("temporary signing failure"));

        assertThatThrownBy(() -> service.getAttempt(sessionId, attemptId, examiner))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("temporary signing failure");
    }

    @Test
    void blindAttemptAndQueueDtoSerializationOmitsOtherScoreSourcesAndProvenance() throws Exception {
        CurrentUser examiner = examiner();
        UUID sessionId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        AiEvaluation answer = answer(examiner.tenantId(), sessionId, attemptId, UUID.randomUUID());
        answer.markAiScored(94, AiProviderCategory.REAL, "private-ai-provider", "private-ai-model", "private-v1");
        answer.setTeacherScore(98);
        givenActiveExaminer(examiner);
        when(workQueueRepository.findOwnedAttempt(examiner.tenantId(), sessionId, attemptId, examiner.userId()))
                .thenReturn(Optional.of(new GradingAssignment(examiner.tenantId(),
                        sessionId, attemptId, examiner.userId(), 1, UUID.randomUUID(), Instant.now())));
        when(scoringAnswerRepository.findBySessionPublicIdAndTenantIdAndAttemptPublicIdIn(sessionId,
                examiner.tenantId(), List.of(attemptId))).thenReturn(List.of(answer));
        when(eligibilityQueryService.isAiEligible(answer)).thenReturn(true);
        when(examinerAnswerScoreRepository.findSubmittedByAttempt(examiner.tenantId(), sessionId, attemptId))
                .thenReturn(List.of());
        stubContent(examiner, attemptId, answer);
        when(attemptService.getExaminerPrompts(attemptId, sessionId, examiner.tenantId(),
                List.of(answer.getItemPublicId())))
                .thenReturn(Map.of(answer.getItemPublicId(), new AttemptExaminerPromptView(
                        answer.getItemPublicId(), 1, "SPEAKING",
                        "READ_ALOUD", "Prompt", "Text", null, null, null, null, List.of())));
        when(answerPayloadDecoder.decode(content(answer))).thenReturn(new DecodedAnswerPayload(
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

        verifyNoInteractions(scoringAnswerRepository, assignmentRepository, gateway,
                examinerAnswerScoreRepository, mediaService);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 101})
    void scoreOutsideZeroToOneHundredIsRejectedBeforeAnswerRead(int score) {
        CurrentUser examiner = examiner();
        givenActiveExaminer(examiner);

        assertThatThrownBy(() -> service.submitScore(UUID.randomUUID(), new SubmitExaminerScoreRequest(score), examiner))
                .isInstanceOf(InvalidExaminerScoreException.class);

        verifyNoInteractions(scoringAnswerRepository, assignmentRepository, gateway,
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
        verify(examinerAnswerScoreRepository).saveAndFlush(any(ExaminerEvaluation.class));
    }

    @Test
    void identicalScoreRetryReturnsPersistedScoreWithoutWritingAgain() {
        CurrentUser examiner = examiner();
        SubmissionFixture fixture = prepareSubmissionWithExistingScore(examiner, examiner.userId(), 76, false);

        var retried = service.submitScore(fixture.answer().getAnswerPublicId(), new SubmitExaminerScoreRequest(76),
                examiner);

        assertThat(retried.myScore()).isEqualTo(76);
        assertThat(retried.submittedAt()).isEqualTo(fixture.existingScore().getSubmittedAt());
        verify(examinerAnswerScoreRepository, never()).saveAndFlush(any());
    }

    @Test
    void conflictingScoreRetryIsRejectedWithoutChangingPersistedScore() {
        CurrentUser examiner = examiner();
        SubmissionFixture fixture = prepareSubmissionWithExistingScore(examiner, examiner.userId(), 76, false);

        assertThatThrownBy(() -> service.submitScore(fixture.answer().getAnswerPublicId(),
                new SubmitExaminerScoreRequest(77), examiner)).isInstanceOf(ExaminerScoreConflictException.class);

        verify(examinerAnswerScoreRepository, never()).saveAndFlush(any());
    }

    @Test
    void retryByAnotherExaminerIsRejectedEvenWhenScoreMatches() {
        CurrentUser examiner = examiner();
        SubmissionFixture fixture = prepareSubmissionWithExistingScore(examiner, UUID.randomUUID(), 76, false);

        assertThatThrownBy(() -> service.submitScore(fixture.answer().getAnswerPublicId(),
                new SubmitExaminerScoreRequest(76), examiner)).isInstanceOf(ExaminerScoreConflictException.class);

        verify(examinerAnswerScoreRepository, never()).saveAndFlush(any());
    }

    @Test
    void publicationLockRejectsScoreWriteBeforeExistingScoreLookupOrSave() {
        CurrentUser examiner = examiner();
        SubmissionFixture fixture = prepareNewSubmission(examiner, 80, true);

        assertThatThrownBy(() -> service.submitScore(fixture.answer().getAnswerPublicId(),
                new SubmitExaminerScoreRequest(80), examiner)).isInstanceOf(ExaminerScoreConflictException.class);

        verify(entityManager).lock(fixture.answer(), LockModeType.PESSIMISTIC_WRITE);
        verify(entityManager).refresh(fixture.answer(), LockModeType.PESSIMISTIC_WRITE);
        verify(examinerAnswerScoreRepository, never()).findByAnswerPublicIdAndTenantId(any(), any());
        verify(examinerAnswerScoreRepository, never()).saveAndFlush(any());
    }

    @Test
    void foreignTenantAnswerIsRejectedBeforeAssignmentAndPublicationLookups() {
        CurrentUser examiner = examiner();
        UUID answerId = UUID.randomUUID();
        AiEvaluation foreignTenantAnswer = answer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), answerId);
        givenActiveExaminer(examiner);
        when(scoringAnswerRepository.findByAnswerPublicId(answerId)).thenReturn(Optional.of(foreignTenantAnswer));

        assertThatThrownBy(() -> service.submitScore(answerId, new SubmitExaminerScoreRequest(50), examiner))
                .isInstanceOf(ExaminerWorkNotFoundException.class);

        verify(assignmentRepository, never()).existsByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicId(
                any(), any(), any(), any());
        verifyNoInteractions(gateway, examinerAnswerScoreRepository, mediaService);
    }

    private SubmissionFixture prepareNewSubmission(CurrentUser examiner, int score) {
        return prepareNewSubmission(examiner, score, false);
    }

    private SubmissionFixture prepareNewSubmission(CurrentUser examiner, int score,
            boolean published) {
        AiEvaluation answer = answer(examiner.tenantId(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        answer.markAiScored(92, AiProviderCategory.REAL, "ai", "model", "v1");
        answer.setTeacherScore(85);
        givenActiveExaminer(examiner);
        when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId())).thenReturn(Optional.of(answer));
        when(assignmentRepository.existsByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicId(
                examiner.tenantId(), answer.getSessionPublicId(), answer.getAttemptPublicId(), examiner.userId()))
                .thenReturn(true);
        when(eligibilityQueryService.isAiEligible(answer)).thenReturn(true);
        if (published) {
            when(gateway.isPublished(examiner.tenantId(), answer.getSessionPublicId())).thenReturn(true);
        }
        if (!published) {
            when(examinerAnswerScoreRepository.saveAndFlush(any(ExaminerEvaluation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
        }
        return new SubmissionFixture(answer, null);
    }

    private SubmissionFixture prepareSubmissionWithExistingScore(CurrentUser examiner, UUID scoreOwner, int score,
            boolean published) {
        AiEvaluation answer = answer(examiner.tenantId(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ExaminerEvaluation existing = ExaminerEvaluation.submittedDirectly(examiner.tenantId(),
                answer.getSessionPublicId(), answer.getAttemptPublicId(), answer.getAnswerPublicId(), scoreOwner,
                score, Instant.parse("2026-01-04T00:00:00Z"), null, null, null, null, null, null);
        givenActiveExaminer(examiner);
        when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId())).thenReturn(Optional.of(answer));
        when(assignmentRepository.existsByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicId(
                examiner.tenantId(), answer.getSessionPublicId(), answer.getAttemptPublicId(), examiner.userId()))
                .thenReturn(true);
        when(eligibilityQueryService.isAiEligible(answer)).thenReturn(true);
        if (published) {
            when(gateway.isPublished(examiner.tenantId(), answer.getSessionPublicId())).thenReturn(true);
        }
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

    private AiEvaluation answer(UUID tenantId, UUID sessionId, UUID attemptId, UUID answerId) {
        return answer(tenantId, sessionId, attemptId, answerId, UUID.randomUUID());
    }

    private AiEvaluation answer(UUID tenantId, UUID sessionId, UUID attemptId, UUID answerId, UUID itemId) {
        return new AiEvaluation(tenantId, sessionId, attemptId, answerId, itemId, UUID.randomUUID(), "READ_ALOUD",
                "SPEAKING", ScoringMethod.AI_SPEECH);
    }

    /** What the attempt holds for this evaluation's answer; equal for equal evaluations. */
    private SubmittedAnswerView content(AiEvaluation answer) {
        return new SubmittedAnswerView(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getItemPublicId(), answer.getSessionPublicId(), answer.getTenantId(),
                answer.getExamDefinitionPublicId(), answer.getTaskType(), "student answer", null, null, false, null,
                null, null, null, null, answer.getSection(), null, null, null, null, null);
    }

    private void stubContent(CurrentUser examiner, UUID attemptId, AiEvaluation... answers) {
        Map<UUID, SubmittedAnswerView> byAnswer = new java.util.HashMap<>();
        for (AiEvaluation answer : answers) {
            byAnswer.put(answer.getAnswerPublicId(), content(answer));
        }
        when(contentReader.forAttempt(attemptId, examiner.tenantId())).thenReturn(byAnswer);
    }

    private DecodedAnswerPayload textAnswer(String text) {
        return new DecodedAnswerPayload(AnswerPayloadKind.TEXT, text, null, null, null, null, null);
    }

    private record SubmissionFixture(AiEvaluation answer, ExaminerEvaluation existingScore) {
    }
}
