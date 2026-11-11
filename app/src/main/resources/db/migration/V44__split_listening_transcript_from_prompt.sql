-- Imported listening choice questions stored the recording transcript in prompt_text, followed by a blank line and
-- the question, so the transcript was shown to the candidate. The transcript belongs in reference_answer_text, which
-- is never sent to the candidate; prompt_text keeps only the text after the last blank line (the question).
-- Only rows with no reference text yet are touched, so a question authored the intended way is left as it is.

UPDATE questions
SET reference_answer_text = left(prompt_text, length(prompt_text) - strpos(reverse(prompt_text), E'\n\n') - 1),
    prompt_text = substr(prompt_text, length(prompt_text) - strpos(reverse(prompt_text), E'\n\n') + 2)
WHERE task_type_key IN ('MC_LISTENING_SINGLE', 'MC_LISTENING_MULTIPLE', 'HIGHLIGHT_CORRECT_SUMMARY',
                        'SELECT_MISSING_WORD')
  AND coalesce(reference_answer_text, '') = ''
  AND strpos(prompt_text, E'\n\n') > 0;

-- Published exam versions carry their own copy of each question, which the exam client shows.
UPDATE exam_version_items
SET reference_answer_text = left(prompt_text, length(prompt_text) - strpos(reverse(prompt_text), E'\n\n') - 1),
    prompt_text = substr(prompt_text, length(prompt_text) - strpos(reverse(prompt_text), E'\n\n') + 2)
WHERE task_type_key IN ('MC_LISTENING_SINGLE', 'MC_LISTENING_MULTIPLE', 'HIGHLIGHT_CORRECT_SUMMARY',
                        'SELECT_MISSING_WORD')
  AND coalesce(reference_answer_text, '') = ''
  AND strpos(prompt_text, E'\n\n') > 0;
