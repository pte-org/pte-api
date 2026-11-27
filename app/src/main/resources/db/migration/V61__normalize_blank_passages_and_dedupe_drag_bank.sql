-- Fill-in-the-blanks passages stored with a blank written some other way than {{n}} (__BLANK__, [BLANK], a run of
-- underscores, a bare {{}}) show no gap to drop a word into: the student sees the raw marker and cannot answer. Number
-- those blanks {{0}}, {{1}}, ... in reading order, the format the exam and practice clients split on.
-- Passages that already carry numbered markers are left as they are.
DO $$
DECLARE
    q RECORD;
    text_value text;
    marker constant text := '__BLANK__|\[\s*BLANK\s*\]|\{\{\s*\}\}|_{3,}';
    gap integer;
BEGIN
    FOR q IN
        SELECT id, prompt_text FROM questions
        WHERE task_type_key IN ('FILL_IN_THE_BLANKS_DROPDOWN', 'FILL_IN_THE_BLANKS_DRAG_AND_DROP',
                                'FILL_IN_THE_BLANKS_TYPE_IN')
          AND prompt_text ~* marker
          AND prompt_text !~ '\{\{\d+\}\}'
    LOOP
        text_value := q.prompt_text;
        gap := 0;
        WHILE text_value ~* marker LOOP
            -- No 'g' flag: only the first marker is replaced, and a numbered one no longer matches the pattern.
            text_value := regexp_replace(text_value, marker, '{{' || gap || '}}', 'i');
            gap := gap + 1;
        END LOOP;
        UPDATE questions SET prompt_text = text_value WHERE id = q.id;
    END LOOP;
END
$$;

-- A drag-and-drop word bank holds the right word of every gap plus extra words. Models often repeated the same extra
-- words under every gap, so the bank listed one word several times, or listed a right answer again as an extra word.
-- Keep each extra word once and never as a copy of a right answer; the right answers themselves are never removed.
DELETE FROM question_options o
USING questions q
WHERE q.id = o.question_id
  AND q.task_type_key = 'FILL_IN_THE_BLANKS_DRAG_AND_DROP'
  AND o.correct = false
  AND EXISTS (
      SELECT 1 FROM question_options other
      WHERE other.question_id = o.question_id
        AND other.public_id <> o.public_id
        AND lower(btrim(other.text)) = lower(btrim(o.text))
        AND (other.correct = true OR other.order_index < o.order_index));
