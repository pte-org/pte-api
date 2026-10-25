package com.pte.scoring.internal.repository;

import com.pte.scoring.dto.response.ExaminerQueueItemResponse;
import com.pte.scoring.dto.response.ExaminerReportsResponse;
import com.pte.scoring.dto.response.ExaminerWorkspaceOverviewResponse;
import com.pte.scoring.internal.repository.workspace.ExaminerReportsReadQuery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.pte.scoring.internal.repository.workspace.ExaminerAssignmentScoresCte.CTE_SQL;
import static com.pte.scoring.internal.repository.workspace.NativeColumnValues.toBoolean;
import static com.pte.scoring.internal.repository.workspace.NativeColumnValues.toInstant;
import static com.pte.scoring.internal.repository.workspace.NativeColumnValues.toInt;
import static com.pte.scoring.internal.repository.workspace.NativeColumnValues.toLong;
import static com.pte.scoring.internal.repository.workspace.NativeColumnValues.toUUID;

/**
 * Dedicated read repository using a shared native CTE projection for Examiner queue,
 * overview aggregates, and priority previews. Guarantees 100% semantic alignment.
 */
@Repository
public class ExaminerWorkspaceReadRepository {

    private final EntityManager entityManager;
    private final ExaminerReportsReadQuery reportsQuery;

    public ExaminerWorkspaceReadRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.reportsQuery = new ExaminerReportsReadQuery(entityManager);
    }

    public ExaminerWorkspaceOverviewResponse getWorkspaceOverview(
            UUID tenantId, UUID examinerPublicId, Instant asOf) {
        String countsSql = CTE_SQL + """
                SELECT
                    COUNT(CASE WHEN status = 'PENDING' THEN 1 END) AS pending_count,
                    COUNT(CASE WHEN status = 'IN_PROGRESS' THEN 1 END) AS in_progress_count,
                    COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) AS completed_count,
                    COALESCE(SUM(eligible_answer_count), 0) AS eligible_answer_total,
                    COALESCE(SUM(submitted_answer_count), 0) AS finalized_answer_total
                FROM assignment_scores
                """;

        Object[] countsRow = (Object[]) entityManager.createNativeQuery(countsSql)
                .setParameter("tenantId", tenantId)
                .setParameter("examinerPublicId", examinerPublicId)
                .getSingleResult();

        long pending = toLong(countsRow[0]);
        long inProgress = toLong(countsRow[1]);
        long completed = toLong(countsRow[2]);
        long eligibleAnswerTotal = toLong(countsRow[3]);
        long finalizedAnswerTotal = toLong(countsRow[4]);

        ExaminerWorkspaceOverviewResponse.ExaminerOverviewCounts counts =
                new ExaminerWorkspaceOverviewResponse.ExaminerOverviewCounts(pending, inProgress, completed);
        ExaminerWorkspaceOverviewResponse.ExaminerOverviewProgress progress =
                new ExaminerWorkspaceOverviewResponse.ExaminerOverviewProgress(eligibleAnswerTotal, finalizedAnswerTotal);

        String prioritySql = CTE_SQL + """
                SELECT
                    attempt_public_id,
                    session_public_id,
                    assigned_at,
                    eligible_answer_count,
                    submitted_answer_count,
                    status,
                    can_finalize,
                    blocking_reason,
                    has_outstanding_answers,
                    attempt_ordinal
                FROM assignment_scores
                WHERE can_finalize = true
                  AND has_outstanding_answers = true
                  AND status <> 'COMPLETED'
                  AND eligible_answer_count > 0
                ORDER BY
                    CASE WHEN status = 'IN_PROGRESS' THEN 0 ELSE 1 END ASC,
                    assigned_at ASC,
                    attempt_public_id ASC
                LIMIT 5
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> priorityRows = entityManager.createNativeQuery(prioritySql)
                .setParameter("tenantId", tenantId)
                .setParameter("examinerPublicId", examinerPublicId)
                .getResultList();

        List<ExaminerQueueItemResponse> priorityItems = priorityRows.stream()
                .map(this::mapRow)
                .toList();

        return new ExaminerWorkspaceOverviewResponse(
                asOf, "ASSIGNED_WITH_PUBLISHED_COMPLETED", counts, progress, priorityItems);
    }

    public Page<ExaminerQueueItemResponse> findQueue(
            UUID tenantId, UUID examinerPublicId, UUID sessionPublicId, String status, Pageable pageable) {
        return findQueue(tenantId, examinerPublicId, sessionPublicId, status, null, null, pageable);
    }

    public Page<ExaminerQueueItemResponse> findQueue(
            UUID tenantId, UUID examinerPublicId, UUID sessionPublicId, String status, String search, String sort, Pageable pageable) {
        StringBuilder whereClause = new StringBuilder(" WHERE 1=1");
        if (sessionPublicId != null) {
            whereClause.append(" AND session_public_id = :sessionPublicId");
        }
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            whereClause.append(" AND status = :status");
        }
        if (search != null && !search.isBlank()) {
            whereClause.append(" AND (CAST(attempt_public_id AS text) ILIKE :searchPattern OR CAST(session_public_id AS text) ILIKE :searchPattern)");
        }

        String orderClause = " ORDER BY assigned_at DESC, assignment_id DESC";
        if ("ASSIGNED_ASC".equalsIgnoreCase(sort)) {
            orderClause = " ORDER BY assigned_at ASC, assignment_id ASC";
        }

        String selectSql = CTE_SQL + """
                SELECT
                    attempt_public_id,
                    session_public_id,
                    assigned_at,
                    eligible_answer_count,
                    submitted_answer_count,
                    status,
                    can_finalize,
                    blocking_reason,
                    has_outstanding_answers,
                    attempt_ordinal
                FROM assignment_scores
                """ + whereClause + orderClause + """

                LIMIT :limit OFFSET :offset
                """;

        String countSql = CTE_SQL + "\nSELECT COUNT(*) FROM assignment_scores" + whereClause + "\n";

        Query selectQuery = entityManager.createNativeQuery(selectSql)
                .setParameter("tenantId", tenantId)
                .setParameter("examinerPublicId", examinerPublicId)
                .setParameter("limit", pageable.getPageSize())
                .setParameter("offset", pageable.getOffset());

        Query countQuery = entityManager.createNativeQuery(countSql)
                .setParameter("tenantId", tenantId)
                .setParameter("examinerPublicId", examinerPublicId);

        if (sessionPublicId != null) {
            selectQuery.setParameter("sessionPublicId", sessionPublicId);
            countQuery.setParameter("sessionPublicId", sessionPublicId);
        }
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            selectQuery.setParameter("status", status.trim().toUpperCase(java.util.Locale.ROOT));
            countQuery.setParameter("status", status.trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim() + "%";
            selectQuery.setParameter("searchPattern", pattern);
            countQuery.setParameter("searchPattern", pattern);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = selectQuery.getResultList();
        List<ExaminerQueueItemResponse> items = rows.stream().map(this::mapRow).toList();
        long total = toLong(countQuery.getSingleResult());

        return new PageImpl<>(items, pageable, total);
    }

    private ExaminerQueueItemResponse mapRow(Object[] row) {
        UUID attemptPublicId = toUUID(row[0]);
        UUID sessionPublicId = toUUID(row[1]);
        Instant assignedAt = toInstant(row[2]);
        int eligibleAnswerCount = toInt(row[3]);
        long submittedAnswerCount = toLong(row[4]);
        String status = row[5] != null ? row[5].toString() : null;
        boolean canFinalize = toBoolean(row[6]);
        String blockingReason = row[7] != null ? row[7].toString() : null;
        boolean hasOutstandingAnswers = toBoolean(row[8]);
        int attemptOrdinal = toInt(row[9]);

        return new ExaminerQueueItemResponse(
                attemptPublicId,
                sessionPublicId,
                assignedAt,
                eligibleAnswerCount,
                submittedAnswerCount,
                status,
                canFinalize,
                blockingReason,
                hasOutstandingAnswers,
                null,
                attemptOrdinal
        );
    }

    public ExaminerReportsResponse getReports(
            UUID tenantId, UUID examinerPublicId, Instant fromDate, Instant toDate,
            String taskType, String status, UUID sessionPublicId) {
        return reportsQuery.getReports(tenantId, examinerPublicId, fromDate, toDate, taskType, status,
                sessionPublicId);
    }

}
