package com.pte.scoring.internal.service;

import com.pte.scoring.internal.service.ai.AiScoreDetailQueryService;
import com.pte.scoring.internal.service.examiner.ExaminerReviewVersionService;
import com.pte.scoring.internal.service.examiner.ExaminerScoringService;
import com.pte.scoring.internal.service.grading.SessionGradingGateway;
import com.pte.scoring.internal.service.objective.AnswerContentReader;
import com.pte.scoring.internal.service.objective.AnswerPayloadDecoder;
import com.pte.scoring.internal.service.staff.ScoringEligibilityQueryService;
import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptExaminerPromptView;
import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.identity.IdentityService;
import com.pte.identity.dto.response.ExaminerIdentityView;
import com.pte.media.MediaService;
import com.pte.scoring.domain.ExaminerEvaluation;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.domain.GradingAssignment;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.dto.response.AiScoreDetailResponse;
import com.pte.scoring.dto.response.ExaminerAttemptDetailResponse;
import com.pte.scoring.internal.dto.response.AnswerPayloadKind;
import com.pte.scoring.internal.dto.response.DecodedAnswerPayload;
import com.pte.scoring.internal.exception.examiner.ExaminerWorkNotFoundException;
import com.pte.scoring.internal.repository.ExaminerEvaluationRepository;
import com.pte.scoring.internal.repository.GradingAssignmentRepository;
import com.pte.scoring.internal.repository.ExaminerWorkQueueRepository;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import com.pte.shared.security.CurrentUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The AI score reaches an examiner only through the assignment-checked attempt detail. */
@ExtendWith(MockitoExtension.class)
class ExaminerScoringAiDetailTest {

    private final UUID sessionId = UUID.randomUUID();
    private final UUID attemptId = UUID.randomUUID();

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
    @Mock
    private AiScoreDetailQueryService aiDetails;

    private ExaminerScoringService service;
    private CurrentUser examiner;

