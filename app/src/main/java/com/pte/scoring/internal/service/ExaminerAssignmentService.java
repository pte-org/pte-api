package com.pte.scoring.internal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptSummaryView;
import com.pte.enrollment.EnrollmentModuleService;
import com.pte.identity.IdentityService;
import com.pte.identity.dto.response.ExaminerIdentityView;
import com.pte.scoring.domain.ExaminerAssignmentBatch;
import com.pte.scoring.domain.ExaminerAttemptAssignment;
import com.pte.scoring.domain.enums.AssignmentBatchMode;
import com.pte.scoring.domain.enums.AssignmentBatchStatus;
import com.pte.scoring.domain.enums.AssignmentScopeType;
import com.pte.scoring.dto.request.CreateExaminerAssignmentPreviewRequest;
import com.pte.scoring.dto.request.CreateExaminerAssignmentPreviewRequest.AssignmentScopeRequest;
import com.pte.scoring.dto.response.ExaminerAssignmentBatchSummaryResponse;
import com.pte.scoring.dto.response.ExaminerAssignmentConflictResponse;
import com.pte.scoring.dto.response.ExaminerAssignmentLoadResponse;
import com.pte.scoring.dto.response.ExaminerAssignmentOverviewResponse;
import com.pte.scoring.dto.response.ExaminerAssignmentPreviewResponse;
import com.pte.scoring.dto.response.ExaminerAssignmentScopeReferenceResponse;
import com.pte.scoring.internal.exception.InvalidExaminerAssignmentException;
import com.pte.scoring.internal.exception.StaleExaminerAssignmentPreviewException;
import com.pte.scoring.internal.repository.ExaminerAssignmentBatchRepository;
import com.pte.scoring.internal.repository.ExaminerAttemptAssignmentRepository;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Owns the durable preview and immutable commit of per-session Examiner assignments. */
@Service
public class ExaminerAssignmentService {

    private static final Duration PREVIEW_TTL = Duration.ofMinutes(30);
    private static final int MAX_BATCH_PAGE_SIZE = 100;
    private static final TypeReference<List<AssignmentEntry>> ASSIGNMENT_LIST = new TypeReference<>() { };

    private final SessionService sessionService;
    private final EnrollmentModuleService enrollmentService;
    private final AttemptService attemptService;
    private final ScoringEligibilityQueryService eligibilityQueryService;
    private final IdentityService identityService;
    private final ExaminerAssignmentBatchRepository batchRepository;
    private final ExaminerAttemptAssignmentRepository assignmentRepository;
    private final ObjectMapper objectMapper;

