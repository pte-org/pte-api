package com.pte.scoring.internal.service;

import com.pte.scoring.dto.response.ExaminerProgressView;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

final class StaffSessionProgress {
    final UUID sessionId;
    long assigned;
    long frozen;
    long eligible;
    long submitted;
    long pending;
    long inProgress;
    long completed;
    long unavailable;
    long batches;
    UUID lastBatchId;
    boolean invalidFrozen;
    final Set<String> diagnostics = new LinkedHashSet<>();

    StaffSessionProgress(UUID sessionId) { this.sessionId = sessionId; }

    void accept(StaffAttemptProgress attempt, long scoreRows) {
        assigned++;
        // Assignments are scanned in batch-id order; one last key per session bounds memory.
        UUID batchId = attempt.assignment.getBatchPublicId();
        if (batchId != null && !batchId.equals(lastBatchId)) { batches++; lastBatchId = batchId; }
        int denominator = attempt.assignment.getEligibleAnswerCount();
        invalidFrozen |= denominator < 0;
        frozen += Math.max(0, denominator);
        switch (attempt.finish(scoreRows)) {
            case "PENDING" -> pending++;
            case "IN_PROGRESS" -> inProgress++;
            case "COMPLETED" -> completed++;
            default -> unavailable++;
        }
        eligible += attempt.eligible;
        submitted += attempt.submitted;
        diagnostics.addAll(attempt.diagnostics);
    }

    ExaminerProgressView view(boolean published) {
        String status = unavailable > 0 ? "UNAVAILABLE" : completed == assigned ? "COMPLETED"
                : pending == assigned ? "PENDING" : "IN_PROGRESS";
        return new ExaminerProgressView(sessionId, assigned, batches, invalidFrozen ? null : frozen,
                unavailable > 0 ? null : eligible, unavailable > 0 ? null : submitted, pending,
                inProgress, completed, unavailable, status, published ? "PUBLISHED" : "UNPUBLISHED",
                unavailable > 0 ? null : eligible == 0 ? 100 : (int) (100L * submitted / eligible),
                java.util.List.copyOf(diagnostics));
    }
}
