package com.pte.scoring.internal.rubric.service;

import com.pte.scoring.domain.ScoringRubricActivationAudit;
import com.pte.scoring.domain.ScoringRubricVersion;
import com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus;
import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.exception.RubricVersionException;
import com.pte.scoring.internal.rubric.repository.ScoringRubricActivationAuditRepository;
import com.pte.scoring.internal.rubric.repository.ScoringRubricVersionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Lifecycle of rubric versions: DRAFT -> PENDING -> ACTIVE (approval activates) | REJECTED; the previous ACTIVE
 * becomes RETIRED. ACTIVE and RETIRED versions are immutable here (first layer) and in the V32 trigger (second
 * layer). Role checks per action belong to the write API (phase 9).
 */
@Service
public class RubricVersionService {

    private static final Set<RubricVersionStatus> ACTIVATABLE = Set.of(RubricVersionStatus.DRAFT,
            RubricVersionStatus.PENDING);

    private final ScoringRubricVersionRepository versionRepository;
    private final ScoringRubricActivationAuditRepository auditRepository;
    private final RubricParametersValidator validator;
    private final Clock clock;

    public RubricVersionService(ScoringRubricVersionRepository versionRepository,
                                ScoringRubricActivationAuditRepository auditRepository,
                                RubricParametersValidator validator, Clock clock) {
        this.versionRepository = versionRepository;
        this.auditRepository = auditRepository;
        this.validator = validator;
        this.clock = clock;
    }

    /**
     * Validates the full parameters against the catalog before anything is stored. Two drafts created at once may
     * pick the same version number; the unique index rejects the second, which is reported as a 409.
     */
    @Transactional
    public ScoringRubricVersion createDraft(String parametersJson, int catalogVersion, UUID authorUserPublicId) {
        validator.validate(parametersJson, catalogVersion);
        int versionNo = versionRepository.findMaxVersionNo() + 1;
        try {
            return versionRepository.saveAndFlush(
                    ScoringRubricVersion.draft(versionNo, parametersJson, catalogVersion, authorUserPublicId));
        } catch (DataIntegrityViolationException concurrent) {
            throw concurrentChange(concurrent);
        }
    }

    @Transactional
    public ScoringRubricVersion submit(UUID versionPublicId) {
        ScoringRubricVersion version = load(versionPublicId);
        requireStatus(version, Set.of(RubricVersionStatus.DRAFT));
        version.markSubmitted(clock.instant());
        return versionRepository.save(version);
    }

    /**
     * Re-validates the candidate's parameters against the current catalog (a draft stays editable after it was
     * created, and the catalog may have moved on), then retires the current ACTIVE version with a native update and
     * flushes it BEFORE the new version is set ACTIVE, so the single-ACTIVE unique index never sees two ACTIVE rows;
     * then records exactly one audit row. An invalid candidate is rejected before anything changes.
     */
    @Transactional
    public ScoringRubricVersion activate(UUID versionPublicId, UUID approverUserPublicId, String diffSummary) {
        Objects.requireNonNull(approverUserPublicId, "approverUserPublicId");
        ScoringRubricVersion candidate = load(versionPublicId);
        requireStatus(candidate, ACTIVATABLE);
        validator.validate(candidate.getParameters(), candidate.getCatalogVersion());
        Instant now = clock.instant();
        UUID previousActive = versionRepository.findPublicIdByStatus(RubricVersionStatus.ACTIVE).orElse(null);
        ScoringRubricVersion activated;
        try {
            versionRepository.retireActive(now);
            versionRepository.flush();
            candidate.markActive(approverUserPublicId, now);
            activated = versionRepository.saveAndFlush(candidate);
        } catch (DataIntegrityViolationException concurrent) {
            // Another activation committed first: the single-ACTIVE index refused this one.
            throw concurrentChange(concurrent);
        }
        auditRepository.save(new ScoringRubricActivationAudit(activated.getPublicId(), previousActive,
                approverUserPublicId, now, diffSummary));
        return activated;
    }

    @Transactional
    public ScoringRubricVersion reject(UUID versionPublicId, UUID approverUserPublicId, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > RubricConstants.MAX_REJECTION_REASON_LENGTH) {
            throw new RubricVersionException(HttpStatus.BAD_REQUEST, RubricConstants.RUBRIC_REJECTION_REASON_INVALID);
        }
        ScoringRubricVersion version = load(versionPublicId);
        requireStatus(version, Set.of(RubricVersionStatus.PENDING));
        version.markRejected(approverUserPublicId, reason, clock.instant());
        return versionRepository.save(version);
    }

    private static RubricVersionException concurrentChange(DataIntegrityViolationException cause) {
        RubricVersionException conflict = new RubricVersionException(HttpStatus.CONFLICT,
                RubricConstants.RUBRIC_VERSION_CONCURRENT_CHANGE);
        conflict.initCause(cause);
        return conflict;
    }

    private ScoringRubricVersion load(UUID versionPublicId) {
        return versionRepository.findByPublicId(versionPublicId)
                .orElseThrow(() -> new RubricVersionException(HttpStatus.NOT_FOUND,
                        RubricConstants.RUBRIC_VERSION_NOT_FOUND));
    }

    private static void requireStatus(ScoringRubricVersion version, Set<RubricVersionStatus> allowed) {
        if (version.isImmutable()) {
            throw new RubricVersionException(HttpStatus.CONFLICT, RubricConstants.RUBRIC_VERSION_IMMUTABLE);
        }
        if (!allowed.contains(version.getStatus())) {
            throw new RubricVersionException(HttpStatus.CONFLICT, RubricConstants.RUBRIC_VERSION_STATUS_INVALID);
        }
    }
}