    public ExaminerAssignmentService(SessionService sessionService, EnrollmentModuleService enrollmentService,
            AttemptService attemptService, ScoringEligibilityQueryService eligibilityQueryService,
            IdentityService identityService, ExaminerAssignmentBatchRepository batchRepository,
            ExaminerAttemptAssignmentRepository assignmentRepository, ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.enrollmentService = enrollmentService;
        this.attemptService = attemptService;
        this.eligibilityQueryService = eligibilityQueryService;
        this.identityService = identityService;
        this.batchRepository = batchRepository;
        this.assignmentRepository = assignmentRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ExaminerAssignmentPreviewResponse preview(UUID sessionPublicId,
            CreateExaminerAssignmentPreviewRequest request, CurrentUser caller) {
        UUID tenantId = requireHost(caller);
        sessionService.lockForExaminerAssignment(sessionPublicId, tenantId);
        batchRepository.expireOverduePreviews(tenantId, sessionPublicId,
                AssignmentBatchStatus.PREVIEWED, AssignmentBatchStatus.EXPIRED, Instant.now());
        NormalizedRequest normalized = normalize(request);
        validateExaminers(tenantId, normalized.examinerPublicIds());

        List<ExaminerAttemptAssignment> priorAssignments =
                assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionPublicId);
        Set<UUID> alreadyAssigned = priorAssignments.stream()
                .map(ExaminerAttemptAssignment::getAttemptPublicId).collect(Collectors.toSet());
        ResolvedPool pool = resolvePool(sessionPublicId, tenantId, normalized.scopes(), alreadyAssigned);
        UUID seed = normalized.mode() == AssignmentBatchMode.RANDOM ? UUID.randomUUID() : null;
        Map<UUID, UUID> allocation = normalized.mode() == AssignmentBatchMode.RANDOM
                ? ExaminerAssignmentAllocator.random(pool.eligibleAnswerCounts(), normalized.examinerPublicIds(), seed)
                : ExaminerAssignmentAllocator.manual(pool.attemptsByScope(), normalized.scopes());
        List<ExaminerAssignmentConflictResponse> conflicts = findConflicts(
                pool.attemptsByScope(), normalized.scopes());

        if (!conflicts.isEmpty() || allocation.isEmpty()) {
            return response(null, normalized.mode(), "INVALID", false, !alreadyAssigned.isEmpty(),
                    pool.eligibleAnswerCounts().size(),
                    countAnswers(pool.eligibleAnswerCounts().keySet(), pool.eligibleAnswerCounts()),
                    normalized.examinerPublicIds(), allocationEntries(allocation, pool.eligibleAnswerCounts()),
                    conflicts, null, null);
        }

        Instant now = Instant.now();
        List<AssignmentEntry> entries = allocationEntries(allocation, pool.eligibleAnswerCounts());
        ScopeSnapshotEnvelope scopeSnapshot = new ScopeSnapshotEnvelope(
                pool.scopeSnapshots(), normalized.examinerPublicIds(), !alreadyAssigned.isEmpty(),
                pool.eligibleAnswerCounts().values().stream().mapToInt(Integer::intValue).sum());
        ExaminerAssignmentBatch batch = new ExaminerAssignmentBatch(tenantId, sessionPublicId,
                caller.userId(), normalized.mode(), writeJson(scopeSnapshot), writeJson(entries), seed,
                now.plus(PREVIEW_TTL));
        batch = batchRepository.saveAndFlush(batch);
        return response(batch.getPublicId(), normalized.mode(), batch.getStatus().name(), true,
                scopeSnapshot.supplemental(), entries.size(), scopeSnapshot.eligibleAnswerCount(),
                normalized.examinerPublicIds(), entries, List.of(), batch.getPreviewExpiresAt(), null);
    }

    @Transactional
    public ExaminerAssignmentPreviewResponse confirm(UUID sessionPublicId, UUID batchPublicId, CurrentUser caller) {
        UUID tenantId = requireHost(caller);
        sessionService.lockForExaminerAssignment(sessionPublicId, tenantId);
        ExaminerAssignmentBatch batch = batchRepository.findForUpdate(batchPublicId, tenantId, sessionPublicId)
                .orElseThrow(StaleExaminerAssignmentPreviewException::new);
        ScopeSnapshotEnvelope expectedScopes = readJson(batch.getScopeSnapshotJson(), ScopeSnapshotEnvelope.class);
        List<AssignmentEntry> entries = readJson(batch.getAssignmentSnapshotJson(), ASSIGNMENT_LIST);

        if (batch.getStatus() == AssignmentBatchStatus.COMMITTED) {
            return response(batch.getPublicId(), batch.getMode(), batch.getStatus().name(), true,
                    expectedScopes.supplemental(), entries.size(), expectedScopes.eligibleAnswerCount(),
                    expectedScopes.examinerPublicIds(), entries, List.of(), batch.getPreviewExpiresAt(),
                    batch.getCommittedAt());
        }
        if (batch.getStatus() == AssignmentBatchStatus.STALE || batch.getStatus() == AssignmentBatchStatus.EXPIRED) {
            return response(batch.getPublicId(), batch.getMode(), batch.getStatus().name(), false,
                    expectedScopes.supplemental(), entries.size(), expectedScopes.eligibleAnswerCount(),
                    expectedScopes.examinerPublicIds(), entries, List.of(), batch.getPreviewExpiresAt(), null);
        }
        if (!Instant.now().isBefore(batch.getPreviewExpiresAt())) {
            batch.expire();
            batchRepository.save(batch);
            return response(batch.getPublicId(), batch.getMode(), batch.getStatus().name(), false,
                    expectedScopes.supplemental(), entries.size(), expectedScopes.eligibleAnswerCount(),
                    expectedScopes.examinerPublicIds(), entries, List.of(), batch.getPreviewExpiresAt(), null);
        }

        List<ExaminerAttemptAssignment> priorAssignments =
                assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionPublicId);
        Set<UUID> alreadyAssigned = priorAssignments.stream()
                .map(ExaminerAttemptAssignment::getAttemptPublicId).collect(Collectors.toSet());
        List<AssignmentScopeRequest> scopes = expectedScopes.scopes().stream()
                .map(scope -> new AssignmentScopeRequest(scope.type(), scope.scopePublicId(), scope.examinerPublicId()))
                .toList();
        ResolvedPool currentPool = resolvePool(sessionPublicId, tenantId, scopes, alreadyAssigned);
        if (!sameScopeSnapshot(expectedScopes.scopes(), currentPool.scopeSnapshots())) {
            return markPreviewStale(batch, expectedScopes, entries);
        }
        if (entries.stream().anyMatch(entry -> !java.util.Objects.equals(
                currentPool.eligibleAnswerCounts().get(entry.attemptPublicId()), entry.eligibleAnswerCount()))) {
            return markPreviewStale(batch, expectedScopes, entries);
        }
        if (batch.getMode() == AssignmentBatchMode.MANUAL) {
            Map<UUID, UUID> recalculated = ExaminerAssignmentAllocator.manual(currentPool.attemptsByScope(), scopes);
            if (!allocationEntries(recalculated, currentPool.eligibleAnswerCounts()).equals(entries)) {
                return markPreviewStale(batch, expectedScopes, entries);
            }
        }
        if (entries.stream().anyMatch(entry -> alreadyAssigned.contains(entry.attemptPublicId()))) {
            return markPreviewStale(batch, expectedScopes, entries);
        }

