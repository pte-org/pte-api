package com.pte.scoring.internal.service;

import com.pte.scoring.domain.enums.AssignmentScopeType;
import com.pte.scoring.dto.request.CreateExaminerAssignmentPreviewRequest.AssignmentScopeRequest;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ExaminerAssignmentAllocatorTest {

    @Test
    void randomPoolDistributesFortyAttemptsEvenlyAcrossTwoExaminers() {
        UUID examiner1 = UUID.randomUUID();
        UUID examiner2 = UUID.randomUUID();
        Map<UUID, Integer> candidates = candidates(40);

        Map<UUID, UUID> result = ExaminerAssignmentAllocator.random(candidates,
                List.of(examiner1, examiner2), UUID.fromString("00000000-0000-0000-0000-000000000001"));

        assertThat(result).hasSize(40);
        assertThat(result.values().stream().filter(examiner1::equals).count()).isEqualTo(20);
        assertThat(result.values().stream().filter(examiner2::equals).count()).isEqualTo(20);
    }

    @Test
    void randomAllocationIsDeterministicAndCountDifferenceNeverExceedsOne() {
        UUID seed = UUID.randomUUID();
        List<UUID> examiners = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        Map<UUID, Integer> candidates = candidates(41);

        Map<UUID, UUID> first = ExaminerAssignmentAllocator.random(candidates, examiners, seed);
        Map<UUID, UUID> retry = ExaminerAssignmentAllocator.random(candidates, examiners, seed);
        Map<UUID, Long> counts = first.values().stream().collect(Collectors.groupingBy(
                Function.identity(), Collectors.counting()));

        assertThat(retry).isEqualTo(first);
        assertThat(examiners.stream().map(id -> counts.getOrDefault(id, 0L)).max(Long::compareTo).orElseThrow()
                - examiners.stream().map(id -> counts.getOrDefault(id, 0L)).min(Long::compareTo).orElseThrow())
                .isLessThanOrEqualTo(1L);
    }

    @Test
    void manualAssignmentDeduplicatesOverlapWhenExaminerMatchesAndOmitsConflictingAttempt() {
        UUID classId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        UUID examiner1 = UUID.randomUUID();
        UUID examiner2 = UUID.randomUUID();
        UUID onlyClass = UUID.randomUUID();
        UUID overlap = UUID.randomUUID();
        UUID onlyProgram = UUID.randomUUID();
        Map<AssignmentScopeKey, Set<UUID>> candidates = new LinkedHashMap<>();
        candidates.put(new AssignmentScopeKey(AssignmentScopeType.CLASS, classId), Set.of(onlyClass, overlap));
        candidates.put(new AssignmentScopeKey(AssignmentScopeType.PROGRAM, programId), Set.of(overlap, onlyProgram));

        Map<UUID, UUID> matching = ExaminerAssignmentAllocator.manual(candidates, List.of(
                new AssignmentScopeRequest(AssignmentScopeType.CLASS, classId, examiner1),
                new AssignmentScopeRequest(AssignmentScopeType.PROGRAM, programId, examiner1)));
        Map<UUID, UUID> conflicting = ExaminerAssignmentAllocator.manual(candidates, List.of(
                new AssignmentScopeRequest(AssignmentScopeType.CLASS, classId, examiner1),
                new AssignmentScopeRequest(AssignmentScopeType.PROGRAM, programId, examiner2)));

        assertThat(matching).containsEntry(onlyClass, examiner1).containsEntry(overlap, examiner1)
                .containsEntry(onlyProgram, examiner1).hasSize(3);
        assertThat(conflicting).containsEntry(onlyClass, examiner1).containsEntry(onlyProgram, examiner2)
                .doesNotContainKey(overlap).hasSize(2);
    }

    private Map<UUID, Integer> candidates(int count) {
        return IntStream.range(0, count).mapToObj(ignored -> UUID.randomUUID())
                .collect(Collectors.toMap(Function.identity(), ignored -> 1, (left, right) -> left,
                        LinkedHashMap::new));
    }
}
