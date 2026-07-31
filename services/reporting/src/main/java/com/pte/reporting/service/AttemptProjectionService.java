package com.pte.reporting.service;

import com.pte.reporting.domain.AnswerProjection;
import com.pte.reporting.domain.AttemptReport;
import com.pte.reporting.messaging.consumer.dto.AnswerSubmittedEvent;
import com.pte.reporting.messaging.consumer.dto.AttemptSubmittedEvent;
import com.pte.reporting.repository.AnswerProjectionRepository;
import com.pte.reporting.repository.AttemptReportRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Upsert logic for reporting's projection of exam-delivery's attempt
 * lifecycle — shared by the steady-state {@code AttemptIngestConsumer} and
 * the read-model rebuild orchestration (rabbitmq-outbox-migration Phase 9),
 * so both paths apply IDENTICAL logic and can never silently diverge.
 *
 * <p>The duplicate-insert fallback runs in its own {@code REQUIRES_NEW}
 * transaction (code-review finding, fixed): on Postgres, a failed statement
 * aborts the WHOLE surrounding transaction at the connection level, not just
 * the Java exception — catching {@link DataIntegrityViolationException}
 * without isolating it in its own transaction would poison the caller's
 * ambient {@code @Transactional} listener transaction (e.g. {@code
 * AttemptIngestConsumer}), causing the subsequent {@code ProcessedEvent}
 * save and commit to fail with an unrelated error instead of the intended
 * silent no-op.
 */
@Service
public class AttemptProjectionService {

    private final AttemptReportRepository attemptReportRepository;
    private final AnswerProjectionRepository answerProjectionRepository;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public AttemptProjectionService(AttemptReportRepository attemptReportRepository,
            AnswerProjectionRepository answerProjectionRepository, PlatformTransactionManager transactionManager) {
        this.attemptReportRepository = attemptReportRepository;
        this.answerProjectionRepository = answerProjectionRepository;
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager, definition);
    }

    public void ingestAttempt(AttemptSubmittedEvent event) {
        AttemptReport report = new AttemptReport();
        report.setAttemptPublicId(event.attemptPublicId());
        report.setSessionPublicId(event.sessionPublicId());
        report.setStudentPublicId(event.studentPublicId());
        report.setTenantId(event.tenantId());
        try {
            requiresNewTransactionTemplate.executeWithoutResult(status -> attemptReportRepository.save(report));
        } catch (DataIntegrityViolationException ex) {
            // Already ingested (steady-state redelivery, or a rebuild re-run) — no-op.
            // The REQUIRES_NEW transaction above already rolled back cleanly on its
            // own connection; the caller's ambient transaction is unaffected.
        }
    }

    public void ingestAnswer(AnswerSubmittedEvent event) {
        AnswerProjection answer = new AnswerProjection();
        answer.setAnswerPublicId(event.answerPublicId());
        answer.setAttemptPublicId(event.attemptPublicId());
        answer.setTaskType(event.taskType());
        try {
            requiresNewTransactionTemplate.executeWithoutResult(status -> answerProjectionRepository.save(answer));
        } catch (DataIntegrityViolationException ex) {
            // Already ingested — no-op (see ingestAttempt's note on transaction isolation).
        }
    }
}
