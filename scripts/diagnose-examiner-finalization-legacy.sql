-- Read-only diagnostics for Examiner score finalization and legacy alignment
-- Bounded identifiers only, no student PII, no presigned URLs.

-- 1. Count of legacy scores aligned by V94 (LEGACY_IMPORTED)
SELECT 'ALIGNED_LEGACY_IMPORTED' AS category,
       COUNT(*) AS total_scores,
       COUNT(DISTINCT eas.session_public_id) AS distinct_sessions
FROM examiner_answer_scores eas
WHERE eas.decision_kind = 'LEGACY_IMPORTED'
  AND eas.deleted = FALSE;

-- 2. Cohort-less sessions with examiner scores (require explicit cohort freeze before finalization)
SELECT 'COHORT_LESS_EXAMINER_SCORES' AS category,
       COUNT(*) AS total_scores,
       COUNT(DISTINCT eas.session_public_id) AS distinct_sessions
FROM examiner_answer_scores eas
WHERE eas.deleted = FALSE
  AND NOT EXISTS (
      SELECT 1 FROM session_grading_cohorts sgc
      WHERE sgc.session_public_id = eas.session_public_id
        AND sgc.tenant_id = eas.tenant_id
        AND sgc.deleted = FALSE
  );

-- Sample bounded identifiers for cohort-less sessions (up to 20)
SELECT eas.tenant_id, eas.session_public_id, COUNT(*) AS score_count
FROM examiner_answer_scores eas
WHERE eas.deleted = FALSE
  AND NOT EXISTS (
      SELECT 1 FROM session_grading_cohorts sgc
      WHERE sgc.session_public_id = eas.session_public_id
        AND sgc.tenant_id = eas.tenant_id
        AND sgc.deleted = FALSE
  )
GROUP BY eas.tenant_id, eas.session_public_id
LIMIT 20;

-- 3. Excluded attempt members that have examiner scores
SELECT 'EXCLUDED_ATTEMPT_SCORES' AS category,
       COUNT(*) AS total_scores,
       COUNT(DISTINCT eas.session_public_id) AS distinct_sessions
FROM examiner_answer_scores eas
JOIN session_grading_cohorts sgc
    ON sgc.session_public_id = eas.session_public_id
    AND sgc.tenant_id = eas.tenant_id
    AND sgc.deleted = FALSE
JOIN session_grading_cohort_members sgcm
    ON sgcm.cohort_public_id = sgc.public_id
    AND sgcm.attempt_public_id = eas.attempt_public_id
    AND sgcm.excluded = TRUE
WHERE eas.deleted = FALSE;

-- 4. Published sessions with examiner scores (untouched by legacy alignment)
SELECT 'PUBLISHED_SESSIONS_EXAMINER_SCORES' AS category,
       COUNT(*) AS total_scores,
       COUNT(DISTINCT eas.session_public_id) AS distinct_sessions
FROM examiner_answer_scores eas
WHERE eas.deleted = FALSE
  AND (
      EXISTS (
          SELECT 1 FROM scoring_session_states sss
          WHERE sss.session_public_id = eas.session_public_id
            AND sss.tenant_id = eas.tenant_id
            AND sss.publication_public_id IS NOT NULL
      )
      OR EXISTS (
          SELECT 1 FROM attempt_reports ar
          WHERE ar.session_public_id = eas.session_public_id
            AND ar.tenant_id = eas.tenant_id
            AND ar.published = TRUE
      )
  );

-- 5. AI_ONLY cohorts with examiner scores (untouched by legacy alignment)
SELECT 'AI_ONLY_COHORT_EXAMINER_SCORES' AS category,
       COUNT(*) AS total_scores,
       COUNT(DISTINCT eas.session_public_id) AS distinct_sessions
FROM examiner_answer_scores eas
JOIN session_grading_cohorts sgc
    ON sgc.session_public_id = eas.session_public_id
    AND sgc.tenant_id = eas.tenant_id
    AND sgc.marking_mode = 'AI_ONLY'
    AND sgc.deleted = FALSE
WHERE eas.deleted = FALSE;

-- 6. Ownership inconsistencies (score examiner != assignment examiner)
SELECT 'OWNERSHIP_INCONSISTENT_SCORES' AS category,
       COUNT(*) AS total_scores
FROM examiner_answer_scores eas
WHERE eas.deleted = FALSE
  AND NOT EXISTS (
      SELECT 1 FROM examiner_attempt_assignments eaa
      WHERE eaa.tenant_id = eas.tenant_id
        AND eaa.session_public_id = eas.session_public_id
        AND eaa.attempt_public_id = eas.attempt_public_id
        AND eaa.examiner_public_id = eas.examiner_public_id
        AND eaa.deleted = FALSE
  );
