package com.pte.scoring.internal.repository.workspace;

import com.pte.scoring.dto.response.ExaminerReportsResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.pte.scoring.internal.repository.workspace.ExaminerAssignmentScoresCte.CTE_SQL;
import static com.pte.scoring.internal.repository.workspace.NativeColumnValues.toDouble;
import static com.pte.scoring.internal.repository.workspace.NativeColumnValues.toInstant;
import static com.pte.scoring.internal.repository.workspace.NativeColumnValues.toInt;
import static com.pte.scoring.internal.repository.workspace.NativeColumnValues.toLong;
import static com.pte.scoring.internal.repository.workspace.NativeColumnValues.toUUID;

/** Examiner report queries (summary, task-type breakdown, recent finalized items) over the shared CTE. */
public class ExaminerReportsReadQuery {

    private final EntityManager entityManager;

    public ExaminerReportsReadQuery(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public ExaminerReportsResponse getReports(
            UUID tenantId, UUID examinerPublicId, Instant fromDate, Instant toDate,
            String taskType, String status, UUID sessionPublicId) {
        StringBuilder whereClause = new StringBuilder(" WHERE 1=1");
        if (sessionPublicId != null) {
            whereClause.append(" AND session_public_id = :sessionPublicId");
        }
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            whereClause.append(" AND status = :status");
        }
        if (fromDate != null) {
            whereClause.append(" AND (finalized_at >= :fromDate OR (finalized_at IS NULL AND assigned_at >= :fromDate))");
        }
        if (toDate != null) {
            whereClause.append(" AND (finalized_at <= :toDate OR (finalized_at IS NULL AND assigned_at <= :toDate))");
        }
        if (taskType != null && !taskType.isBlank()) {
            whereClause.append(" AND EXISTS (SELECT 1 FROM answer_evaluations sa WHERE sa.source = 'AI' AND sa.tenant_id = assignment_scores.tenant_id AND sa.session_public_id = assignment_scores.session_public_id AND sa.attempt_public_id = assignment_scores.attempt_public_id AND sa.task_type = :taskType AND NOT sa.deleted)");
        }

        String filterCte = CTE_SQL + ",\nfiltered_assignments AS (\n    SELECT * FROM assignment_scores" + whereClause + "\n)\n";

        // 1. Summary Query
        String summarySql = filterCte + """
                SELECT
                    COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) AS finalized_count,
                    COUNT(CASE WHEN status = 'IN_PROGRESS' THEN 1 END) AS in_progress_count,
                    COUNT(CASE WHEN status = 'PENDING' THEN 1 END) AS pending_count,
                    COUNT(*) AS total_assigned_count,
                    COALESCE(AVG(CASE WHEN status = 'COMPLETED' AND started_at IS NOT NULL AND finalized_at IS NOT NULL AND finalized_at >= started_at THEN EXTRACT(EPOCH FROM (finalized_at - started_at)) / 60.0 END), 0.0) AS avg_duration_minutes,
                    COUNT(CASE WHEN work_version > 2 THEN 1 END) AS regrade_count
                FROM filtered_assignments
                """;

        Query summaryQuery = entityManager.createNativeQuery(summarySql);
        bindReportParams(summaryQuery, tenantId, examinerPublicId, sessionPublicId, status, fromDate, toDate, taskType);
        Object[] summaryRow = (Object[]) summaryQuery.getSingleResult();

        long finalizedCount = toLong(summaryRow[0]);
        long inProgressCount = toLong(summaryRow[1]);
        long pendingCount = toLong(summaryRow[2]);
        long totalAssignedCount = toLong(summaryRow[3]);
        double avgDuration = toDouble(summaryRow[4]) != null ? toDouble(summaryRow[4]) : 0.0;
        long regradeCount = toLong(summaryRow[5]);

        ExaminerReportsResponse.ExaminerReportSummary summary =
                new ExaminerReportsResponse.ExaminerReportSummary(
                        finalizedCount, inProgressCount, pendingCount, totalAssignedCount, avgDuration, regradeCount);

        // 2. Task Type Breakdown Query
        String breakdownSql = filterCte + """
                SELECT
                    sa.task_type,
                    COUNT(DISTINCT sa.answer_public_id) AS total_questions,
                    COUNT(DISTINCT sc.id) AS scored_questions,
                    AVG(sc.score) AS average_score
                FROM filtered_assignments a
                JOIN answer_evaluations sa ON sa.source = 'AI' AND sa.tenant_id = a.tenant_id
                    AND sa.session_public_id = a.session_public_id
                    AND sa.attempt_public_id = a.attempt_public_id
                    AND NOT sa.deleted
                LEFT JOIN answer_evaluations sc ON sc.source = 'EXAMINER' AND sc.examiner_status = 'SUBMITTED'
                    AND sc.tenant_id = a.tenant_id
                    AND sc.session_public_id = a.session_public_id
                    AND sc.attempt_public_id = a.attempt_public_id
                    AND sc.answer_public_id = sa.answer_public_id
                    AND sc.examiner_public_id = a.examiner_public_id
                    AND NOT sc.deleted
                GROUP BY sa.task_type
                ORDER BY sa.task_type ASC
                """;

        Query breakdownQuery = entityManager.createNativeQuery(breakdownSql);
        bindReportParams(breakdownQuery, tenantId, examinerPublicId, sessionPublicId, status, fromDate, toDate, taskType);

        @SuppressWarnings("unchecked")
        List<Object[]> breakdownRows = breakdownQuery.getResultList();
        List<ExaminerReportsResponse.ExaminerReportTaskTypeBreakdown> taskTypeBreakdowns = breakdownRows.stream()
                .map(row -> new ExaminerReportsResponse.ExaminerReportTaskTypeBreakdown(
                        row[0] != null ? row[0].toString() : "",
                        toLong(row[1]),
                        toLong(row[2]),
                        toDouble(row[3])
                ))
                .toList();

        // 3. Recent Finalized Items Query
        String recentSql = filterCte + """
                SELECT
                    a.attempt_public_id,
                    a.session_public_id,
                    a.finalized_at,
                    COALESCE(COUNT(sc.id), 0) AS score_count,
                    AVG(sc.score) AS average_score,
                    a.work_version,
                    a.attempt_ordinal
                FROM filtered_assignments a
                LEFT JOIN answer_evaluations sc ON sc.source = 'EXAMINER' AND sc.examiner_status = 'SUBMITTED'
                    AND sc.tenant_id = a.tenant_id
                    AND sc.session_public_id = a.session_public_id
                    AND sc.attempt_public_id = a.attempt_public_id
                    AND sc.examiner_public_id = a.examiner_public_id
                    AND NOT sc.deleted
                WHERE a.status = 'COMPLETED'
                GROUP BY a.attempt_public_id, a.session_public_id, a.finalized_at, a.work_version, a.attempt_ordinal
                ORDER BY a.finalized_at DESC NULLS LAST, a.attempt_public_id ASC
                LIMIT 10
                """;

        Query recentQuery = entityManager.createNativeQuery(recentSql);
        bindReportParams(recentQuery, tenantId, examinerPublicId, sessionPublicId, status, fromDate, toDate, taskType);

        @SuppressWarnings("unchecked")
        List<Object[]> recentRows = recentQuery.getResultList();
        List<ExaminerReportsResponse.ExaminerReportRecentItem> recentFinalizedItems = recentRows.stream()
                .map(row -> new ExaminerReportsResponse.ExaminerReportRecentItem(
                        toUUID(row[0]),
                        toUUID(row[1]),
                        toInstant(row[2]),
                        toLong(row[3]),
                        toDouble(row[4]),
                        toLong(row[5]),
                        null,
                        toInt(row[6])
                ))
                .toList();

        return new ExaminerReportsResponse(summary, taskTypeBreakdowns, recentFinalizedItems);
    }

    private void bindReportParams(Query query, UUID tenantId, UUID examinerPublicId,
            UUID sessionPublicId, String status, Instant fromDate, Instant toDate, String taskType) {
        query.setParameter("tenantId", tenantId);
        query.setParameter("examinerPublicId", examinerPublicId);
        if (sessionPublicId != null) {
            query.setParameter("sessionPublicId", sessionPublicId);
        }
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            query.setParameter("status", status.trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (fromDate != null) {
            query.setParameter("fromDate", fromDate);
        }
        if (toDate != null) {
            query.setParameter("toDate", toDate);
        }
        if (taskType != null && !taskType.isBlank()) {
            query.setParameter("taskType", taskType.trim());
        }
    }
}
