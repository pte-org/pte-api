-- The AI scorers need two inputs that no audio file carries: the lecture transcript
-- of Summarize Spoken Text (prompt text) and the sentence of Repeat Sentence
-- (reference answer). Authoring requirements live in the catalog, so both the
-- question-type catalog and the standard runtime contracts must demand them.
ALTER TABLE question_types
    ADD COLUMN requires_reference_answer BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE task_runtime_contracts
    ADD COLUMN requires_reference_answer BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE question_types
   SET requires_prompt_text = TRUE, updated_at = now()
 WHERE code = 'SUMMARIZE_SPOKEN_TEXT' AND deleted = FALSE;

UPDATE question_types
   SET requires_reference_answer = TRUE, updated_at = now()
 WHERE code = 'REPEAT_SENTENCE' AND deleted = FALSE;

UPDATE task_runtime_contracts
   SET requires_prompt_text = TRUE, updated_at = now()
 WHERE screen_key = 'SUMMARIZE_SPOKEN_TEXT_V1' AND contract_version = 1 AND deleted = FALSE;

UPDATE task_runtime_contracts
   SET requires_reference_answer = TRUE, updated_at = now()
 WHERE screen_key = 'REPEAT_SENTENCE_V1' AND contract_version = 1 AND deleted = FALSE;
