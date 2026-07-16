package com.aptis.modules.examdelivery.service;

import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.examdelivery.constant.ExamDeliveryConstants;
import com.aptis.modules.examdelivery.domain.ExamAttempt;
import com.aptis.modules.examdelivery.dto.ExtendTimeRequest;
import com.aptis.modules.examdelivery.dto.ExtendTimeResponse;
import com.aptis.modules.examdelivery.dto.FlagViolationResponse;
import com.aptis.modules.examdelivery.dto.ProctorActionResponse;
import com.aptis.modules.examdelivery.repository.ExamAttemptRepository;
import com.aptis.modules.examoperations.domain.Exam;
import com.aptis.modules.examoperations.domain.SessionTimeOverride;
import com.aptis.modules.examoperations.repository.SessionTimeOverrideRepository;
import com.aptis.modules.proctor.domain.ProctorActionType;
import com.aptis.modules.proctor.service.ProctorActionAuditService;
import com.aptis.modules.proctor.service.ProctorStatusPushService;
import com.aptis.modules.questionbank.domain.Skill;

/**
 * Owns the 3 per-attempt privileged proctor actions (force-submit, extend-time,
 * flag-violation). Lives in examdelivery — not `proctor` — because these mutate
 * {@link ExamAttempt}, which examdelivery owns; the audit write and realtime push are
 * delegated to `proctor` (an already-established, one-directional dependency).
 */
@Service
public class ProctorActionService {

    private final ExamAttemptRepository examAttemptRepository;
    private final SessionTimeOverrideRepository timeOverrideRepository;
    private final ProctorActionAuditService auditService;
    private final ProctorStatusPushService pushService;

    public ProctorActionService(
            ExamAttemptRepository examAttemptRepository,
            SessionTimeOverrideRepository timeOverrideRepository,
            ProctorActionAuditService auditService,
            ProctorStatusPushService pushService) {
        this.examAttemptRepository = examAttemptRepository;
        this.timeOverrideRepository = timeOverrideRepository;
        this.auditService = auditService;
        this.pushService = pushService;
    }

    @Transactional
    public ProctorActionResponse forceSubmit(Long attemptId, JwtPrincipal proctorPrincipal) {
        ExamAttempt attempt = loadAssignedAttemptForUpdate(attemptId, proctorPrincipal);

        boolean transitioned = attempt.forceSubmit();
        if (transitioned) {
            Map<String, Object> details = new HashMap<>();
            details.put("previousStatus", "NON_SUBMITTED");
            auditService.record(proctorPrincipal.userId(), attempt.getId(), ProctorActionType.FORCE_SUBMIT, details);
            pushStatus(attempt);
        }

        return new ProctorActionResponse(attempt.getId(), attempt.getStatus().name(), !transitioned);
    }

    @Transactional
    public ExtendTimeResponse extendTime(Long attemptId, ExtendTimeRequest request, JwtPrincipal proctorPrincipal) {
        ExamAttempt attempt = loadAssignedAttemptForUpdate(attemptId, proctorPrincipal);

        Map<String, Object> details = new HashMap<>();
        details.put("addedMinutes", request.minutes());

        ExtendTimeResponse response;
        if (request.skill() != null && request.part() != null) {
            Skill skill = parseSkill(request.skill());
            Exam exam = attempt.getExam();
            SessionTimeOverride override = timeOverrideRepository
                    .findByExamIdAndSkillAndPart(exam.getId(), skill, request.part())
                    .orElseGet(() -> SessionTimeOverride.create(exam, skill, request.part(), 0));
            int newTotal = override.getOverrideMinutes() + request.minutes();
            override.setOverrideMinutes(newTotal);
            timeOverrideRepository.save(override);

            details.put("skill", skill.name());
            details.put("part", request.part());
            details.put("newTotalMinutes", newTotal);
            response = new ExtendTimeResponse(attempt.getId(), skill.name(), request.part(), request.minutes(), newTotal);
        } else {
            attempt.extendTime(request.minutes());
            details.put("newTotalMinutes", attempt.getExtraTimeMinutes());
            response = new ExtendTimeResponse(attempt.getId(), null, null, request.minutes(), attempt.getExtraTimeMinutes());
        }

        auditService.record(proctorPrincipal.userId(), attempt.getId(), ProctorActionType.EXTEND_TIME, details);
        pushStatus(attempt);
        return response;
    }

    @Transactional
    public FlagViolationResponse flagViolation(Long attemptId, String reason, JwtPrincipal proctorPrincipal) {
        ExamAttempt attempt = loadAssignedAttemptForUpdate(attemptId, proctorPrincipal);

        attempt.flag();

        Map<String, Object> details = new HashMap<>();
        details.put("reason", reason);
        auditService.record(proctorPrincipal.userId(), attempt.getId(), ProctorActionType.FLAG_VIOLATION, details);
        pushStatus(attempt);

        return new FlagViolationResponse(attempt.getId(), attempt.getFlagged());
    }

    // ── Private helpers ──────────────────────────────────────────────

    /**
     * CRITICAL Design Constraint: authorization is verified against the row's CURRENT,
     * LOCKED state, inside the same transaction as the mutation — never a separate
     * check-then-act step. {@code findByIdAndAssignedProctorForUpdate} puts the proctor
     * assignment check in the query's WHERE clause itself (not a follow-up read of the lazy
     * {@code exam} relation after the lock), so a concurrent {@code unassignProctor()} can't
     * race an in-flight action: the join means Postgres locks the Exam row too. This is also
     * what makes a concurrent student-submit vs. proctor-force-submit race resolve safely:
     * the loser of the lock observes the winner's already-committed state and reacts
     * accordingly (student's submit() throws RESOURCE_CONFLICT; proctor's forceSubmit() is
     * idempotently a no-op).
     */
    private ExamAttempt loadAssignedAttemptForUpdate(Long attemptId, JwtPrincipal proctorPrincipal) {
        return examAttemptRepository.findByIdAndAssignedProctorForUpdate(attemptId, proctorPrincipal.userId())
                .orElseThrow(() -> new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND, ExamDeliveryConstants.ATTEMPT_NOT_FOUND + attemptId));
    }

    private Skill parseSkill(String skill) {
        try {
            return Skill.valueOf(skill.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid skill: " + skill);
        }
    }

    private void pushStatus(ExamAttempt attempt) {
        pushService.pushStatus(
                attempt.getExamId(),
                attempt.getId(),
                attempt.getStudentId(),
                attempt.getStatus().name(),
                attempt.getLastHeartbeatAt(),
                attempt.getSubmittedAt() != null ? attempt.getSubmittedAt().atOffset(ZoneOffset.UTC) : null);
    }
}
