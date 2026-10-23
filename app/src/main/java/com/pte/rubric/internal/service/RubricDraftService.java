package com.pte.rubric.internal.service;

import com.pte.rubric.domain.RubricRevision;
import com.pte.rubric.domain.enums.RubricChannel;
import com.pte.rubric.domain.enums.RubricStatus;
import com.pte.rubric.dto.request.CreateRubricRequest;
import com.pte.rubric.dto.request.ReplaceRubricDocumentRequest;
import com.pte.rubric.dto.response.RubricResponse;
import com.pte.rubric.internal.constant.RubricConstants;
import com.pte.rubric.internal.exception.RubricConcurrentModificationException;
import com.pte.rubric.internal.exception.RubricNotDraftException;
import com.pte.rubric.internal.exception.RubricNotFoundException;
import com.pte.rubric.internal.mapper.RubricMapper;
import com.pte.rubric.internal.repository.RubricRevisionRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class RubricDraftService {

    private final RubricRevisionRepository repository;
    private final AuditLogService auditLogService;
    private final RubricAccessPolicy accessPolicy;
    private final RubricDocumentValidator documentValidator;
    private final RubricDocumentProjectionService projectionService;

    @Autowired
    public RubricDraftService(RubricRevisionRepository repository, AuditLogService auditLogService,
            RubricAccessPolicy accessPolicy, RubricDocumentValidator documentValidator,
            RubricDocumentProjectionService projectionService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
        this.accessPolicy = accessPolicy;
        this.documentValidator = documentValidator;
        this.projectionService = projectionService;
    }

    /** Compatibility constructor for focused unit tests. */
    public RubricDraftService(RubricRevisionRepository repository, AuditLogService auditLogService) {
        this(repository, auditLogService, new RubricAccessPolicy(auditLogService),
                new RubricDocumentValidator(JsonMapper.builder().build()), null);
    }

    @Transactional
    public RubricResponse createDraft(CreateRubricRequest request, CurrentUser caller) {
        accessPolicy.requireDraftCreate(caller);
        try {
            String rubricKey = request.rubricKey().trim();
            repository.findAllByRubricKeyForUpdate(rubricKey);
            int nextVersion = repository.findMaxVersionByRubricKey(rubricKey) + 1;
            documentValidator.parseAndValidate(request.documentJson());
            RubricRevision revision = new RubricRevision();
            revision.setRubricKey(rubricKey);
            revision.setName(request.name().trim());
            revision.setTaskTypeKey(request.taskTypeKey().trim().toUpperCase(java.util.Locale.ROOT));
            revision.setChannel(RubricChannel.PRACTICE);
            revision.setVersion(nextVersion);
            revision.setStatus(RubricStatus.DRAFT);
            revision.setAuthorUserPublicId(caller == null ? null : caller.userId());
            revision.setDocumentJson(request.documentJson().trim());
            revision.setReferencesJson(documentValidator.extractArrayJson(request.documentJson(), "references"));
            revision.setAnchorsJson(documentValidator.extractArrayJson(request.documentJson(), "anchors"));
            projectionServiceIfPresent(revision);
            revision.setChangeReason(request.changeReason());
            RubricRevision saved = saveOrTranslateConflict(revision);
            audit(caller, RubricConstants.AUDIT_CREATED, saved, "Created draft version " + saved.getVersion());
            return RubricMapper.toResponse(saved);
        } catch (com.pte.rubric.internal.exception.RubricValidationException ex) {
            auditValidationFailure(caller, null, ex);
            throw ex;
        }
    }

    @Transactional
    public RubricResponse cloneToDraft(UUID sourcePublicId, CurrentUser caller) {
        accessPolicy.requireDraftCreate(caller);
        RubricRevision source = find(sourcePublicId);
        accessPolicy.requireCloneReadable(caller, source);
        repository.findAllByRubricKeyForUpdate(source.getRubricKey());
        RubricRevision revision = new RubricRevision();
        revision.setRubricKey(source.getRubricKey());
        revision.setName(source.getName());
        revision.setTaskTypeKey(source.getTaskTypeKey());
        revision.setChannel(source.getChannel());
        revision.setVersion(repository.findMaxVersionByRubricKey(source.getRubricKey()) + 1);
        revision.setStatus(RubricStatus.DRAFT);
        revision.setAuthorUserPublicId(caller == null ? null : caller.userId());
        revision.setDocumentJson(source.getDocumentJson());
        revision.setReferencesJson(source.getReferencesJson());
        revision.setAnchorsJson(source.getAnchorsJson());
        revision.setChangeReason("Cloned from revision " + source.getVersion());
        projectionServiceIfPresent(revision);
        RubricRevision saved = saveOrTranslateConflict(revision);
        audit(caller, RubricConstants.AUDIT_CLONED, saved, revision.getChangeReason());
        return RubricMapper.toResponse(saved);
    }

    @Transactional
    public RubricResponse replaceDocument(UUID publicId, ReplaceRubricDocumentRequest request, CurrentUser caller) {
        RubricRevision revision = find(publicId);
        requireDraft(revision);
        accessPolicy.requireDraftModify(caller, revision.getAuthorUserPublicId(), publicId);
        checkExpectedVersion(revision, request.expectedVersion());
        try {
            documentValidator.parseAndValidate(request.documentJson());
            revision.setDocumentJson(request.documentJson().trim());
            revision.setReferencesJson(documentValidator.extractArrayJson(request.documentJson(), "references"));
            revision.setAnchorsJson(documentValidator.extractArrayJson(request.documentJson(), "anchors"));
            projectionServiceIfPresent(revision);
            revision.setChangeReason(request.changeReason());
            RubricRevision saved = saveOrTranslateConflict(revision);
            audit(caller, RubricConstants.AUDIT_UPDATED, saved, "Updated draft version " + saved.getVersion());
            return RubricMapper.toResponse(saved);
        } catch (com.pte.rubric.internal.exception.RubricValidationException ex) {
            auditValidationFailure(caller, publicId, ex);
            throw ex;
        }
    }

    @Transactional
    public RubricResponse submitApproval(UUID publicId, CurrentUser caller) {
        RubricRevision revision = find(publicId);
        requireDraft(revision);
        accessPolicy.requireDraftModify(caller, revision.getAuthorUserPublicId(), publicId);
        try {
            documentValidator.parseAndValidate(revision.getDocumentJson());
            documentValidator.validatePublicationReadiness(revision.getDocumentJson(), revision.getTaskTypeKey(), false);
            revision.setDocumentJson(documentValidator.withAnchorApprovalStatus(revision.getDocumentJson(), "PENDING"));
            revision.setAnchorsJson(documentValidator.extractArrayJson(revision.getDocumentJson(), "anchors"));
            revision.setStatus(RubricStatus.PENDING_APPROVAL);
            revision.setSubmittedAt(Instant.now());
            RubricRevision saved = repository.save(revision);
            audit(caller, RubricConstants.AUDIT_SUBMITTED, saved, "Submitted version " + saved.getVersion());
            return RubricMapper.toResponse(saved);
        } catch (com.pte.rubric.internal.exception.RubricValidationException ex) {
            auditValidationFailure(caller, publicId, ex);
            throw ex;
        }
    }

    private RubricRevision find(UUID publicId) {
        return repository.findWithDetailsByPublicId(publicId).orElseThrow(RubricNotFoundException::new);
    }

    private void requireDraft(RubricRevision revision) {
        if (revision.getStatus() != RubricStatus.DRAFT) {
            throw new RubricNotDraftException();
        }
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
                    String.valueOf(revision.getPublicId()), action, truncate(summary));
        }
    }

    private void projectionServiceIfPresent(RubricRevision revision) {
        if (projectionService != null) {
            projectionService.replaceChildren(revision, revision.getDocumentJson());
        }
    }

    private String truncate(String value) {
        return value == null || value.length() <= 500 ? value : value.substring(0, 500);
    }

    private void auditValidationFailure(CurrentUser caller, UUID publicId,
            com.pte.rubric.internal.exception.RubricValidationException exception) {
        if (caller != null) {
            auditLogService.recordFailure(caller, RubricConstants.AUDIT_AGGREGATE_TYPE,
                    String.valueOf(publicId), RubricConstants.AUDIT_VALIDATION_FAILED, truncate(exception.getMessage()));
        }
    }
}
