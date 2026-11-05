package com.pte.scoring.internal.service.examiner;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptGradingCandidateView;
import com.pte.scoring.CriteriaScores;
import com.pte.scoring.domain.*;
import com.pte.scoring.domain.enums.*;
import com.pte.scoring.internal.repository.*;
import com.pte.scoring.internal.rubric.service.*;
import com.pte.scoring.internal.service.examiner.trait.*;
import com.pte.scoring.internal.service.grading.SessionGradingGateway;
import com.pte.scoring.internal.service.objective.AnswerContentReader;
import com.pte.scoring.internal.service.staff.ScoringEligibilityQueryService;
import com.pte.scoring.rubric.*;
import com.pte.scoring.support.EvaluationFixtures;
import com.pte.session.SessionService;
import com.pte.shared.audit.*;
import com.pte.shared.idempotency.*;
import com.pte.shared.security.CurrentUser;
import jakarta.persistence.EntityManager;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;

/** Real trait computation/freshness with persistence and external module ports isolated. */
class ExaminerTraitCommandFixture {
    final UUID tenant = UUID.randomUUID(), session = UUID.randomUUID(), attempt = UUID.randomUUID(), answerId = UUID.randomUUID();
    final UUID version = UUID.randomUUID();
    final CurrentUser user = new CurrentUser(UUID.randomUUID(), tenant, List.of("EXAMINER"));
    final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    final GradingAssignmentRepository assignments = mock(GradingAssignmentRepository.class);
    final AiEvaluationRepository answers = mock(AiEvaluationRepository.class);
    final ExaminerEvaluationRepository scores = mock(ExaminerEvaluationRepository.class);
    final GradingIssueRepository issues = mock(GradingIssueRepository.class);
    final SessionGradingGateway gateway = mock(SessionGradingGateway.class);
    final AttemptService attempts = mock(AttemptService.class);
    final AnswerContentReader content = mock(AnswerContentReader.class);
    final ExaminerWorkflowQueryService workflow = mock(ExaminerWorkflowQueryService.class);
    final ScoringEligibilityQueryService eligibility = mock(ScoringEligibilityQueryService.class);
    final IdempotencyKeyService idempotency = mock(IdempotencyKeyService.class);
    final EntityManager entities = mock(EntityManager.class);
    final SessionService sessions = mock(SessionService.class);
    final ExaminerTraitRubricReader rubrics = mock(ExaminerTraitRubricReader.class);
    final ExaminerReviewVersionService versions = new ExaminerReviewVersionService();
    final EffectiveRubric rubric = new EffectiveRubric(RubricVersionFixtures.CATALOG.find("DESCRIBE_IMAGE").orElseThrow(), RubricVersionFixtures.CATALOG.defaultParameters("DESCRIBE_IMAGE"));
    final ExaminerTraitScoreResolver traits = new ExaminerTraitScoreResolver(rubrics, new ExaminerTraitValidator(), content, json);
    final ExaminerTraitFreshness freshness = new ExaminerTraitFreshness(versions, gateway, attempts, content);
    final AiEvaluation answer = EvaluationFixtures.ai(tenant, session, attempt, answerId, "DESCRIBE_IMAGE", ScoringMethod.AI_SPEECH);
    final GradingAssignment work = new GradingAssignment(tenant, session, attempt, user.userId(), 1, UUID.randomUUID(), com.pte.TestClocks.fixed().instant());
    final SessionGradingGateway.Grading cohort = new SessionGradingGateway.Grading(session, tenant, GradingMarkingMode.MANUAL_EXAMINER,
            GradingCohortStatus.FROZEN, "v1", UUID.randomUUID(), com.pte.TestClocks.fixed().instant(), null);
    final AttemptGradingCandidateView member = new AttemptGradingCandidateView(attempt, UUID.randomUUID(), "SUBMITTED", "v1", false, null);
    final ExaminerDraftCommandService drafts;
    final ExaminerDirectScoreSubmitter direct;
    final ExaminerFinalizeSubmitter finalize;

    ExaminerTraitCommandFixture() {
        work.setPublicId(UUID.randomUUID()); ReflectionTestUtils.setField(work, "id", 7L); work.start(com.pte.TestClocks.fixed().instant());
        var ai = Map.of("CONTENT", 3, "PRONUNCIATION", 3, "ORAL_FLUENCY", 3);
        int score = RubricScorer.score(rubric, new TraitScoreSet(ai, null)).itemScore();
        answer.markAiScored(score, AiProviderCategory.REAL, "fixture", "model", "v1", com.pte.TestClocks.fixed().instant());
        answer.recordDetail(CriteriaScores.pearson(version, ai).toJson(json), null, null, null, false, version);
        when(rubrics.read(answer)).thenReturn(new ResolvedRubric(version, rubric));
        when(rubrics.stored(version, "DESCRIBE_IMAGE")).thenReturn(new ResolvedRubric(version, rubric));
        when(assignments.existsByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicId(tenant, session, attempt, user.userId())).thenReturn(true);
        when(assignments.findByTenantIdAndSessionPublicIdAndExaminerPublicId(tenant, session, user.userId())).thenReturn(List.of(work));
        when(assignments.findByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicIdAndDeletedFalse(tenant, session, attempt, user.userId())).thenReturn(Optional.of(work));
        when(assignments.findForUpdate(tenant, work.getPublicId())).thenReturn(Optional.of(work));
        when(answers.findByAnswerPublicId(answerId)).thenReturn(Optional.of(answer));
        when(answers.existsByTenantIdAndSessionPublicIdAndAttemptPublicIdAndAnswerPublicIdAndDeletedFalse(tenant, session, attempt, answerId)).thenReturn(true);
        when(eligibility.isAiEligible(answer)).thenReturn(true);
        when(gateway.grading(tenant, session)).thenReturn(Optional.of(cohort));
        when(attempts.getGradingCandidate(attempt, tenant)).thenReturn(Optional.of(member));
        org.mockito.stubbing.Answer<ExaminerEvaluation> persist = call -> {
            ExaminerEvaluation row = call.getArgument(0); if (row.getPublicId() == null) row.setPublicId(UUID.randomUUID()); return row;
        };
        when(scores.save(any())).thenAnswer(persist);
        when(scores.saveAndFlush(any())).thenAnswer(persist);
        when(idempotency.begin(any(), any(), any(), any(), any())).thenReturn(new IdempotencyDecision(IdempotencyOutcome.NEW, null));
        var recorder = new ExaminerWorkRecorder(idempotency, mock(AuditLogChangeWriter.class), mock(AuditHistoryReader.class), json);
        finalize = new ExaminerFinalizeSubmitter(scores, versions, attempts, traits, freshness);
        drafts = new ExaminerDraftCommandService(sessions, assignments, issues, scores, answers, gateway, attempts, eligibility,
                workflow, versions, recorder, finalize, mock(org.springframework.context.ApplicationEventPublisher.class), traits,
                new ExaminerTraitResponses(json), freshness, com.pte.TestClocks.fixed());
        direct = new ExaminerDirectScoreSubmitter(assignments, answers, scores, gateway, eligibility, content, attempts,
                entities, null, versions, sessions, workflow, traits, com.pte.TestClocks.fixed());
    }
    String review() { return versions.computeReviewVersion(answer, null, work, cohort, member, null, null); }
    Map<String, BigDecimal> input(int value) {
        var input = new LinkedHashMap<String, BigDecimal>(); input.put("CONTENT", BigDecimal.valueOf(value));
        input.put("PRONUNCIATION", null); input.put("ORAL_FLUENCY", null); return input;
    }
}
