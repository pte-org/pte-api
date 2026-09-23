package com.pte.scoring.internal.service;

import com.pte.scoring.dto.request.CreateExaminerAssignmentPreviewRequest.AssignmentScopeRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Pure allocation rules: shuffle one deduplicated attempt pool and balance counts to within one. */
final class ExaminerAssignmentAllocator {

    private ExaminerAssignmentAllocator() {
    }

    static Map<UUID, UUID> random(Map<UUID, Integer> eligibleAttempts, List<UUID> examinerIds, UUID seed) {
        if (eligibleAttempts.isEmpty() || examinerIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> shuffled = new ArrayList<>(eligibleAttempts.keySet());
        long randomSeed = seed.getMostSignificantBits() ^ seed.getLeastSignificantBits();
        Collections.shuffle(shuffled, new Random(randomSeed));
        Map<UUID, UUID> assignments = new LinkedHashMap<>();
        for (int i = 0; i < shuffled.size(); i++) {
            assignments.put(shuffled.get(i), examinerIds.get(i % examinerIds.size()));
        }
        return assignments;
    }

    static Map<UUID, UUID> manual(Map<AssignmentScopeKey, Set<UUID>> candidatesByScope,
            List<AssignmentScopeRequest> scopes) {
        Map<UUID, Set<UUID>> examinerCandidates = new LinkedHashMap<>();
        for (AssignmentScopeRequest scope : scopes) {
            if (scope.examinerPublicId() == null) {
                continue;
            }
            Set<UUID> attempts = candidatesByScope.getOrDefault(
                    new AssignmentScopeKey(scope.type(), scope.scopePublicId()), Set.of());
            for (UUID attemptId : attempts) {
                examinerCandidates.computeIfAbsent(attemptId, ignored -> new java.util.LinkedHashSet<>())
                        .add(scope.examinerPublicId());
            }
        }
        Map<UUID, UUID> assignments = new LinkedHashMap<>();
        examinerCandidates.forEach((attemptId, examiners) -> {
            if (examiners.size() == 1) {
                assignments.put(attemptId, examiners.iterator().next());
            }
        });
        return assignments;
    }
}
