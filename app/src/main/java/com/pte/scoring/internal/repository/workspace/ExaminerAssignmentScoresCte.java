package com.pte.scoring.internal.repository.workspace;

/**
 * Shared native CTE projection ({@code assignment_scores}) behind the Examiner queue, overview aggregates,
 * priority previews and reports. Binds {@code :tenantId} and {@code :examinerPublicId}.
 */
public final class ExaminerAssignmentScoresCte {

    private ExaminerAssignmentScoresCte() {
    }

    public static final String CTE_SQL = """
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
                    assignment.started_at,
                    assignment.finalized_at,
                    assignment.work_version AS work_version,
                    CASE
                        WHEN assignment.work_version > 0 THEN assignment.status
                        WHEN assignment.eligible_answer_count = 0 THEN 'PENDING'
                        WHEN COALESCE(sc.score_count, 0) = 0 THEN 'PENDING'
                        WHEN COALESCE(sc.score_count, 0) < assignment.eligible_answer_count THEN 'IN_PROGRESS'
                        ELSE 'COMPLETED'
                    END AS status,
                    CASE
                        WHEN state.publication_public_id IS NOT NULL THEN false
                        WHEN state.grading_status IS NOT NULL
                             AND state.grading_marking_mode = 'MANUAL_EXAMINER'
                             AND state.grading_status IN ('FROZEN', 'COMPLETED')
                             AND member.id IS NOT NULL
                             AND NOT member.grading_excluded THEN true
                        ELSE false
                    END AS can_finalize,
                    CASE
                        WHEN state.publication_public_id IS NOT NULL THEN 'ALREADY_PUBLISHED'
                        WHEN state.grading_status IS NULL THEN 'WAITING_FOR_HOST_FREEZE'
                        WHEN state.grading_marking_mode <> 'MANUAL_EXAMINER' THEN 'COHORT_NOT_MANUAL_EXAMINER'
                        WHEN state.grading_status NOT IN ('FROZEN', 'COMPLETED') THEN 'WAITING_FOR_HOST_FREEZE'
                        WHEN member.id IS NULL OR member.grading_excluded THEN 'EXCLUDED_FROM_COHORT'
                        ELSE NULL
                    END AS blocking_reason,
                    CASE
                        WHEN assignment.eligible_answer_count > 0
                             AND COALESCE(sc.score_count, 0) < assignment.eligible_answer_count THEN true
                        ELSE false
                    END AS has_outstanding_answers,
                    (SELECT COUNT(*) FROM grading_assignments a2
                        WHERE a2.tenant_id = assignment.tenant_id
                          AND a2.examiner_public_id = assignment.examiner_public_id
                          AND a2.session_public_id = assignment.session_public_id
                          AND NOT a2.deleted
                          AND (a2.assigned_at < assignment.assigned_at
                               OR (a2.assigned_at = assignment.assigned_at AND a2.id <= assignment.id))
                    ) AS attempt_ordinal
                FROM grading_assignments assignment
                LEFT JOIN (
                    SELECT
                        score.tenant_id,
                        score.session_public_id,
                        score.attempt_public_id,
                        COUNT(score.id) AS score_count
                    FROM answer_evaluations score
                    WHERE score.source = 'EXAMINER' AND score.examiner_status = 'SUBMITTED' AND NOT score.deleted
                    AND score.tenant_id = :tenantId
                    GROUP BY score.tenant_id, score.session_public_id, score.attempt_public_id
                ) sc ON sc.tenant_id = assignment.tenant_id
                    AND sc.session_public_id = assignment.session_public_id
                    AND sc.attempt_public_id = assignment.attempt_public_id
                LEFT JOIN exam_sessions state ON state.tenant_id = assignment.tenant_id
                    AND state.public_id = assignment.session_public_id
                    AND NOT state.deleted
                LEFT JOIN exam_attempts member ON member.tenant_id = assignment.tenant_id
                    AND member.public_id = assignment.attempt_public_id
                    AND NOT member.deleted
                WHERE assignment.tenant_id = :tenantId
                  AND assignment.examiner_public_id = :examinerPublicId
                  AND NOT assignment.deleted
                  AND (state.publication_public_id IS NULL OR (assignment.work_version > 0 AND assignment.status = 'COMPLETED') OR (assignment.work_version = 0 AND COALESCE(sc.score_count, 0) >= assignment.eligible_answer_count AND assignment.eligible_answer_count > 0))
            )
            """;
}
