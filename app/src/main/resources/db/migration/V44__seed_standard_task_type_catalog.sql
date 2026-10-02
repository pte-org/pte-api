-- Complete the platform-owned catalog after V37 created the table without
-- seed data. V40 has already renamed the three legacy fill-blank identifiers;
-- the compatibility update below is defensive for databases that imported an
-- old catalog before V40.
--
-- Existing presentation/order/lifecycle values are preserved. An unexpected
-- code aborts the migration instead of being guessed or silently discarded.

CREATE TEMP TABLE _pte_standard_task_type_catalog (
    code VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(128) NOT NULL,
    short_name VARCHAR(32) NOT NULL,
    section VARCHAR(16) NOT NULL,
    scored BOOLEAN NOT NULL,
    display_order INTEGER NOT NULL,
    requires_audio_prompt BOOLEAN NOT NULL,
    requires_image_prompt BOOLEAN NOT NULL,
    requires_prompt_text BOOLEAN NOT NULL,
    requires_options BOOLEAN NOT NULL,
    requires_correct_answer BOOLEAN NOT NULL,
    requires_word_count BOOLEAN NOT NULL,
    requires_single_correct_option BOOLEAN NOT NULL,
    uses_option_order_as_correct_position BOOLEAN NOT NULL
) ON COMMIT DROP;

