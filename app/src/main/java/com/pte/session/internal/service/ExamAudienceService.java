package com.pte.session.internal.service;

import com.pte.enrollment.EnrollmentModuleService;
import com.pte.session.domain.ExamSessionAudienceSource;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.AudienceSourceType;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.internal.constant.SessionConstants;
import com.pte.session.internal.dto.request.AudienceSourceRequest;
import com.pte.session.internal.dto.response.AudiencePreviewResponse;
import com.pte.session.internal.dto.response.AudienceSourceResponse;
import com.pte.session.internal.exception.AudienceSourceNotFoundException;
import com.pte.session.internal.exception.AudienceSourceAlreadyCoveredException;
import com.pte.session.internal.exception.ExamDraftNotEditableException;
import com.pte.session.internal.repository.ExamSessionAudienceSourceRepository;
import com.pte.session.internal.repository.ExamRegistrationRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


/** Manages the audience sources of a draft exam and previews who they resolve to, without persisting. */
@Service
public class ExamAudienceService {

    private final ExamSessionAudienceSourceRepository sourceRepository;
    private final ExamRegistrationRepository registrationRepository;
    private final ExamSessionAccess access;
    private final AudienceResolver resolver;
    private final EnrollmentModuleService enrollmentModuleService;

    public ExamAudienceService(ExamSessionAudienceSourceRepository sourceRepository,
            ExamRegistrationRepository registrationRepository,
            ExamSessionAccess access,
            AudienceResolver resolver,
            EnrollmentModuleService enrollmentModuleService) {
        this.sourceRepository = sourceRepository;
        this.registrationRepository = registrationRepository;
        this.access = access;
        this.resolver = resolver;
        this.enrollmentModuleService = enrollmentModuleService;
    }

