package com.pte.session.internal.service;

import com.pte.enrollment.EnrollmentModuleService;
import com.pte.identity.IdentityService;
import com.pte.session.domain.ExamSessionAudienceSource;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.ExamRegistration;
import com.pte.session.domain.enums.AudienceDecisionReason;
import com.pte.session.domain.enums.AudienceMemberStatus;
import com.pte.session.domain.enums.ReusePolicy;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.internal.dto.response.AudienceMemberResponse;
import com.pte.session.internal.dto.response.AudiencePreviewResponse;
import com.pte.session.internal.repository.ExamSessionAudienceSourceRepository;
import com.pte.session.internal.repository.ExamRegistrationRepository;
import com.pte.shared.StartedAttemptLookup;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;


/** Turns a session's audience sources into registration decisions: who is eligible, who is excluded and why. */
@Component
class AudienceResolver {

    private final ExamSessionAudienceSourceRepository sourceRepository;
    private final ExamRegistrationRepository registrationRepository;
    private final EnrollmentModuleService enrollmentModuleService;
    private final IdentityService identityService;
    private final StartedAttemptLookup startedAttemptLookup;

    AudienceResolver(ExamSessionAudienceSourceRepository sourceRepository,
            ExamRegistrationRepository registrationRepository,
            EnrollmentModuleService enrollmentModuleService,
            IdentityService identityService,
            StartedAttemptLookup startedAttemptLookup) {
        this.sourceRepository = sourceRepository;
        this.registrationRepository = registrationRepository;
        this.enrollmentModuleService = enrollmentModuleService;
        this.identityService = identityService;
        this.startedAttemptLookup = startedAttemptLookup;
    }

    AudiencePreviewResponse resolve(ExamSession session, boolean persist) {
        List<ExamSessionAudienceSource> sources = sourceRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        Map<UUID, LinkedHashSet<String>> provenance = new LinkedHashMap<>();
        int totalResolved = 0;
        for (ExamSessionAudienceSource source : sources) {
            List<UUID> students = resolveSource(source, session.getTenantId());
            totalResolved += students.size();
            for (UUID student : students) {
                provenance.computeIfAbsent(student, ignored -> new LinkedHashSet<>())
                        .add(source.getSourceType().name() + ":" + source.getSourcePublicId());
            }
        }

        List<UUID> studentIds = new ArrayList<>(provenance.keySet());
        List<ExamRegistration> priorEnrollments = studentIds.isEmpty() ? List.of()
                : registrationRepository.findEnrollmentsOfStudents(session.getTenantId(), studentIds);
        Map<UUID, List<ExamRegistration>> priorByStudent = priorEnrollments.stream()
                .filter(enrollment -> !enrollment.getSession().getPublicId().equals(session.getPublicId()))
                .collect(Collectors.groupingBy(ExamRegistration::getStudentPublicId));
        List<UUID> sameSeriesSessionIds = priorEnrollments.stream()
                .map(ExamRegistration::getSession)
                .filter(prior -> !prior.getPublicId().equals(session.getPublicId()))
                .filter(prior -> session.getSeriesKey() != null && session.getSeriesKey().equals(prior.getSeriesKey()))
                .map(ExamSession::getPublicId).distinct().toList();
        Set<UUID> startedStudents = session.getReusePolicy() == ReusePolicy.EXCLUDE_STARTED_IN_SERIES
                ? startedAttemptLookup.findStartedStudentPublicIds(session.getTenantId(), sameSeriesSessionIds, studentIds)
                : Set.of();

        List<ExamRegistration> members = new ArrayList<>();
        for (UUID student : studentIds) {
            ExamRegistration member = new ExamRegistration();
            member.setSession(session);
            member.setTenantId(session.getTenantId());
            member.setLicenseKey(session.getLicenseKey());
            member.setStudentPublicId(student);
            member.setSourceSummary(String.join(", ", provenance.get(student)));
            member.setStatus(AudienceMemberStatus.ELIGIBLE);
            List<ExamRegistration> prior = priorByStudent.getOrDefault(student, List.of());
            ExamRegistration conflict = prior.stream().filter(enrollment -> !enrollment.getSession().getStatus().equals(SessionStatus.CANCELLED))
                    .filter(enrollment -> session.getSeriesKey() != null
                            && session.getSeriesKey().equals(enrollment.getSession().getSeriesKey()))
                    .findFirst().orElse(null);
            if (session.getReusePolicy() == ReusePolicy.EXCLUDE_ASSIGNED_IN_SERIES && conflict != null) {
                exclude(member, AudienceDecisionReason.ALREADY_ASSIGNED, conflict);
            } else if (session.getReusePolicy() == ReusePolicy.EXCLUDE_STARTED_IN_SERIES && startedStudents.contains(student)) {
                exclude(member, AudienceDecisionReason.ALREADY_STARTED, conflict);
            } else if (session.getReusePolicy() == ReusePolicy.BLOCK_ON_SCHEDULE_OVERLAP) {
                ExamRegistration overlap = prior.stream().filter(enrollment -> overlaps(session, enrollment.getSession()))
                        .findFirst().orElse(null);
                if (overlap != null) exclude(member, AudienceDecisionReason.SCHEDULE_OVERLAP, overlap);
            }
            members.add(member);
        }

        List<ExamRegistration> eligible = members.stream()
                .filter(member -> member.getStatus() == AudienceMemberStatus.ELIGIBLE).toList();
        boolean capacityReady = eligible.size() <= session.getCapacity();
        if (!capacityReady) {
            for (int index = session.getCapacity(); index < eligible.size(); index++) {
                ExamRegistration member = eligible.get(index);
                member.setStatus(AudienceMemberStatus.EXCLUDED);
                member.setReason(AudienceDecisionReason.CAPACITY_EXCEEDED);
            }
        }
        if (persist) {
            // Preflight is intentionally repeatable: the UI calls it before generation and
            // generation calls it again. Replacing every row with a new entity instance can
            // make Hibernate execute the INSERT before the pending DELETE and violate the
            // session/student unique constraint. Reuse the existing rows and only add/remove
            // the delta so repeated preflight/generate requests are idempotent.
            List<ExamRegistration> existingMembers = registrationRepository
                    .findBySessionIdOrderByStudentPublicIdAsc(session.getId());
            Map<UUID, ExamRegistration> existingByStudent = existingMembers.stream()
                    .collect(Collectors.toMap(ExamRegistration::getStudentPublicId, member -> member,
                            (first, ignored) -> first, LinkedHashMap::new));
            List<ExamRegistration> persistedMembers = members.stream()
                    .map(computed -> mergeAudienceMember(existingByStudent.get(computed.getStudentPublicId()), computed))
                    .toList();
            existingMembers.stream()
                    .filter(existing -> !provenance.containsKey(existing.getStudentPublicId()))
                    .forEach(registrationRepository::delete);
            registrationRepository.saveAll(persistedMembers);
            members = persistedMembers;
        }
        int eligibleCount = (int) members.stream().filter(member -> member.getStatus() == AudienceMemberStatus.ELIGIBLE).count();
        int excludedCount = (int) members.stream().filter(member -> member.getStatus() == AudienceMemberStatus.EXCLUDED).count();
        int blockedCount = (int) members.stream().filter(member -> member.getReason() == AudienceDecisionReason.SCHEDULE_OVERLAP).count();
        List<AudienceMemberResponse> responses = members.stream().map(this::toMemberResponse).toList();
        return new AudiencePreviewResponse(studentIds.size(), Math.max(0, totalResolved - studentIds.size()),
                eligibleCount, excludedCount, blockedCount, session.getCapacity(),
                blockedCount == 0 && capacityReady, responses);
    }

