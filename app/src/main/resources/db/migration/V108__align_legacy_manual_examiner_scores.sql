-- Align legacy manual examiner scores for unpublished explicit MANUAL_EXAMINER cohorts.
-- Only targets rows with nonexcluded cohort membership and verified assignment ownership.
-- Leaves AI_ONLY, published, excluded, cohort-less, and inconsistent records completely untouched.

-- Step 1: Update examiner_answer_scores with LEGACY_IMPORTED decision_kind
UPDATE examiner_answer_scores eas
SET decision_kind = 'LEGACY_IMPORTED',
    reviewed_ai_score = NULL,
    reviewed_ai_version = NULL,
    expected_review_version = NULL
FROM session_grading_cohorts sgc
JOIN session_grading_cohort_members sgcm
    ON sgcm.cohort_public_id = sgc.public_id
    AND sgcm.excluded = FALSE
JOIN examiner_attempt_assignments eaa
    ON eaa.tenant_id = sgc.tenant_id
    AND eaa.session_public_id = sgc.session_public_id
    AND eaa.attempt_public_id = sgcm.attempt_public_id
    AND eaa.deleted = FALSE
WHERE eas.session_public_id = sgc.session_public_id
  AND eas.tenant_id = sgc.tenant_id
  AND eas.attempt_public_id = sgcm.attempt_public_id
  AND eas.examiner_public_id = eaa.examiner_public_id
  AND sgc.marking_mode = 'MANUAL_EXAMINER'
  AND sgc.deleted = FALSE
  AND eas.decision_kind IS NULL
  AND eas.deleted = FALSE
  -- Guard: session must not be published in session state
  AND NOT EXISTS (
      SELECT 1 FROM scoring_session_states sss
      WHERE sss.session_public_id = sgc.session_public_id
        AND sss.tenant_id = sgc.tenant_id
        AND sss.publication_public_id IS NOT NULL
  )
  -- Guard: session must not have any published reports
  AND NOT EXISTS (
      SELECT 1 FROM attempt_reports ar
      WHERE ar.session_public_id = sgc.session_public_id
        AND ar.tenant_id = sgc.tenant_id
        AND ar.published = TRUE
  );

-- Step 2: Align scoring_answers selected score source to EXAMINER
UPDATE scoring_answers sa
SET selected_score_source = 'EXAMINER',
    selected_score_source_by_public_id = eas.examiner_public_id,
    selected_score_source_at = eas.submitted_at,
    selected_examiner_score_public_id = eas.public_id
FROM examiner_answer_scores eas
JOIN session_grading_cohorts sgc
    ON sgc.session_public_id = eas.session_public_id
    AND sgc.tenant_id = eas.tenant_id
    AND sgc.marking_mode = 'MANUAL_EXAMINER'
    AND sgc.deleted = FALSE
JOIN session_grading_cohort_members sgcm
    ON sgcm.cohort_public_id = sgc.public_id
    AND sgcm.attempt_public_id = eas.attempt_public_id
    AND sgcm.excluded = FALSE
JOIN examiner_attempt_assignments eaa
    ON eaa.tenant_id = eas.tenant_id
    AND eaa.session_public_id = eas.session_public_id
    AND eaa.attempt_public_id = eas.attempt_public_id
    AND eaa.examiner_public_id = eas.examiner_public_id
    AND eaa.deleted = FALSE
WHERE sa.answer_public_id = eas.answer_public_id
  AND sa.tenant_id = eas.tenant_id
  AND eas.decision_kind = 'LEGACY_IMPORTED'
  AND eas.deleted = FALSE
  AND sa.deleted = FALSE
  AND (sa.selected_score_source IS NULL OR sa.selected_score_source <> 'EXAMINER' OR sa.selected_examiner_score_public_id IS NULL)
  -- Guard: session must not be published in session state
  AND NOT EXISTS (
      SELECT 1 FROM scoring_session_states sss
      WHERE sss.session_public_id = sgc.session_public_id
        AND sss.tenant_id = sgc.tenant_id
        AND sss.publication_public_id IS NOT NULL
  )
  -- Guard: session must not have any published reports
  AND NOT EXISTS (
      SELECT 1 FROM attempt_reports ar
      WHERE ar.session_public_id = sgc.session_public_id
        AND ar.tenant_id = sgc.tenant_id
        AND ar.published = TRUE
  );