    @Transactional
    public AudienceSourceResponse addSource(UUID sessionPublicId, AudienceSourceRequest request, CurrentUser caller) {
        ExamSession session = access.ownedForEdit(sessionPublicId, caller);
        access.assertTenantActive(session.getTenantId());
        List<ExamSessionAudienceSource> existing = new ArrayList<>(
                sourceRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()));
        int sizeBefore = existing.size();
        ExamSessionAudienceSource source = addToSession(session, existing, request, caller);
        if (existing.size() > sizeBefore) {
            access.recordExamAudit(caller, session, SessionConstants.EVENT_EXAM_AUDIENCE_SOURCE_ADDED,
                    request.sourceType().name());
        }
        return toSourceResponse(source);
    }

    /**
     * Adds many sources in one transaction: either every new source is saved or none is. Duplicates (within the
     * request or already on the session) are idempotent; a source covered by a wider one fails the whole batch.
     */
    @Transactional
    public List<AudienceSourceResponse> addSources(UUID sessionPublicId, List<AudienceSourceRequest> requests,
            CurrentUser caller) {
        ExamSession session = access.ownedForEdit(sessionPublicId, caller);
        access.assertTenantActive(session.getTenantId());
        List<ExamSessionAudienceSource> existing = new ArrayList<>(
                sourceRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()));
        int sizeBefore = existing.size();
        List<AudienceSourceResponse> responses = new ArrayList<>();
        for (AudienceSourceRequest request : new LinkedHashSet<>(requests)) {
            responses.add(toSourceResponse(addToSession(session, existing, request, caller)));
        }
        int added = existing.size() - sizeBefore;
        if (added > 0) {
            access.recordExamAudit(caller, session, SessionConstants.EVENT_EXAM_AUDIENCE_SOURCE_ADDED,
                    added + " sources");
        }
        return responses;
    }

    /** Returns the matching existing source, or validates and saves a new one (appending it to {@code existing}). */
    private ExamSessionAudienceSource addToSession(ExamSession session, List<ExamSessionAudienceSource> existing,
            AudienceSourceRequest request, CurrentUser caller) {
        validateSourceOwnership(request, caller.tenantId());
        Optional<ExamSessionAudienceSource> duplicate = existing.stream()
                .filter(source -> source.getSourceType() == request.sourceType()
                        && source.getSourcePublicId().equals(request.sourcePublicId()))
                .findFirst();
        if (duplicate.isPresent()) {
            return duplicate.get();
        }
        if (isCoveredByExistingSource(existing, request, caller.tenantId())) {
            throw new AudienceSourceAlreadyCoveredException();
        }
        ExamSessionAudienceSource source = new ExamSessionAudienceSource();
        source.setSession(session);
        source.setTenantId(caller.tenantId());
        source.setSourceType(request.sourceType());
        source.setSourcePublicId(request.sourcePublicId());
        source.setCreatedBy(caller.userId());
        ExamSessionAudienceSource saved = sourceRepository.save(source);
        existing.add(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<AudienceSourceResponse> listSources(UUID sessionPublicId, CurrentUser caller) {
        ExamSession session = access.owned(sessionPublicId, caller);
        return sourceRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                .map(this::toSourceResponse).toList();
    }

    @Transactional
    public void removeSource(UUID sessionPublicId, UUID sourcePublicId, CurrentUser caller) {
        ExamSession session = access.ownedForEdit(sessionPublicId, caller);
        access.assertTenantActive(session.getTenantId());
        ExamSessionAudienceSource source = sourceRepository.findByPublicIdAndSessionId(sourcePublicId, session.getId())
                .orElseThrow(AudienceSourceNotFoundException::new);
        sourceRepository.delete(source);
        registrationRepository.deleteBySessionId(session.getId());
        access.recordExamAudit(caller, session, SessionConstants.EVENT_EXAM_AUDIENCE_SOURCE_REMOVED,
                source.getSourceType().name());
    }

    @Transactional
    public AudiencePreviewResponse previewAudience(UUID sessionPublicId, CurrentUser caller) {
        ExamSession session = access.owned(sessionPublicId, caller);
        if (session.getStatus() != SessionStatus.DRAFT) {
            throw new ExamDraftNotEditableException();
        }
        return resolver.resolve(session, false);
    }

    /**
     * A new source may not sit inside the scope of one already added (program > class > student).
     * The reverse is allowed: a wider source added later simply overlaps the narrower one, and
     * the audience is de-duplicated by student when it is resolved.
     */
    private boolean isCoveredByExistingSource(List<ExamSessionAudienceSource> existing, AudienceSourceRequest request,
            UUID tenantId) {
        return switch (request.sourceType()) {
            case STUDENT -> existing.stream()
                    .filter(source -> source.getSourceType() != AudienceSourceType.STUDENT)
                    .anyMatch(source -> resolver.resolveSource(source, tenantId).contains(request.sourcePublicId()));
            case CLASS -> {
                UUID programPublicId = enrollmentModuleService
                        .findProgramPublicIdOfClass(tenantId, request.sourcePublicId());
                yield existing.stream().anyMatch(source -> source.getSourceType() == AudienceSourceType.PROGRAM
                        && source.getSourcePublicId().equals(programPublicId));
            }
            case PROGRAM -> false;
        };
    }

    private void validateSourceOwnership(AudienceSourceRequest request, UUID tenantId) {
        List<UUID> resolved = switch (request.sourceType()) {
            case CLASS -> enrollmentModuleService.findActiveStudentPublicIds(tenantId, request.sourcePublicId());
            case PROGRAM -> enrollmentModuleService.findActiveStudentPublicIdsByProgram(tenantId, request.sourcePublicId());
            case STUDENT -> resolver.activeStudentSource(request.sourcePublicId(), tenantId);
        };
        if (request.sourceType() == AudienceSourceType.STUDENT && resolved.isEmpty()) {
            throw new AudienceSourceNotFoundException();
        }
    }

    private AudienceSourceResponse toSourceResponse(ExamSessionAudienceSource source) {
        return new AudienceSourceResponse(source.getPublicId(), source.getSourceType(), source.getSourcePublicId());
    }
}
