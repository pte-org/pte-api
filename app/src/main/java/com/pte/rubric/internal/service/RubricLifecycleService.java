package com.pte.rubric.internal.service;

import tools.jackson.databind.JsonNode;
import com.pte.rubric.domain.RubricRevision;
import com.pte.rubric.domain.enums.RubricChannel;
import com.pte.rubric.domain.enums.RubricStatus;
import com.pte.rubric.dto.request.PublishRubricRequest;
import com.pte.rubric.dto.request.RejectRubricRequest;
import com.pte.rubric.dto.response.RubricResponse;
import com.pte.rubric.dto.response.RubricReadinessResponse;
import com.pte.rubric.internal.constant.RubricConstants;
import com.pte.rubric.internal.exception.RubricConcurrentModificationException;
import com.pte.rubric.internal.exception.RubricNotFoundException;
import com.pte.rubric.internal.mapper.RubricMapper;
import com.pte.rubric.internal.repository.RubricRevisionRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.exception.DomainException;
import com.pte.shared.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class RubricLifecycleService {

    private final RubricRevisionRepository repository;
    private final AuditLogService auditLogService;
    private final RubricAccessPolicy accessPolicy;
    private final RubricDocumentValidator documentValidator;
    private final RubricCanonicalizer canonicalizer;
    private final RubricDocumentProjectionService projectionService;

    @Autowired
    public RubricLifecycleService(RubricRevisionRepository repository, AuditLogService auditLogService,
            RubricAccessPolicy accessPolicy, RubricDocumentValidator documentValidator,
            RubricCanonicalizer canonicalizer, RubricDocumentProjectionService projectionService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
        this.accessPolicy = accessPolicy;
        this.documentValidator = documentValidator;
        this.canonicalizer = canonicalizer;
        this.projectionService = projectionService;
    }

    /** Compatibility constructor for focused unit tests. */
    public RubricLifecycleService(RubricRevisionRepository repository, AuditLogService auditLogService) {
        this(repository, auditLogService, new RubricAccessPolicy(auditLogService),
                new RubricDocumentValidator(JsonMapper.builder().build()),
                new RubricCanonicalizer(JsonMapper.builder().build()), null);
    }

    @Transactional
    public RubricResponse reject(UUID publicId, RejectRubricRequest request, CurrentUser caller) {
        RubricRevision revision = find(publicId);
        if (revision.getStatus() != RubricStatus.PENDING_APPROVAL) {
            com.pte.rubric.internal.exception.RubricValidationException exception =
                    new com.pte.rubric.internal.exception.RubricValidationException(
                            RubricConstants.RUBRIC_INVALID_TRANSITION);
            auditValidationFailure(caller, publicId, exception);
            throw exception;
        }
        accessPolicy.requireReview(caller, revision.getAuthorUserPublicId(), publicId);
        revision.setStatus(RubricStatus.DRAFT);
        revision.setRejectionReason(request.reason().trim());
        RubricRevision saved = repository.save(revision);
        audit(caller, RubricConstants.AUDIT_REJECTED, saved, request.reason());
        return RubricMapper.toResponse(saved);
    }

    @Transactional
    public RubricResponse publish(UUID publicId, PublishRubricRequest request, CurrentUser caller) {
        try {
            RubricRevision revision = find(publicId);
            if (revision.getStatus() != RubricStatus.PENDING_APPROVAL) {
                throw new com.pte.rubric.internal.exception.RubricValidationException(
                        RubricConstants.RUBRIC_INVALID_TRANSITION);
            }
            checkExpectedVersion(revision, request.expectedVersion());
            accessPolicy.requireReview(caller, revision.getAuthorUserPublicId(), publicId);
            if (request.reason() == null || request.reason().isBlank()) {
                throw new com.pte.rubric.internal.exception.RubricValidationException(
                        RubricConstants.RUBRIC_REASON_REQUIRED);
            }
            JsonNode document = documentValidator.parseAndValidate(revision.getDocumentJson());
            documentValidator.validatePublicationReadiness(revision.getDocumentJson(), revision.getTaskTypeKey(), false);
            String approvedJson = documentValidator.withAnchorApprovalStatus(revision.getDocumentJson(), "APPROVED");
            document = documentValidator.parseAndValidate(approvedJson);
            documentValidator.validatePublicationReadiness(approvedJson, revision.getTaskTypeKey(), true);
            String canonicalJson = canonicalizer.canonicalize(document);
            revision.setCanonicalHash(canonicalizer.sha256(canonicalJson));
            revision.setDocumentJson(canonicalJson);
            revision.setReferencesJson(documentValidator.extractArrayJson(canonicalJson, "references"));
            revision.setAnchorsJson(documentValidator.extractArrayJson(canonicalJson, "anchors"));
            revision.setRuntimeCompatibilityHash(canonicalizer.runtimeCompatibilityHash(document));
            if (projectionService != null) {
                projectionService.replaceChildren(revision, canonicalJson);
            }
            repository.findByTaskTypeKeyAndChannelAndStatusForUpdate(
                    revision.getTaskTypeKey(), RubricChannel.PRACTICE, RubricStatus.PUBLISHED)
                    .ifPresent(current -> {
                        current.setStatus(RubricStatus.RETIRED);
                        current.setRetiredAt(Instant.now());
                        repository.save(current);
                    });
            revision.setStatus(RubricStatus.PUBLISHED);
            revision.setPublisherUserPublicId(caller == null ? null : caller.userId());
            revision.setReviewerUserPublicId(caller == null ? null : caller.userId());
            revision.setPublishedAt(Instant.now());
            RubricRevision saved = saveOrTranslateConflict(revision);
            audit(caller, accessPolicy.isPlatformAdmin(caller)
                    ? RubricConstants.AUDIT_OVERRIDE_PUBLISHED : RubricConstants.AUDIT_PUBLISHED,
                    saved, request.reason());
            return RubricMapper.toResponse(saved);
        } catch (com.pte.rubric.internal.exception.RubricValidationException ex) {
            auditValidationFailure(caller, publicId, ex);
            throw ex;
        }
    }

    @Transactional
    public RubricResponse retire(UUID publicId, Long expectedVersion, CurrentUser caller) {
        RubricRevision revision = find(publicId);
        accessPolicy.requireReview(caller, revision.getAuthorUserPublicId(), publicId);
        if (revision.getStatus() != RubricStatus.PUBLISHED) {
            com.pte.rubric.internal.exception.RubricValidationException exception =
                    new com.pte.rubric.internal.exception.RubricValidationException(
                            RubricConstants.RUBRIC_INVALID_TRANSITION);
            auditValidationFailure(caller, publicId, exception);
            throw exception;
        }
        checkExpectedVersion(revision, expectedVersion);
        revision.setStatus(RubricStatus.RETIRED);
        revision.setRetiredAt(Instant.now());
        RubricRevision saved = repository.save(revision);
        audit(caller, RubricConstants.AUDIT_RETIRED, saved, "Retired version " + saved.getVersion());
        return RubricMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public RubricReadinessResponse readiness(UUID publicId) {
        return readiness(publicId, null);
    }

    @Transactional(readOnly = true)
    public RubricReadinessResponse readiness(UUID publicId, CurrentUser caller) {
        RubricRevision revision = find(publicId);
        try {
            documentValidator.validatePublicationReadiness(revision.getDocumentJson(), revision.getTaskTypeKey(), false);
            return new RubricReadinessResponse(true, java.util.List.of());
        } catch (RuntimeException ex) {
            String blocker = ex instanceof DomainException domainException && domainException.getCode() != null
                    ? domainException.getCode() : RubricConstants.RUBRIC_VALIDATION_FAILED;
            if (caller != null) {
                auditLogService.recordFailure(caller, RubricConstants.AUDIT_AGGREGATE_TYPE,
                        publicId.toString(), RubricConstants.AUDIT_VALIDATION_FAILED,
                        blocker);
            }
            return new RubricReadinessResponse(false, java.util.List.of(blocker));
        }
    }


    private RubricRevision find(UUID publicId) {
        return repository.findWithDetailsByPublicId(publicId).orElseThrow(RubricNotFoundException::new);
    }

    private void checkExpectedVersion(RubricRevision revision, Long expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(revision.getEntityVersion())) {
            throw new RubricConcurrentModificationException();
        }
    }

    private RubricRevision saveOrTranslateConflict(RubricRevision revision) {
        try {
            return repository.save(revision);
        } catch (DataIntegrityViolationException ex) {
            throw new RubricConcurrentModificationException();
        }
    }

    private void audit(CurrentUser caller, String action, RubricRevision revision, String summary) {
        if (caller != null) {
            auditLogService.record(caller, RubricConstants.AUDIT_AGGREGATE_TYPE,
                    String.valueOf(revision.getPublicId()), action, summary == null ? "" : summary);
        }
    }

    private void auditValidationFailure(CurrentUser caller, UUID publicId,
            com.pte.rubric.internal.exception.RubricValidationException exception) {
        if (caller != null) {
            String summary = exception.getMessage() == null ? "Rubric validation failed" : exception.getMessage();
            auditLogService.recordFailure(caller, RubricConstants.AUDIT_AGGREGATE_TYPE,
                    String.valueOf(publicId), RubricConstants.AUDIT_VALIDATION_FAILED,
                    summary.length() <= 500 ? summary : summary.substring(0, 500));
        }
    }
}