    private ExamRegistration mergeAudienceMember(ExamRegistration existing, ExamRegistration computed) {
        if (existing == null) return computed;
        existing.setTenantId(computed.getTenantId());
        existing.setLicenseKey(computed.getLicenseKey());
        existing.setStudentPublicId(computed.getStudentPublicId());
        existing.setStatus(computed.getStatus());
        existing.setReason(computed.getReason());
        existing.setSourceSummary(computed.getSourceSummary());
        existing.setPriorSessionPublicId(computed.getPriorSessionPublicId());
        existing.setPriorSessionName(computed.getPriorSessionName());
        existing.setPriorStatus(computed.getPriorStatus());
        existing.setPublishedAt(computed.getPublishedAt());
        return existing;
    }

    List<UUID> resolveSource(ExamSessionAudienceSource source, UUID tenantId) {
        return switch (source.getSourceType()) {
            case CLASS -> enrollmentModuleService.findActiveStudentPublicIds(tenantId, source.getSourcePublicId());
            case PROGRAM -> enrollmentModuleService.findActiveStudentPublicIdsByProgram(tenantId, source.getSourcePublicId());
            case STUDENT -> activeStudentSource(source.getSourcePublicId(), tenantId);
        };
    }

    private void exclude(ExamRegistration member, AudienceDecisionReason reason, ExamRegistration conflict) {
        member.setStatus(AudienceMemberStatus.EXCLUDED);
        member.setReason(reason);
        if (conflict != null) {
            member.setPriorSessionPublicId(conflict.getSession().getPublicId());
            member.setPriorSessionName(conflict.getSession().getName());
            member.setPriorStatus(conflict.getSession().getStatus().name());
        }
    }

    private boolean overlaps(ExamSession left, ExamSession right) {
        return left.getOpensAt().isBefore(right.getClosesAt()) && right.getOpensAt().isBefore(left.getClosesAt())
                && right.getStatus() != SessionStatus.CANCELLED;
    }

    private AudienceMemberResponse toMemberResponse(ExamRegistration member) {
        return new AudienceMemberResponse(member.getStudentPublicId(), member.getStatus(), member.getReason(),
                member.getSourceSummary(), member.getPriorSessionPublicId(), member.getPriorSessionName(),
                member.getPriorStatus());
    }

    boolean changedAfterGeneration(ExamSession session, AudiencePreviewResponse current) {
        Set<UUID> currentEligible = current.members().stream()
                .filter(member -> member.status() == AudienceMemberStatus.ELIGIBLE)
                .map(AudienceMemberResponse::studentPublicId)
                .collect(Collectors.toSet());
        Set<UUID> generatedAudience = registrationRepository.findBySessionIdOrderByStudentPublicIdAsc(session.getId()).stream()
                .filter(member -> member.getStatus() != AudienceMemberStatus.EXCLUDED)
                .map(ExamRegistration::getStudentPublicId)
                .collect(Collectors.toSet());
        return !currentEligible.equals(generatedAudience);
    }

    List<UUID> activeStudentSource(UUID studentPublicId, UUID tenantId) {
        identityService.requireActiveStudentInTenant(studentPublicId, tenantId);
        return List.of(studentPublicId);
    }
}
