package com.pte.examstaff.internal;

import com.pte.scoring.dto.response.ExaminerProgressView;
import java.util.LinkedHashMap;
import java.util.Map;

final class ExaminerOverviewAccumulator {
    final Map<String, Long> buckets = new LinkedHashMap<>();
    long total;
    long assigned;
    long batches;
    long eligible;
    long submitted;
    long pending;
    long inProgress;
    long completed;
    long unavailable;

    ExaminerOverviewAccumulator() {
        for (String status : java.util.List.of("PENDING", "IN_PROGRESS", "COMPLETED", "UNAVAILABLE")) {
            buckets.put(status, 0L);
        }
    }

    void accept(ExaminerProgressView progress) {
        total++;
        buckets.merge(progress.workStatus(), 1L, Long::sum);
        assigned += progress.assignedAttemptCount();
        batches += progress.batchCount();
        pending += progress.pendingAttemptCount();
        inProgress += progress.inProgressAttemptCount();
        completed += progress.completedAttemptCount();
        unavailable += progress.unavailableAttemptCount();
        if (progress.eligibleAnswerCount() != null && progress.submittedAnswerCount() != null) {
            eligible += progress.eligibleAnswerCount();
            submitted += progress.submittedAnswerCount();
        }
    }

    ExaminerOverviewView view() {
        boolean completeCoverage = buckets.get("UNAVAILABLE") == 0;
        Integer percent = !completeCoverage || assigned == 0 ? null : eligible == 0 ? 100
                : (int) (100L * submitted / eligible);
        return new ExaminerOverviewView(total, Map.copyOf(buckets), assigned, batches,
                completeCoverage ? eligible : null, completeCoverage ? submitted : null, percent,
                pending, inProgress, completed, unavailable, eligible, submitted, completeCoverage);
    }
}