    @BeforeEach
    void setUp() {
        service = new ExaminerScoringService(workQueueRepository, assignmentRepository, scoringAnswerRepository,
                examinerAnswerScoreRepository, gateway, eligibilityQueryService, answerPayloadDecoder,
                contentReader, identityService, attemptService, mediaService, entityManager, null,
                new ExaminerReviewVersionService(), null, null, null, null, com.pte.TestClocks.fixed(), aiDetails);
        examiner = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("EXAMINER"));
        when(identityService.findActiveExaminers(examiner.tenantId(), List.of(examiner.userId())))
                .thenReturn(List.of(new ExaminerIdentityView(examiner.userId(), "Examiner", "e@example.test")));
    }

    @Test
    void anAssignedExaminerSeesTheAiScoreAndBreakdownBeforeScoringAnything() {
        AiEvaluation answer = assignedAnswer();
        AiScoreDetailResponse ai = new AiScoreDetailResponse("SCORED", 64, Map.of("CONTENT", 70), "Fine.",
                "what was said", Map.of(), false, null);
        when(aiDetails.findAll(examiner.tenantId(), List.of(answer))).thenReturn(Map.of(answer.getAnswerPublicId(), ai));
        when(examinerAnswerScoreRepository.findSubmittedByAttempt(examiner.tenantId(), sessionId, attemptId)).thenReturn(List.of());

        ExaminerAttemptDetailResponse detail = service.getAttempt(sessionId, attemptId, examiner);

        var shown = detail.answers().getFirst();
        assertThat(shown.myScore()).isNull();
        assertThat(shown.ai()).isSameAs(ai);
    }

    @Test
    void theAiDetailStaysVisibleAfterTheExaminerHasScored() {
        AiEvaluation answer = assignedAnswer();
        AiScoreDetailResponse ai = new AiScoreDetailResponse("SCORED", 64, Map.of(), null, null, Map.of(), false,
                null);
        when(aiDetails.findAll(examiner.tenantId(), List.of(answer))).thenReturn(Map.of(answer.getAnswerPublicId(), ai));
        ExaminerEvaluation mine = ExaminerEvaluation.submittedDirectly(examiner.tenantId(), sessionId, attemptId,
                answer.getAnswerPublicId(), examiner.userId(), 80, Instant.parse("2026-01-02T00:00:00Z"), null, null,
                null, null, null, null);
        when(examinerAnswerScoreRepository.findSubmittedByAttempt(examiner.tenantId(), sessionId, attemptId)).thenReturn(List.of(mine));

        var shown = service.getAttempt(sessionId, attemptId, examiner).answers().getFirst();

        assertThat(shown.myScore()).isEqualTo(80);
        assertThat(shown.ai()).isSameAs(ai);
    }

    @Test
    void anAnswerWithoutAnAiScoreYetStillCarriesItsStatus() {
        AiEvaluation answer = assignedAnswer();
        AiScoreDetailResponse scoring = new AiScoreDetailResponse("SCORING", null, Map.of(), null, null, Map.of(),
                false, null);
        when(aiDetails.findAll(examiner.tenantId(), List.of(answer)))
                .thenReturn(Map.of(answer.getAnswerPublicId(), scoring));
        when(examinerAnswerScoreRepository.findSubmittedByAttempt(examiner.tenantId(), sessionId, attemptId)).thenReturn(List.of());

        var shown = service.getAttempt(sessionId, attemptId, examiner).answers().getFirst();

        assertThat(shown.ai().status()).isEqualTo("SCORING");
        assertThat(shown.ai().score()).isNull();
    }

    @Test
    void anAnswerTheHostHasNotQueuedYetShowsAsPendingRatherThanMissing() {
        AiEvaluation answer = assignedAnswer();
        when(aiDetails.findAll(examiner.tenantId(), List.of(answer))).thenReturn(Map.of());
        when(examinerAnswerScoreRepository.findSubmittedByAttempt(examiner.tenantId(), sessionId, attemptId)).thenReturn(List.of());

        var shown = service.getAttempt(sessionId, attemptId, examiner).answers().getFirst();

        assertThat(shown.ai().status()).isEqualTo("PENDING");
        assertThat(shown.ai().score()).isNull();
        assertThat(shown.ai().criteria()).isEmpty();
    }

    @Test
    void anExaminerOutsideTheAssignmentIsRejectedAndNothingAiIsLookedUp() {
        when(workQueueRepository.findOwnedAttempt(examiner.tenantId(), sessionId, attemptId, examiner.userId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAttempt(sessionId, attemptId, examiner))
                .isInstanceOf(ExaminerWorkNotFoundException.class);

        verifyNoInteractions(aiDetails, scoringAnswerRepository);
    }

    private AiEvaluation assignedAnswer() {
        AiEvaluation answer = new AiEvaluation(examiner.tenantId(), sessionId, attemptId, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "WRITE_ESSAY", "WRITING", ScoringMethod.AI_TEXT);
        SubmittedAnswerView content = new SubmittedAnswerView(answer.getAnswerPublicId(), attemptId,
                answer.getItemPublicId(), sessionId, examiner.tenantId(), answer.getExamDefinitionPublicId(),
                "WRITE_ESSAY", "essay", null, null, false, null, null, null, null, null, "WRITING", null, null, null,
                null, null);
        when(contentReader.forAttempt(attemptId, examiner.tenantId()))
                .thenReturn(Map.of(answer.getAnswerPublicId(), content));
        GradingAssignment assignment = new GradingAssignment(examiner.tenantId(),
                sessionId, attemptId, examiner.userId(), 1, UUID.randomUUID(), Instant.parse("2026-01-01T00:00:00Z"));
        when(workQueueRepository.findOwnedAttempt(examiner.tenantId(), sessionId, attemptId, examiner.userId()))
                .thenReturn(Optional.of(assignment));
        when(scoringAnswerRepository.findBySessionPublicIdAndTenantIdAndAttemptPublicIdIn(sessionId,
                examiner.tenantId(), List.of(attemptId))).thenReturn(List.of(answer));
        when(eligibilityQueryService.isAiEligible(answer)).thenReturn(true);
        AttemptExaminerPromptView prompt = new AttemptExaminerPromptView(answer.getItemPublicId(), 1,
                "WRITING", "WRITE_ESSAY", "Prompt", "Write an essay", null, null, 200, 300, List.of());
        when(attemptService.getExaminerPrompts(attemptId, sessionId, examiner.tenantId(),
                List.of(answer.getItemPublicId()))).thenReturn(Map.of(answer.getItemPublicId(), prompt));
        when(answerPayloadDecoder.decode(content)).thenReturn(
                new DecodedAnswerPayload(AnswerPayloadKind.TEXT, "essay", null, null, null, null, null));
        return answer;
    }
}
