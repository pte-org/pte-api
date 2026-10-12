package com.pte.scoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptOwnerView;
import com.pte.identity.IdentityService;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.internal.dto.response.AttemptReviewItemResponse;
import com.pte.scoring.internal.repository.AttemptStatusCount;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The host's submissions list for one session: one row per attempt (student) instead of one per answer.
 * The per-answer detail stays in {@link ScoringReviewService}, narrowed by attempt.
 */
@Service
public class ScoringAttemptReviewService {

    private final ScoringAnswerRepository scoringAnswerRepository;
    private final AttemptService attemptService;
    private final IdentityService identityService;

    public ScoringAttemptReviewService(ScoringAnswerRepository scoringAnswerRepository,
                                       AttemptService attemptService, IdentityService identityService) {
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.attemptService = attemptService;
        this.identityService = identityService;
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
        Map<UUID, String> names = identityService.findFullNames(caller.tenantId(), owners.values().stream()
                .map(AttemptOwnerView::studentPublicId).distinct().toList());
        return countsByAttempt.entrySet().stream()
                .map(entry -> toItem(entry.getKey(), entry.getValue(), owners.get(entry.getKey()), names))
                .toList();
    }

    private AttemptReviewItemResponse toItem(UUID attemptPublicId, Map<ScoringAnswerStatus, Integer> counts,
                                             AttemptOwnerView owner, Map<UUID, String> names) {
        UUID studentPublicId = owner == null ? null : owner.studentPublicId();
        return new AttemptReviewItemResponse(attemptPublicId, studentPublicId, names.get(studentPublicId),
                owner == null ? null : owner.attemptNumber(),
                counts.values().stream().mapToInt(Integer::intValue).sum(),
                counts.getOrDefault(ScoringAnswerStatus.SCORED, 0),
                counts.getOrDefault(ScoringAnswerStatus.PENDING, 0),
                counts.getOrDefault(ScoringAnswerStatus.AI_SCORING, 0),
                counts.getOrDefault(ScoringAnswerStatus.SCORING_FAILED, 0));
    }
}