INSERT INTO _pte_standard_task_type_catalog (
    code, display_name, short_name, section, scored, display_order,
    requires_audio_prompt, requires_image_prompt, requires_prompt_text,
    requires_options, requires_correct_answer, requires_word_count,
    requires_single_correct_option, uses_option_order_as_correct_position
) VALUES
    ('PERSONAL_INTRODUCTION', 'Personal Introduction', 'PI', 'SPEAKING', FALSE, 1, FALSE, FALSE, TRUE,  FALSE, FALSE, FALSE, FALSE, FALSE),
    ('READ_ALOUD', 'Read Aloud', 'RA', 'SPEAKING', TRUE, 2, FALSE, FALSE, TRUE,  FALSE, FALSE, FALSE, FALSE, FALSE),
    ('REPEAT_SENTENCE', 'Repeat Sentence', 'RS', 'SPEAKING', TRUE, 3, TRUE,  FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
    ('DESCRIBE_IMAGE', 'Describe Image', 'DI', 'SPEAKING', TRUE, 4, FALSE, TRUE,  FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
    ('RE_TELL_LECTURE', 'Re-tell Lecture', 'RL', 'SPEAKING', TRUE, 5, TRUE,  FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
    ('ANSWER_SHORT_QUESTION', 'Answer Short Question', 'ASQ', 'SPEAKING', TRUE, 6, TRUE,  FALSE, FALSE, FALSE, TRUE,  FALSE, FALSE, FALSE),
    ('RESPOND_TO_A_SITUATION', 'Respond to a Situation', 'RTS', 'SPEAKING', TRUE, 7, TRUE,  FALSE, TRUE,  FALSE, FALSE, FALSE, FALSE, FALSE),
    ('SUMMARIZE_GROUP_DISCUSSION', 'Summarize Group Discussion', 'SGD', 'SPEAKING', TRUE, 8, TRUE,  FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
    ('SUMMARIZE_WRITTEN_TEXT', 'Summarize Written Text', 'SWT', 'WRITING', TRUE, 9, FALSE, FALSE, TRUE,  FALSE, FALSE, TRUE,  FALSE, FALSE),
    ('WRITE_ESSAY', 'Write Essay', 'WE', 'WRITING', TRUE, 10, FALSE, FALSE, TRUE, FALSE, FALSE, TRUE,  FALSE, FALSE),
    ('MC_READING_SINGLE', 'Multiple-choice Reading (Single)', 'MCS-R', 'READING', TRUE, 11, FALSE, FALSE, TRUE, TRUE, TRUE, FALSE, TRUE,  FALSE),
    ('MC_READING_MULTIPLE', 'Multiple-choice Reading (Multiple)', 'MCM-R', 'READING', TRUE, 12, FALSE, FALSE, TRUE, TRUE, TRUE, FALSE, FALSE, FALSE),
    ('RE_ORDER_PARAGRAPHS', 'Re-order Paragraphs', 'RO', 'READING', TRUE, 13, FALSE, FALSE, FALSE, TRUE, FALSE, FALSE, FALSE, TRUE),
    ('FILL_IN_THE_BLANKS_DRAG_AND_DROP', 'Fill in the Blanks (Drag and Drop)', 'FIB-DND', 'READING', TRUE, 14, FALSE, FALSE, TRUE, TRUE, TRUE, FALSE, FALSE, FALSE),
    ('FILL_IN_THE_BLANKS_DROPDOWN', 'Fill in the Blanks (Dropdown)', 'FIB-DD', 'READING', TRUE, 15, FALSE, FALSE, TRUE, TRUE, TRUE, FALSE, FALSE, FALSE),
    ('SUMMARIZE_SPOKEN_TEXT', 'Summarize Spoken Text', 'SST', 'LISTENING', TRUE, 16, TRUE, FALSE, FALSE, FALSE, FALSE, TRUE, FALSE, FALSE),
    ('MC_LISTENING_SINGLE', 'Multiple-choice Listening (Single)', 'MCS-L', 'LISTENING', TRUE, 17, TRUE, FALSE, FALSE, TRUE, TRUE, FALSE, TRUE, FALSE),
    ('MC_LISTENING_MULTIPLE', 'Multiple-choice Listening (Multiple)', 'MCM-L', 'LISTENING', TRUE, 18, TRUE, FALSE, FALSE, TRUE, TRUE, FALSE, FALSE, FALSE),
    ('FILL_IN_THE_BLANKS_TYPE_IN', 'Fill in the Blanks (Type In)', 'FIB-TI', 'LISTENING', TRUE, 19, TRUE, FALSE, TRUE, FALSE, TRUE, FALSE, FALSE, FALSE),
    ('HIGHLIGHT_CORRECT_SUMMARY', 'Highlight Correct Summary', 'HCS', 'LISTENING', TRUE, 20, TRUE, FALSE, FALSE, TRUE, TRUE, FALSE, FALSE, FALSE),
    ('SELECT_MISSING_WORD', 'Select Missing Word', 'SMW', 'LISTENING', TRUE, 21, TRUE, FALSE, FALSE, TRUE, TRUE, FALSE, FALSE, FALSE),
    ('HIGHLIGHT_INCORRECT_WORDS', 'Highlight Incorrect Words', 'HIW', 'LISTENING', TRUE, 22, TRUE, FALSE, TRUE, FALSE, TRUE, FALSE, FALSE, FALSE),
    ('WRITE_FROM_DICTATION', 'Write From Dictation', 'WFD', 'LISTENING', TRUE, 23, TRUE, FALSE, FALSE, FALSE, TRUE, FALSE, FALSE, FALSE);

DO $$
DECLARE
    canonicalized_count INTEGER;
    preserved_count INTEGER;
    inserted_count INTEGER;
    unknown_codes TEXT;
BEGIN
    WITH aliases(legacy_code, canonical_code) AS (
        VALUES
            ('FILL_BLANKS_READING_WRITING', 'FILL_IN_THE_BLANKS_DROPDOWN'),
            ('FILL_BLANKS_READING', 'FILL_IN_THE_BLANKS_DRAG_AND_DROP'),
            ('FILL_BLANKS_LISTENING', 'FILL_IN_THE_BLANKS_TYPE_IN')
    )
    UPDATE question_types existing
       SET code = aliases.canonical_code
      FROM aliases
     WHERE existing.code = aliases.legacy_code
       AND NOT EXISTS (
           SELECT 1
             FROM question_types canonical
            WHERE canonical.code = aliases.canonical_code
       );
    GET DIAGNOSTICS canonicalized_count = ROW_COUNT;

    SELECT string_agg(existing.code, ', ' ORDER BY existing.code)
      INTO unknown_codes
      FROM question_types existing
     WHERE NOT EXISTS (
         SELECT 1
           FROM _pte_standard_task_type_catalog standard
          WHERE standard.code = existing.code
     );
    IF unknown_codes IS NOT NULL THEN
        RAISE EXCEPTION 'Unknown question type catalog code(s); migration requires explicit mapping: %', unknown_codes;
    END IF;

    SELECT COUNT(*)
      INTO preserved_count
      FROM question_types existing
      JOIN _pte_standard_task_type_catalog standard ON standard.code = existing.code;

    INSERT INTO question_types (
        public_id, created_at, updated_at, deleted, code, display_name,
        short_name, section, scored, active, display_order,
        requires_audio_prompt, requires_image_prompt, requires_prompt_text,
        requires_options, requires_correct_answer, requires_word_count,
        requires_single_correct_option, uses_option_order_as_correct_position
    )
    SELECT gen_random_uuid(), now(), now(), FALSE, code, display_name,
           short_name, section, scored, TRUE, display_order,
           requires_audio_prompt, requires_image_prompt, requires_prompt_text,
           requires_options, requires_correct_answer, requires_word_count,
           requires_single_correct_option, uses_option_order_as_correct_position
      FROM _pte_standard_task_type_catalog
    ON CONFLICT (code) DO NOTHING;
    GET DIAGNOSTICS inserted_count = ROW_COUNT;

    RAISE NOTICE 'Task-type catalog migration: inserted %, preserved %, canonicalized legacy rows %',
        inserted_count, preserved_count, canonicalized_count;
END $$;

DROP TABLE _pte_standard_task_type_catalog;
