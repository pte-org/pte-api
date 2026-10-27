package com.pte.attempt.internal.service.capability;

import com.pte.assessment.AssessmentService;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.internal.service.ExamContentService;
import com.pte.attempt.internal.service.cache.ExamItemView;
import com.pte.attempt.internal.config.CapabilityProperties;
import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.attempt.internal.dto.request.AttemptPreflightRequest;
import com.pte.attempt.internal.dto.request.ClientCapabilityManifest;
import com.pte.attempt.internal.dto.response.AttemptPreflightResponse;
import com.pte.attempt.internal.exception.AttemptNotFoundException;
import com.pte.attempt.internal.exception.ExamCapabilityException;
import com.pte.itembank.TaskTypeObservability;
import com.pte.session.SessionService;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Owns capability negotiation at the attempt boundary. It only consumes the
 * immutable runtime descriptors exposed by an exam summary or exam item;
 * it never joins the mutable question-type/profile catalog during delivery.
 * The pure manifest matching lives in {@link CapabilityManifestEvaluator};
 * this class performs the entitlement/assessment/content lookups and audit.
 */
@Service
public class CapabilityNegotiationService {

    private final SessionService sessionService;
    private final AssessmentService assessmentService;
    private final AuditLogService auditLogService;
    private final ExamContentService contentService;
    private final CapabilityManifestEvaluator evaluator;

    @Autowired
    public CapabilityNegotiationService(SessionService sessionService, AssessmentService assessmentService,
            CapabilityProperties properties, AuditLogService auditLogService,
            TaskTypeObservability observability, ExamContentService contentService) {
        this.sessionService = sessionService;
        this.assessmentService = assessmentService;
        this.auditLogService = auditLogService;
        this.contentService = contentService;
        this.evaluator = new CapabilityManifestEvaluator(properties, observability,
                new CapabilityManifestNormalizer());
    }

    /** Answer-free and side-effect-free preflight. */
    public AttemptPreflightResponse preflight(AttemptPreflightRequest request, CurrentUser caller) {
        var entitlement = sessionService.checkEntitlement(request.sessionPublicId(), caller.userId());
        requireCallerTenant(entitlement.tenantId(), caller);
        SnapshotResponse summary = assessmentService.getSummary(entitlement.snapshotPublicId());
        return evaluator.evaluateSummary(summary.items(), request.capabilityManifest()).toResponse();
    }

    /** Authoritative new-attempt check. Returns the normalized value persisted on the attempt. */
    public String authorizeStart(java.util.UUID sessionPublicId, java.util.UUID studentPublicId,
            ClientCapabilityManifest manifest, CurrentUser caller) {
        if (studentPublicId == null || caller == null || !studentPublicId.equals(caller.userId())) {
            throw new AttemptNotFoundException();
        }
        var entitlement = sessionService.checkEntitlement(sessionPublicId, studentPublicId);
        requireCallerTenant(entitlement.tenantId(), caller);
        SnapshotResponse summary = assessmentService.getSummary(entitlement.snapshotPublicId());
        CapabilityCheck check = evaluator.evaluateSummary(summary.items(), manifest);
        try {
            requireCompatible(check);
        } catch (ExamCapabilityException ex) {
            recordFailure(caller, summary.publicId(), ex.getCode());
            throw ex;
        }
        return check.fingerprint();
    }

    /**
     * Existing-attempt path: the ownership lookup has already completed before
     * this method is called. A supplied manifest is checked as well as the
     * stored decision; an omitted manifest reuses the stored decision.
     */
    public void authorizeExisting(ExamAttempt attempt, ClientCapabilityManifest manifest, CurrentUser caller) {
        List<ExamItemView> items = contentService.items(attempt);
        CapabilityCheck stored = evaluator.evaluateItems(items, attempt.getCapabilityFingerprint());
        try {
            requireCompatible(stored);
        } catch (ExamCapabilityException ex) {
            recordFailure(caller, attempt.getExamVersionPublicId(), ex.getCode());
            throw ex;
        }
        if (manifest != null) {
            CapabilityCheck supplied = evaluator.evaluateItems(items, manifest);
            try {
                requireCompatible(supplied);
            } catch (ExamCapabilityException ex) {
                recordFailure(caller, attempt.getExamVersionPublicId(), ex.getCode());
                throw ex;
            }
            if (CapabilityManifestNormalizer.isBlank(attempt.getCapabilityFingerprint())) {
                attempt.setCapabilityFingerprint(supplied.fingerprint());
            }
        }
    }

    /** Same verification with audit context for the authoritative start path. */
    public void verifyForStart(List<ExamItemView> items, UUID examVersionPublicId, String fingerprint,
            CurrentUser caller) {
        try {
            requireCompatible(evaluator.evaluateItems(items, fingerprint));
        } catch (ExamCapabilityException ex) {
            recordFailure(caller, examVersionPublicId, ex.getCode());
            throw ex;
        }
    }

    /** Re-used before every task is returned, including resume and next-task. */
    public void assertStoredCapabilities(ExamAttempt attempt) {
        assertStoredCapabilities(attempt, null);
    }

    /** Re-used before every task is returned and records a blocked delivery. */
    public void assertStoredCapabilities(ExamAttempt attempt, CurrentUser caller) {
        try {
            requireCompatible(evaluator.evaluateItems(contentService.items(attempt),
                    attempt.getCapabilityFingerprint()));
        } catch (ExamCapabilityException ex) {
            recordFailure(caller, attempt.getExamVersionPublicId(), ex.getCode());
            throw ex;
        }
    }

    /** Records a start-time runtime rejection when the exam content fails to load. */
    public void recordBlockedDelivery(CurrentUser caller, java.util.UUID attemptPublicId, String code) {
        recordFailure(caller, "EXAM_ATTEMPT", attemptPublicId, code);
    }

    private void requireCompatible(CapabilityCheck check) {
        if (!check.canStart()) {
            throw new ExamCapabilityException(check.code(), check.missingCapabilities());
        }
    }

    private void recordFailure(CurrentUser caller, java.util.UUID snapshotPublicId, String code) {
        recordFailure(caller, "EXAM_SNAPSHOT", snapshotPublicId, code);
    }

    private void recordFailure(CurrentUser caller, String aggregateType, java.util.UUID aggregateId, String code) {
        if (auditLogService != null && caller != null && aggregateId != null) {
            auditLogService.recordFailure(caller, aggregateType, aggregateId.toString(),
                    AttemptConstants.RUNTIME_CONTRACT_FAILURE_AUDIT_ACTION,
                    AttemptConstants.RUNTIME_CONTRACT_FAILURE_AUDIT_SUMMARY + ": " + code);
        }
    }

    private ExamCapabilityException configurationException() {
        return new ExamCapabilityException(AttemptConstants.EXAM_CONFIGURATION_NOT_COMPATIBLE, List.of());
    }

    private void requireCallerTenant(java.util.UUID entitlementTenantId, CurrentUser caller) {
        if (caller == null || caller.tenantId() == null || entitlementTenantId == null
                || !caller.tenantId().equals(entitlementTenantId)) {
            throw new AttemptNotFoundException();
        }
    }
}