        if (!validateExaminerLocks(tenantId, expectedScopes.examinerPublicIds())) {
            return markPreviewStale(batch, expectedScopes, entries);
        }

        Instant committedAt = Instant.now();
        if (!batch.commit(committedAt)) {
            batchRepository.save(batch);
            return response(batch.getPublicId(), batch.getMode(), batch.getStatus().name(), false,
                    expectedScopes.supplemental(), entries.size(), expectedScopes.eligibleAnswerCount(),
                    expectedScopes.examinerPublicIds(), entries, List.of(), batch.getPreviewExpiresAt(), null);
        }
        assignmentRepository.saveAll(entries.stream().map(entry -> new ExaminerAttemptAssignment(
                batch.getPublicId(), tenantId, sessionPublicId, entry.attemptPublicId(), entry.examinerPublicId(),
                entry.eligibleAnswerCount(), caller.userId(), committedAt)).toList());
        assignmentRepository.flush();
        return response(batch.getPublicId(), batch.getMode(), batch.getStatus().name(), true,
                expectedScopes.supplemental(), entries.size(), expectedScopes.eligibleAnswerCount(),
                expectedScopes.examinerPublicIds(), entries, List.of(), batch.getPreviewExpiresAt(), committedAt);
    }

    @Transactional
    public ExaminerAssignmentOverviewResponse overview(UUID sessionPublicId, CurrentUser caller, int page, int size) {
        UUID tenantId = requireHost(caller);
        if (page < 0 || size < 1 || size > MAX_BATCH_PAGE_SIZE) {
            throw new InvalidExaminerAssignmentException("Batch history page/size is outside the allowed range.");
        }
        sessionService.verifyHostAccess(sessionPublicId, tenantId);
        batchRepository.expireOverduePreviews(tenantId, sessionPublicId,
                AssignmentBatchStatus.PREVIEWED, AssignmentBatchStatus.EXPIRED, Instant.now());
        Page<ExaminerAssignmentBatch> batchPage = batchRepository
                .findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionPublicId,
                        PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        List<ExaminerAssignmentBatch> batches = batchPage.getContent();
        Map<UUID, MutableLoad> loads = new LinkedHashMap<>();
        assignmentRepository.summarizeLoads(tenantId, sessionPublicId).forEach(load ->
                loads.put(load.getExaminerPublicId(), new MutableLoad(load.getAttemptCount(),
                        load.getEligibleAnswerCount())));
        List<ExaminerAssignmentBatchSummaryResponse> summaries = batches.stream().map(batch -> {
            ScopeSnapshotEnvelope scopeSnapshot = readJson(batch.getScopeSnapshotJson(), ScopeSnapshotEnvelope.class);
            int count = readJson(batch.getAssignmentSnapshotJson(), ASSIGNMENT_LIST).size();
            return new ExaminerAssignmentBatchSummaryResponse(batch.getPublicId(), batch.getMode().name(),
                    batch.getStatus().name(), count, scopeSnapshot.eligibleAnswerCount(), batch.getCreatedAt(),
                    batch.getPreviewExpiresAt(), batch.getCommittedAt());
        }).toList();
        long assignedAttemptCount = assignmentRepository
                .countByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionPublicId);
        return new ExaminerAssignmentOverviewResponse(summaries, loadResponses(loads), assignedAttemptCount,
                batchPage.getNumber(), batchPage.getSize(), batchPage.getTotalElements(), batchPage.getTotalPages());
    }

    private ResolvedPool resolvePool(UUID sessionPublicId, UUID tenantId, List<AssignmentScopeRequest> scopes,
            Set<UUID> alreadyAssigned) {
        List<AttemptSummaryView> submitted = attemptService.getSubmittedAttemptsForSession(sessionPublicId, tenantId);
        Map<UUID, AttemptSummaryView> attemptById = submitted.stream().collect(Collectors.toMap(
                AttemptSummaryView::attemptPublicId, attempt -> attempt, (left, right) -> left));
        Map<AssignmentScopeKey, Set<UUID>> rawAttemptsByScope = new LinkedHashMap<>();
        Set<UUID> allRawAttemptIds = new LinkedHashSet<>();

        for (AssignmentScopeRequest scope : scopes) {
            List<UUID> studentIds = switch (scope.type()) {
                case CLASS -> enrollmentService.findActiveStudentPublicIds(tenantId, scope.scopePublicId());
                case PROGRAM -> enrollmentService.findActiveStudentPublicIdsByProgram(tenantId, scope.scopePublicId());
            };
            Set<UUID> students = new HashSet<>(studentIds);
            Set<UUID> matchingAttemptIds = submitted.stream()
                    .filter(attempt -> students.contains(attempt.studentPublicId()))
                    .map(AttemptSummaryView::attemptPublicId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            AssignmentScopeKey key = new AssignmentScopeKey(scope.type(), scope.scopePublicId());
            rawAttemptsByScope.put(key, matchingAttemptIds);
            allRawAttemptIds.addAll(matchingAttemptIds);
        }

        List<UUID> candidateIds = allRawAttemptIds.stream().filter(attemptById::containsKey).toList();
        Map<UUID, Integer> allEligibility = eligibilityQueryService
                .findEligibleAttempts(sessionPublicId, tenantId, candidateIds).stream()
                .collect(Collectors.toMap(view -> view.attemptPublicId(), view -> view.eligibleAnswerCount()));
        Map<UUID, Integer> eligibleUnassigned = allEligibility.entrySet().stream()
                .filter(entry -> !alreadyAssigned.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left,
                        LinkedHashMap::new));

        Map<AssignmentScopeKey, Set<UUID>> eligibleAttemptsByScope = new LinkedHashMap<>();
        List<ScopeSnapshot> scopeSnapshots = new ArrayList<>();
        for (AssignmentScopeRequest scope : scopes) {
            AssignmentScopeKey key = new AssignmentScopeKey(scope.type(), scope.scopePublicId());
            Set<UUID> eligibleInScope = rawAttemptsByScope.getOrDefault(key, Set.of()).stream()
                    .filter(eligibleUnassigned::containsKey)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            eligibleAttemptsByScope.put(key, eligibleInScope);
            scopeSnapshots.add(new ScopeSnapshot(scope.type(), scope.scopePublicId(), scope.examinerPublicId(),
                    eligibleInScope.stream().sorted().toList()));
        }
        return new ResolvedPool(eligibleAttemptsByScope, eligibleUnassigned, scopeSnapshots);
    }

    private NormalizedRequest normalize(CreateExaminerAssignmentPreviewRequest request) {
        if (request == null || request.mode() == null || request.scopes() == null || request.scopes().isEmpty()) {
            throw new InvalidExaminerAssignmentException("At least one scope and an assignment mode are required.");
        }
        Map<AssignmentScopeKey, AssignmentScopeRequest> uniqueScopes = new LinkedHashMap<>();
        for (AssignmentScopeRequest scope : request.scopes()) {
            if (scope == null || scope.type() == null || scope.scopePublicId() == null) {
                throw new InvalidExaminerAssignmentException("Each scope must specify its type and public ID.");
            }
            if (request.mode() == AssignmentBatchMode.MANUAL && scope.examinerPublicId() == null) {
                throw new InvalidExaminerAssignmentException("Manual mode requires an Examiner for every scope.");
            }
            if (request.mode() == AssignmentBatchMode.RANDOM && scope.examinerPublicId() != null) {
                throw new InvalidExaminerAssignmentException("Random mode uses one pooled Examiner list, not per-scope mappings.");
            }
            AssignmentScopeKey key = new AssignmentScopeKey(scope.type(), scope.scopePublicId());
            if (uniqueScopes.putIfAbsent(key, scope) != null) {
                throw new InvalidExaminerAssignmentException("Duplicate scope in assignment request.");
            }
        }
        List<UUID> examinerIds;
        if (request.mode() == AssignmentBatchMode.RANDOM) {
            examinerIds = distinctIds(request.examinerPublicIds());
            int requestedCount = request.examinerPublicIds() == null ? 0 : request.examinerPublicIds().size();
            if (examinerIds.isEmpty() || examinerIds.size() != requestedCount) {
                throw new InvalidExaminerAssignmentException("Random mode requires unique active Examiner IDs.");
            }
        } else {
            if (request.examinerPublicIds() != null && !request.examinerPublicIds().isEmpty()) {
                throw new InvalidExaminerAssignmentException("Manual mode takes its Examiner from each scope mapping.");
            }
            examinerIds = uniqueScopes.values().stream().map(AssignmentScopeRequest::examinerPublicId)
                    .distinct().toList();
        }
        return new NormalizedRequest(request.mode(), List.copyOf(uniqueScopes.values()), examinerIds);
    }

    private List<UUID> distinctIds(List<UUID> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }

    private void validateExaminers(UUID tenantId, List<UUID> requestedIds) {
        List<ExaminerIdentityView> active = identityService.findActiveExaminers(tenantId, requestedIds);
        Set<UUID> activeIds = active.stream().map(ExaminerIdentityView::publicId).collect(Collectors.toSet());
        if (activeIds.size() != requestedIds.size() || !activeIds.containsAll(requestedIds)) {
            throw new InvalidExaminerAssignmentException("Every selected Examiner must be active and belong to this tenant.");
        }
    }

    private boolean validateExaminerLocks(UUID tenantId, List<UUID> requestedIds) {
        List<ExaminerIdentityView> active = identityService.lockActiveExaminers(tenantId, requestedIds);
        Set<UUID> activeIds = active.stream().map(ExaminerIdentityView::publicId).collect(Collectors.toSet());
        return activeIds.size() == requestedIds.size() && activeIds.containsAll(requestedIds);
    }

    private ExaminerAssignmentPreviewResponse markPreviewStale(ExaminerAssignmentBatch batch,
            ScopeSnapshotEnvelope expectedScopes, List<AssignmentEntry> entries) {
        batch.markStale();
        batchRepository.saveAndFlush(batch);
        return response(batch.getPublicId(), batch.getMode(), batch.getStatus().name(), false,
                expectedScopes.supplemental(), entries.size(), expectedScopes.eligibleAnswerCount(),
                expectedScopes.examinerPublicIds(), entries, List.of(), batch.getPreviewExpiresAt(), null);
    }

    private List<ExaminerAssignmentConflictResponse> findConflicts(
            Map<AssignmentScopeKey, Set<UUID>> candidatesByScope, List<AssignmentScopeRequest> scopes) {
        Map<UUID, Map<AssignmentScopeKey, UUID>> examinerByAttemptScope = new LinkedHashMap<>();
        for (AssignmentScopeRequest scope : scopes) {
            if (scope.examinerPublicId() == null) {
                continue;
            }
            AssignmentScopeKey key = new AssignmentScopeKey(scope.type(), scope.scopePublicId());
            for (UUID attemptId : candidatesByScope.getOrDefault(key, Set.of())) {
                examinerByAttemptScope.computeIfAbsent(attemptId, ignored -> new LinkedHashMap<>())
                        .put(key, scope.examinerPublicId());
            }
        }
        return examinerByAttemptScope.entrySet().stream()
                .filter(entry -> entry.getValue().values().stream().distinct().count() > 1)
                .map(entry -> new ExaminerAssignmentConflictResponse(entry.getKey(),
                        entry.getValue().keySet().stream()
                                .map(key -> new ExaminerAssignmentScopeReferenceResponse(key.type(), key.scopePublicId()))
                                .toList()))
                .toList();
    }

    private boolean sameScopeSnapshot(List<ScopeSnapshot> expected, List<ScopeSnapshot> actual) {
        Comparator<ScopeSnapshot> order = Comparator.comparing((ScopeSnapshot scope) -> scope.type().name())
                .thenComparing(scope -> scope.scopePublicId().toString());
        return expected.stream().sorted(order).toList().equals(actual.stream().sorted(order).toList());
    }

    private List<AssignmentEntry> allocationEntries(Map<UUID, UUID> allocation, Map<UUID, Integer> answerCounts) {
        return allocation.entrySet().stream()
                .map(entry -> new AssignmentEntry(entry.getKey(), entry.getValue(), answerCounts.get(entry.getKey())))
                .sorted(Comparator.comparing(entry -> entry.attemptPublicId().toString()))
                .toList();
    }

    private int countAnswers(Collection<UUID> attemptIds, Map<UUID, Integer> answerCounts) {
        return attemptIds.stream().mapToInt(attemptId -> answerCounts.getOrDefault(attemptId, 0)).sum();
    }

    private ExaminerAssignmentPreviewResponse response(UUID batchId, AssignmentBatchMode mode, String status,
            boolean valid, boolean supplemental, int attemptCount, int eligibleAnswers, List<UUID> examinerIds,
            List<AssignmentEntry> entries, List<ExaminerAssignmentConflictResponse> conflicts,
            Instant expiresAt, Instant committedAt) {
        Map<UUID, MutableLoad> loads = new LinkedHashMap<>();
        examinerIds.forEach(id -> loads.put(id, new MutableLoad()));
        entries.forEach(entry -> loads.computeIfAbsent(entry.examinerPublicId(), ignored -> new MutableLoad())
                .add(entry.eligibleAnswerCount()));
        return new ExaminerAssignmentPreviewResponse(batchId, mode.name(), status, valid, supplemental,
                attemptCount, eligibleAnswers, loadResponses(loads), conflicts, expiresAt, committedAt);
    }

    private List<ExaminerAssignmentLoadResponse> loadResponses(Map<UUID, MutableLoad> loads) {
        return loads.entrySet().stream().map(entry -> new ExaminerAssignmentLoadResponse(
                entry.getKey(), entry.getValue().attempts, entry.getValue().eligibleAnswers)).toList();
    }

    private UUID requireHost(CurrentUser caller) {
        if (caller == null || caller.tenantId() == null || !caller.hasRole("HOST_ADMIN")) {
            throw new InvalidExaminerAssignmentException("A tenant HOST_ADMIN is required.");
        }
        return caller.tenantId();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not persist the assignment snapshot", exception);
        }
    }

    private <T> T readJson(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read the assignment scope snapshot", exception);
        }
    }

    private <T> T readJson(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read the assignment allocation snapshot", exception);
        }
    }

    public record ScopeSnapshotEnvelope(List<ScopeSnapshot> scopes, List<UUID> examinerPublicIds,
            boolean supplemental, int eligibleAnswerCount) {
    }

    public record ScopeSnapshot(AssignmentScopeType type, UUID scopePublicId, UUID examinerPublicId,
            List<UUID> eligibleAttemptPublicIds) {
    }

    public record AssignmentEntry(UUID attemptPublicId, UUID examinerPublicId, int eligibleAnswerCount) {
    }

    private record NormalizedRequest(AssignmentBatchMode mode, List<AssignmentScopeRequest> scopes,
            List<UUID> examinerPublicIds) {
    }

    private record ResolvedPool(Map<AssignmentScopeKey, Set<UUID>> attemptsByScope,
            Map<UUID, Integer> eligibleAnswerCounts, List<ScopeSnapshot> scopeSnapshots) {
    }

    private static final class MutableLoad {
        private long attempts;
        private long eligibleAnswers;

        private MutableLoad() {
        }

        private MutableLoad(long attempts, long eligibleAnswers) {
            this.attempts = attempts;
            this.eligibleAnswers = eligibleAnswers;
        }

        void add(int answerCount) {
            attempts++;
            eligibleAnswers += answerCount;
        }
    }
}
