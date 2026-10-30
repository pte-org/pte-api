package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.ExaminerEvaluation;
import com.pte.scoring.domain.enums.ExaminerAnswerScoreStatus;
import com.pte.scoring.domain.enums.ExaminerDecisionKind;
import com.pte.scoring.domain.enums.ScoringMethod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * One table holds the AI evaluation and the examiner evaluation of an answer: at most one row per source, the
 * examiner row going from draft to submitted in place.
 */
@DataJpaTest
class AnswerEvaluationTableTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID attemptId = UUID.randomUUID();
    private final UUID answerId = UUID.randomUUID();

    @Autowired
    private AiEvaluationRepository aiRepository;
    @Autowired
    private ExaminerEvaluationRepository examinerRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void anAnswerHasOneAiRowAndOneExaminerRowInTheSameTable() {
        aiRepository.saveAndFlush(ai(answerId));
        examinerRepository.saveAndFlush(draft(answerId));

        assertThat(jdbc.queryForObject("select count(*) from answer_evaluations where answer_public_id = ?",
                Integer.class, answerId)).isEqualTo(2);
        assertThat(aiRepository.findByAnswerPublicId(answerId)).get().isInstanceOf(AiEvaluation.class);
        assertThat(examinerRepository.findByAnswerPublicIdAndTenantId(answerId, tenantId)).get()
                .isInstanceOf(ExaminerEvaluation.class);
    }

    @Test
    void aSecondAiRowForTheSameAnswerIsRejected() {
        aiRepository.saveAndFlush(ai(answerId));

        assertThatThrownBy(() -> aiRepository.saveAndFlush(ai(answerId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aSecondExaminerRowForTheSameAnswerIsRejected() {
        examinerRepository.saveAndFlush(draft(answerId));

        assertThatThrownBy(() -> examinerRepository.saveAndFlush(draft(answerId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDraftIsSubmittedInPlaceAndOnlyThenCountsAsAScore() {
        ExaminerEvaluation draft = examinerRepository.saveAndFlush(draft(answerId));
        assertThat(examinerRepository.findSubmitted(answerId, tenantId)).isEmpty();
        assertThat(examinerRepository.findSubmittedBySession(tenantId, sessionId)).isEmpty();

        draft.submit(70, Instant.parse("2026-10-24T10:00:00Z"), ExaminerDecisionKind.MANUAL, null, null, "rv-1",
                UUID.randomUUID(), "fp");
        examinerRepository.saveAndFlush(draft);

        assertThat(examinerRepository.findSubmitted(answerId, tenantId)).get()
                .extracting(ExaminerEvaluation::getStatus).isEqualTo(ExaminerAnswerScoreStatus.SUBMITTED);
        assertThat(examinerRepository.findSubmittedBySession(tenantId, sessionId)).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from answer_evaluations where answer_public_id = ?",
                Integer.class, answerId)).isEqualTo(1);
    }

    @Test
    void aiRowsAreFoundBySessionWithoutReturningExaminerRows() {
        aiRepository.saveAndFlush(ai(answerId));
        examinerRepository.saveAndFlush(draft(answerId));

        assertThat(aiRepository.findBySessionPublicIdAndTenantId(sessionId, tenantId))
                .hasSize(1).allSatisfy(row -> assertThat(row).isInstanceOf(AiEvaluation.class));
    }

    private AiEvaluation ai(UUID answer) {
        return new AiEvaluation(tenantId, sessionId, attemptId, answer, UUID.randomUUID(), UUID.randomUUID(),
                "READ_ALOUD", "SPEAKING", ScoringMethod.AI_SPEECH);
    }

    private ExaminerEvaluation draft(UUID answer) {
        return ExaminerEvaluation.draft(tenantId, sessionId, attemptId, answer, UUID.randomUUID(), 5L, 60, null,
                ExaminerDecisionKind.MANUAL, "rv-1", null, Instant.parse("2026-10-24T09:00:00Z"));
    }
}
