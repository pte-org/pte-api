-- Items created before V128 do not contain the backend-only answer-key
-- snapshot. They cannot be evaluated safely after deterministic scoring is
-- enabled, so discard only affected unfinished sessions and retain every
-- saved payload and audit row for inspection.
WITH legacy_sessions AS (
    SELECT DISTINCT session.id
    FROM practice_sessions session
    JOIN practice_session_items item ON item.practice_session_id = session.id
    WHERE session.deleted = FALSE
      AND session.status IN ('OVERVIEW', 'IN_PROGRESS')
      AND item.deleted = FALSE
      AND item.status = 'PENDING'
      AND (item.objective_options_json IS NULL OR item.objective_options_json = '')
      AND item.renderer_key IN (
          'MC_READING_SINGLE_V1',
          'MC_READING_MULTIPLE_V1',
          'RE_ORDER_PARAGRAPHS_V1',
          'FILL_IN_THE_BLANKS_DRAG_AND_DROP_V1',
          'FILL_IN_THE_BLANKS_DROPDOWN_V1',
          'MC_LISTENING_SINGLE_V1',
          'MC_LISTENING_MULTIPLE_V1',
          'FILL_IN_THE_BLANKS_TYPE_IN_V1',
          'HIGHLIGHT_CORRECT_SUMMARY_V1',
          'SELECT_MISSING_WORD_V1'
      )
), marked_items AS (
    UPDATE practice_session_items item
    SET status = 'SKIPPED',
        skipped_at = COALESCE(skipped_at, CURRENT_TIMESTAMP)
    FROM legacy_sessions legacy
    WHERE item.practice_session_id = legacy.id
      AND item.deleted = FALSE
      AND item.status = 'PENDING'
    RETURNING item.practice_session_id
)
UPDATE practice_sessions session
SET status = 'DISCARDED',
    discarded_at = COALESCE(discarded_at, CURRENT_TIMESTAMP)
WHERE session.deleted = FALSE
  AND session.status IN ('OVERVIEW', 'IN_PROGRESS')
  AND session.id IN (SELECT id FROM legacy_sessions);
