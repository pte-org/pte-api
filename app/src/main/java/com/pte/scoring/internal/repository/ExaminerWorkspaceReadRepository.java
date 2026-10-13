package com.pte.scoring.internal.repository;

import com.pte.scoring.dto.response.ExaminerQueueItemResponse;
import com.pte.scoring.dto.response.ExaminerReportsResponse;
import com.pte.scoring.dto.response.ExaminerWorkspaceOverviewResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Dedicated read repository using a shared native CTE projection for Examiner queue,
 * overview aggregates, and priority previews. Guarantees 100% semantic alignment.
 */
@Repository
public class ExaminerWorkspaceReadRepository {

    private final EntityManager entityManager;

    public ExaminerWorkspaceReadRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    private static final String CTE_SQL = """
            WITH assignment_scores AS (
                SELECT
                    assignment.id AS assignment_id,
                    assignment.public_id AS assignment_public_id,
                    assignment.tenant_id,
                    assignment.session_public_id,
                    assignment.attempt_public_id,
                    assignment.examiner_public_id,
                    assignment.assigned_at,
                    assignment.eligible_answer_count,
                    COALESCE(sc.score_count, 0) AS submitted_answer_count,
                    work.started_at,
                    work.finalized_at,
                    COALESCE(work.version, 0) AS work_version,
                    CASE
                        WHEN work.status IS NOT NULL THEN work.status
                        WHEN assignment.eligible_answer_count = 0 THEN 'PENDING'
                        WHEN COALESCE(sc.score_count, 0) = 0 THEN 'PENDING'
                        WHEN COALESCE(sc.score_count, 0) < assignment.eligible_answer_count THEN 'IN_PROGRESS'
                        ELSE 'COMPLETED'
                    END AS status,
                    CASE
                        WHEN state.publication_public_id IS NOT NULL THEN false
                        WHEN cohort.id IS NOT NULL
                             AND cohort.marking_mode = 'MANUAL_EXAMINER'
                             AND cohort.status IN ('FROZEN', 'COMPLETED')
                             AND member.id IS NOT NULL
                             AND NOT member.excluded THEN true
                        ELSE false
                    END AS can_finalize,
                    CASE
                        WHEN state.publication_public_id IS NOT NULL THEN 'ALREADY_PUBLISHED'
                        WHEN cohort.id IS NULL THEN 'WAITING_FOR_HOST_FREEZE'
                        WHEN cohort.marking_mode <> 'MANUAL_EXAMINER' THEN 'COHORT_NOT_MANUAL_EXAMINER'
                        WHEN cohort.status NOT IN ('FROZEN', 'COMPLETED') THEN 'WAITING_FOR_HOST_FREEZE'
                        WHEN member.id IS NULL OR member.excluded THEN 'EXCLUDED_FROM_COHORT'
                        ELSE NULL
                    END AS blocking_reason,
                    CASE
                        WHEN assignment.eligible_answer_count > 0
                             AND COALESCE(sc.score_count, 0) < assignment.eligible_answer_count THEN true
                        ELSE false
                    END AS has_outstanding_answers
                FROM examiner_attempt_assignments assignment
                LEFT JOIN (
                    SELECT
                        score.tenant_id,
                        score.session_public_id,
                        score.attempt_public_id,
                        COUNT(score.id) AS score_count
                    FROM examiner_answer_scores score
                    WHERE score.tenant_id = :tenantId
                    GROUP BY score.tenant_id, score.session_public_id, score.attempt_public_id
                ) sc ON sc.tenant_id = assignment.tenant_id
                    AND sc.session_public_id = assignment.session_public_id
                    AND sc.attempt_public_id = assignment.attempt_public_id
                LEFT JOIN examiner_grading_work work ON work.tenant_id = assignment.tenant_id
                    AND work.assignment_public_id = assignment.public_id
                    AND NOT work.deleted
                LEFT JOIN scoring_session_states state ON state.tenant_id = assignment.tenant_id
                    AND state.session_public_id = assignment.session_public_id
                LEFT JOIN session_grading_cohorts cohort ON cohort.tenant_id = assignment.tenant_id
                    AND cohort.session_public_id = assignment.session_public_id
                    AND NOT cohort.deleted
                LEFT JOIN session_grading_cohort_members member ON member.cohort_public_id = cohort.public_id
                    AND member.attempt_public_id = assignment.attempt_public_id
                    AND NOT member.deleted
                WHERE assignment.tenant_id = :tenantId
                  AND assignment.examiner_public_id = :examinerPublicId
                  AND NOT assignment.deleted
                  AND (state.publication_public_id IS NULL OR work.status = 'COMPLETED' OR (work.status IS NULL AND COALESCE(sc.score_count, 0) >= assignment.eligible_answer_count AND assignment.eligible_answer_count > 0))
            )
            """;

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
                    has_outstanding_answers
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
                    has_outstanding_answers
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

        return new ExaminerQueueItemResponse(
                attemptPublicId,
                sessionPublicId,
                assignedAt,
                eligibleAnswerCount,
                submittedAnswerCount,
                status,
                canFinalize,
                blockingReason,
                hasOutstandingAnswers
        );
    }

    private static Instant toInstant(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Instant i) return i;
        if (obj instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (obj instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        return Instant.parse(obj.toString());
    }

    private static UUID toUUID(Object obj) {
        if (obj == null) return null;
        if (obj instanceof UUID u) return u;
        return UUID.fromString(obj.toString());
    }

    private static long toLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number n) return n.longValue();
        return Long.parseLong(obj.toString());
    }

    private static int toInt(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number n) return n.intValue();
        return Integer.parseInt(obj.toString());
    }

    private static boolean toBoolean(Object obj) {
        if (obj == null) return false;
        if (obj instanceof Boolean b) return b;
        return Boolean.parseBoolean(obj.toString());
    }

    private static Double toDouble(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number n) return n.doubleValue();
        return Double.parseDouble(obj.toString());
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
            whereClause.append(" AND EXISTS (SELECT 1 FROM scoring_answers sa WHERE sa.tenant_id = assignment_scores.tenant_id AND sa.session_public_id = assignment_scores.session_public_id AND sa.attempt_public_id = assignment_scores.attempt_public_id AND sa.task_type = :taskType AND NOT sa.deleted)");
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
                JOIN scoring_answers sa ON sa.tenant_id = a.tenant_id
                    AND sa.session_public_id = a.session_public_id
                    AND sa.attempt_public_id = a.attempt_public_id
                    AND NOT sa.deleted
                LEFT JOIN examiner_answer_scores sc ON sc.tenant_id = a.tenant_id
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
                    a.work_version
                FROM filtered_assignments a
                LEFT JOIN examiner_answer_scores sc ON sc.tenant_id = a.tenant_id
                    AND sc.session_public_id = a.session_public_id
                    AND sc.attempt_public_id = a.attempt_public_id
                    AND sc.examiner_public_id = a.examiner_public_id
                    AND NOT sc.deleted
                WHERE a.status = 'COMPLETED'
                GROUP BY a.attempt_public_id, a.session_public_id, a.finalized_at, a.work_version
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
                        toLong(row[5])
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
