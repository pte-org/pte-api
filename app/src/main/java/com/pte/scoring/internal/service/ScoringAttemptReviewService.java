package com.pte.scoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptOwnerView;
import com.pte.identity.IdentityService;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.internal.dto.response.AttemptExaminerProgress;
import com.pte.scoring.internal.dto.response.AttemptReviewItemResponse;
import com.pte.scoring.internal.repository.AttemptStatusCount;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The host's submissions list for one session: one row per attempt (student) instead of one per answer.
 * The per-answer detail stays in {@link ScoringReviewService}, narrowed by attempt.
 */
@Service
public class ScoringAttemptReviewService {

    private final AiEvaluationRepository scoringAnswerRepository;
    private final AttemptService attemptService;
    private final IdentityService identityService;
    private final AttemptExaminerProgressReader examinerProgressReader;

    public ScoringAttemptReviewService(AiEvaluationRepository scoringAnswerRepository,
                                       AttemptService attemptService, IdentityService identityService,
                                       AttemptExaminerProgressReader examinerProgressReader) {
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.attemptService = attemptService;
        this.identityService = identityService;
        this.examinerProgressReader = examinerProgressReader;
    }

    @Transactional(readOnly = true)
    public List<AttemptReviewItemResponse> listAttempts(UUID sessionPublicId, CurrentUser caller) {
        Map<UUID, Map<ScoringAnswerStatus, Integer>> countsByAttempt = new LinkedHashMap<>();
        for (AttemptStatusCount row : scoringAnswerRepository.countByAttemptAndStatus(caller.tenantId(),
                sessionPublicId)) {
            countsByAttempt.computeIfAbsent(row.attemptPublicId(), id -> new EnumMap<>(ScoringAnswerStatus.class))
                    .put(row.status(), (int) row.count());
        }
        if (countsByAttempt.isEmpty()) {
            return List.of();
        }
        Map<UUID, AttemptOwnerView> owners = attemptService.getAttemptOwners(sessionPublicId, caller.tenantId(),
                List.copyOf(countsByAttempt.keySet()));
        Map<UUID, AttemptExaminerProgress> progressByAttempt = examinerProgressReader.read(caller.tenantId(),
                sessionPublicId);
        Map<UUID, String> names = identityService.findFullNames(caller.tenantId(), Stream.concat(
                        owners.values().stream().map(AttemptOwnerView::studentPublicId),
                        progressByAttempt.values().stream().map(AttemptExaminerProgress::examinerPublicId))
                .distinct().toList());
        return countsByAttempt.entrySet().stream()
                .map(entry -> toItem(entry.getKey(), entry.getValue(), owners.get(entry.getKey()),
                        progressByAttempt.get(entry.getKey()), names))
                .toList();
    }

    private AttemptReviewItemResponse toItem(UUID attemptPublicId, Map<ScoringAnswerStatus, Integer> counts,
                                             AttemptOwnerView owner, AttemptExaminerProgress examiner,
                                             Map<UUID, String> names) {
        UUID studentPublicId = owner == null ? null : owner.studentPublicId();
        return new AttemptReviewItemResponse(attemptPublicId, studentPublicId, names.get(studentPublicId),
                owner == null ? null : owner.attemptNumber(),
                owner == null ? null : owner.submittedAt(),
                counts.values().stream().mapToInt(Integer::intValue).sum(),
                counts.getOrDefault(ScoringAnswerStatus.SCORED, 0),
                counts.getOrDefault(ScoringAnswerStatus.PENDING, 0),
                counts.getOrDefault(ScoringAnswerStatus.AI_SCORING, 0),
                counts.getOrDefault(ScoringAnswerStatus.SCORING_FAILED, 0),
                examiner == null ? null : examiner.examinerPublicId(),
                examiner == null ? null : names.get(examiner.examinerPublicId()),
                examiner == null ? null : examiner.status(),
                examiner == null ? 0 : examiner.eligibleCount(),
                examiner == null ? 0 : examiner.savedCount());
    }
}
