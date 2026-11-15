-- Re-tell Lecture, Summarize Spoken Text and Summarize Group Discussion are scored by the AI against the transcript
-- kept in the reference answer; a question saved without it fails every learner's scoring. The code now requires it
-- (PteTaskType.requiresReferenceAnswer), so bring the stored task type definitions in line. Questions already saved
-- are not touched.
UPDATE question_types
SET requires_reference_answer = true
WHERE task_type_key IN ('RE_TELL_LECTURE', 'SUMMARIZE_SPOKEN_TEXT', 'SUMMARIZE_GROUP_DISCUSSION')
  AND requires_reference_answer = false;
