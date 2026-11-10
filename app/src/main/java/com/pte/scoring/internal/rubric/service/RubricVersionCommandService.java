package com.pte.scoring.internal.rubric.service;

import com.pte.scoring.domain.ScoringRubricVersion;
import com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus;
import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.exception.RubricVersionException;
import com.pte.scoring.internal.rubric.dto.RubricVersionResponse;
import com.pte.scoring.internal.rubric.repository.ScoringRubricVersionRepository;
import com.pte.scoring.internal.rubric.workflow.RubricActivationWorkflow;
import com.pte.scoring.internal.rubric.workflow.RubricWorkflowPolicy;
import com.pte.scoring.internal.rubric.workflow.dto.RubricStatusRequest;
import com.pte.scoring.internal.rubric.workflow.dto.RubricWriteRequest;
import com.pte.shared.security.CurrentUser;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;
import java.util.UUID;

/** Write boundary: action-specific authorization, immutable-version guard and optimistic draft edits. */
@Service
public class RubricVersionCommandService {
    private final ScoringRubricVersionRepository versions;
    private final RubricVersionService lifecycle;
    private final RubricParametersValidator validator;
    private final RubricActivationWorkflow activation;

    public RubricVersionCommandService(ScoringRubricVersionRepository versions, RubricVersionService lifecycle,
            RubricParametersValidator validator, RubricActivationWorkflow activation) {
        this.versions = versions;
        this.lifecycle = lifecycle;
        this.validator = validator;
        this.activation = activation;
    }

    @Transactional
    public RubricVersionResponse createDraft(RubricWriteRequest request, CurrentUser caller) {
        RubricWorkflowPolicy.author(caller);
        return RubricResponseMapper.toVersionResponse(lifecycle.createDraft(parameters(request),
                RubricConstants.CATALOG_VERSION, caller.userId()));
    }

    @Transactional
    public RubricVersionResponse updateDraft(UUID id, RubricWriteRequest request, CurrentUser caller) {
        RubricWorkflowPolicy.author(caller);
        var version = load(id);
        mutable(version, Set.of(RubricVersionStatus.DRAFT, RubricVersionStatus.REJECTED));
        if (request.expectedLockVersion() == null || request.expectedLockVersion() != version.getLockVersion()) {
            throw new RubricVersionException(HttpStatus.CONFLICT, RubricConstants.RUBRIC_VERSION_CONCURRENT_CHANGE);
        }
        String parameters = parameters(request);
        validator.validate(parameters, RubricConstants.CATALOG_VERSION);
        version.reviseDraft(parameters, RubricConstants.CATALOG_VERSION);
        version.recordUpdater(caller.userId());
        return save(version);
    }

    /** Activation owns a fresh transaction/retry; other actions use the transactional write method below. */
    public RubricVersionResponse activate(UUID id, RubricStatusRequest request, CurrentUser caller) {
        return RubricResponseMapper.toVersionResponse(activation.activate(id, request, caller));
    }

    @Transactional
    public RubricVersionResponse changeStatus(UUID id, RubricStatusRequest request, CurrentUser caller) {
        RubricWorkflowPolicy.author(caller);
        var version = load(id);
        return switch (request.action()) {
            case SUBMIT -> {
                var submitted = lifecycle.submit(id);
                submitted.recordUpdater(caller.userId());
                yield RubricResponseMapper.toVersionResponse(submitted);
            }
            case REJECT -> {
                caller.requireAnyRole(com.pte.shared.security.SecurityRoles.PLATFORM_ADMIN,
                        com.pte.shared.security.SecurityRoles.ACADEMIC_MANAGER);
                yield RubricResponseMapper.toVersionResponse(lifecycle.reject(id, caller.userId(), request.reason()));
            }
            case DISCARD -> {
                mutable(version, Set.of(RubricVersionStatus.DRAFT));
                version.setDeleted(true);
                yield save(version);
            }
            case ACTIVATE -> throw new RubricVersionException(HttpStatus.CONFLICT, RubricConstants.RUBRIC_VERSION_STATUS_INVALID);
        };
    }

    private RubricVersionResponse save(ScoringRubricVersion version) {
        try {
            return RubricResponseMapper.toVersionResponse(versions.saveAndFlush(version));
        } catch (OptimisticLockingFailureException conflict) {
            throw new RubricVersionException(HttpStatus.CONFLICT, RubricConstants.RUBRIC_VERSION_CONCURRENT_CHANGE);
        }
    }

    private ScoringRubricVersion load(UUID id) {
        return versions.findByPublicId(id).filter(version -> !version.isDeleted()).orElseThrow(() ->
                new RubricVersionException(HttpStatus.NOT_FOUND, RubricConstants.RUBRIC_VERSION_NOT_FOUND));
    }

    private static String parameters(RubricWriteRequest request) {
        if (request.parameters() == null) throw new RubricVersionException(HttpStatus.BAD_REQUEST, RubricConstants.RUBRIC_PARAMETERS_MALFORMED);
        return request.parameters().toString();
    }

    private static void mutable(ScoringRubricVersion version, Set<RubricVersionStatus> allowed) {
        if (version.isImmutable()) throw new RubricVersionException(HttpStatus.CONFLICT, RubricConstants.RUBRIC_VERSION_IMMUTABLE);
        if (!allowed.contains(version.getStatus())) throw new RubricVersionException(HttpStatus.CONFLICT, RubricConstants.RUBRIC_VERSION_STATUS_INVALID);
    }
}
