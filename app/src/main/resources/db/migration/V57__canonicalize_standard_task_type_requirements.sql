-- Repair standard task-type authoring requirements that may have been
-- preserved from a pre-catalog database. Custom task types are intentionally
-- excluded; their requirements come from their own released screen contract.
CREATE TEMP TABLE _pte_standard_task_type_requirements (
    code VARCHAR(64) PRIMARY KEY,
    requires_audio_prompt BOOLEAN NOT NULL,
    requires_image_prompt BOOLEAN NOT NULL,
    requires_prompt_text BOOLEAN NOT NULL,
    requires_options BOOLEAN NOT NULL,
    requires_correct_answer BOOLEAN NOT NULL,
    requires_word_count BOOLEAN NOT NULL,
    requires_single_correct_option BOOLEAN NOT NULL,
    uses_option_order_as_correct_position BOOLEAN NOT NULL
) ON COMMIT DROP;

INSERT INTO _pte_standard_task_type_requirements (
    code, requires_audio_prompt, requires_image_prompt, requires_prompt_text,
    requires_options, requires_correct_answer, requires_word_count,
    requires_single_correct_option, uses_option_order_as_correct_position
) VALUES
    ('PERSONAL_INTRODUCTION', FALSE, FALSE, TRUE,  FALSE, FALSE, FALSE, FALSE, FALSE),
    ('READ_ALOUD', FALSE, FALSE, TRUE,  FALSE, FALSE, FALSE, FALSE, FALSE),
    ('REPEAT_SENTENCE', TRUE,  FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
    ('DESCRIBE_IMAGE', FALSE, TRUE,  FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
    ('RE_TELL_LECTURE', TRUE,  FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
    ('ANSWER_SHORT_QUESTION', TRUE, FALSE, FALSE, FALSE, TRUE,  FALSE, FALSE, FALSE),
    ('RESPOND_TO_A_SITUATION', TRUE, FALSE, TRUE,  FALSE, FALSE, FALSE, FALSE, FALSE),
    ('SUMMARIZE_GROUP_DISCUSSION', TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
    ('SUMMARIZE_WRITTEN_TEXT', FALSE, FALSE, TRUE,  FALSE, FALSE, TRUE,  FALSE, FALSE),
    ('WRITE_ESSAY', FALSE, FALSE, TRUE,  FALSE, FALSE, TRUE,  FALSE, FALSE),
    ('MC_READING_SINGLE', FALSE, FALSE, TRUE,  TRUE,  TRUE,  FALSE, TRUE,  FALSE),
    ('MC_READING_MULTIPLE', FALSE, FALSE, TRUE,  TRUE,  TRUE,  FALSE, FALSE, FALSE),
    ('RE_ORDER_PARAGRAPHS', FALSE, FALSE, FALSE, TRUE,  FALSE, FALSE, FALSE, TRUE),
    ('FILL_IN_THE_BLANKS_DRAG_AND_DROP', FALSE, FALSE, TRUE, TRUE, TRUE, FALSE, FALSE, FALSE),
    ('FILL_IN_THE_BLANKS_DROPDOWN', FALSE, FALSE, TRUE, TRUE, TRUE, FALSE, FALSE, FALSE),
    ('SUMMARIZE_SPOKEN_TEXT', TRUE, FALSE, FALSE, FALSE, FALSE, TRUE, FALSE, FALSE),
    ('MC_LISTENING_SINGLE', TRUE, FALSE, FALSE, TRUE, TRUE, FALSE, TRUE, FALSE),
    ('MC_LISTENING_MULTIPLE', TRUE, FALSE, FALSE, TRUE, TRUE, FALSE, FALSE, FALSE),
    ('FILL_IN_THE_BLANKS_TYPE_IN', TRUE, FALSE, TRUE, FALSE, TRUE, FALSE, FALSE, FALSE),
    ('HIGHLIGHT_CORRECT_SUMMARY', TRUE, FALSE, FALSE, TRUE, TRUE, FALSE, FALSE, FALSE),
    ('SELECT_MISSING_WORD', TRUE, FALSE, FALSE, TRUE, TRUE, FALSE, FALSE, FALSE),
    ('HIGHLIGHT_INCORRECT_WORDS', TRUE, FALSE, TRUE, FALSE, TRUE, FALSE, FALSE, FALSE),
    ('WRITE_FROM_DICTATION', TRUE, FALSE, FALSE, FALSE, TRUE, FALSE, FALSE, FALSE);

UPDATE question_types definition
   SET requires_audio_prompt = requirements.requires_audio_prompt,
       requires_image_prompt = requirements.requires_image_prompt,
       requires_prompt_text = requirements.requires_prompt_text,
       requires_options = requirements.requires_options,
       requires_correct_answer = requirements.requires_correct_answer,
       requires_word_count = requirements.requires_word_count,
       requires_single_correct_option = requirements.requires_single_correct_option,
       uses_option_order_as_correct_position = requirements.uses_option_order_as_correct_position,
       updated_at = now()
  FROM _pte_standard_task_type_requirements requirements
 WHERE definition.code = requirements.code
   AND definition.deleted = FALSE;

UPDATE task_runtime_contracts contract
   SET requires_audio_prompt = requirements.requires_audio_prompt,
       requires_image_prompt = requirements.requires_image_prompt,
       requires_prompt_text = requirements.requires_prompt_text,
       requires_options = requirements.requires_options,
       requires_correct_answer = requirements.requires_correct_answer,
       requires_word_count = requirements.requires_word_count,
       requires_single_correct_option = requirements.requires_single_correct_option,
       uses_option_order_as_correct_position = requirements.uses_option_order_as_correct_position,
       updated_at = now()
  FROM _pte_standard_task_type_requirements requirements
 WHERE contract.screen_key = requirements.code || '_V1'
   AND contract.contract_version = 1
   AND contract.deleted = FALSE;

DROP TABLE _pte_standard_task_type_requirements;
