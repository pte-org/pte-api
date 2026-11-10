-- Replaces the ACTIVE platform template with a new version that follows "PTE Content Question Types &
-- Score Information Table V5": question counts, preparation and answer times, and the Overall and
-- per-skill weights of every task type. The previous ACTIVE version is retired, not edited, so
-- exams already published keep the scoring they were pinned to.
--
-- A "<= x min" answer time in the source table is stored as its upper bound (1-1.5 min = 90 s,
-- 1.5-2 min and <= 2 min = 120 s). Each skill column of the source table sums to 100.

CREATE TEMP TABLE tmp_standard_items (
    task_type_key character varying(64) PRIMARY KEY,
    min_count integer NOT NULL,
    max_count integer NOT NULL,
    prep_seconds integer NOT NULL,
    response_seconds integer NOT NULL,
    overall_weight numeric(5, 2) NOT NULL,
    speaking_weight numeric(5, 2) NOT NULL,
    writing_weight numeric(5, 2) NOT NULL,
    reading_weight numeric(5, 2) NOT NULL,
    listening_weight numeric(5, 2) NOT NULL
) ON COMMIT DROP;

INSERT INTO tmp_standard_items VALUES
    ('READ_ALOUD',                       6,  7, 35,   35,  3.50, 14.00,  0.00,  0.00,  0.00),
    ('REPEAT_SENTENCE',                 10, 12,  0,   15,  9.75, 20.00,  0.00,  0.00, 19.00),
    ('DESCRIBE_IMAGE',                   5,  6, 25,   40,  8.50, 34.00,  0.00,  0.00,  0.00),
    ('RE_TELL_LECTURE',                  2,  3, 10,   40,  5.25, 11.00,  0.00,  0.00, 10.00),
    ('ANSWER_SHORT_QUESTION',            5,  6,  0,   10,  1.25,  0.00,  0.00,  0.00,  5.00),
    ('SUMMARIZE_GROUP_DISCUSSION',       2,  3, 10,  120,  7.00, 13.00,  0.00,  0.00, 15.00),
    ('RESPOND_TO_A_SITUATION',           2,  3, 10,   40,  2.00,  8.00,  0.00,  0.00,  0.00),
    ('SUMMARIZE_WRITTEN_TEXT',           2,  2,  0,  600, 11.13,  0.00, 25.50, 19.00,  0.00),
    ('WRITE_ESSAY',                      1,  1,  0, 1200,  6.38,  0.00, 25.50,  0.00,  0.00),
    ('FILL_IN_THE_BLANKS_DROPDOWN',      5,  6,  0,  120,  7.00,  0.00,  0.00, 28.00,  0.00),
    ('MC_READING_MULTIPLE',              2,  3,  0,   90,  1.50,  0.00,  0.00,  6.00,  0.00),
    ('RE_ORDER_PARAGRAPHS',              2,  3,  0,  120,  2.00,  0.00,  0.00,  8.00,  0.00),
    ('FILL_IN_THE_BLANKS_DRAG_AND_DROP', 4,  5,  0,  120,  4.50,  0.00,  0.00, 18.00,  0.00),
    ('MC_READING_SINGLE',                2,  3,  0,   90,  0.50,  0.00,  0.00,  2.00,  0.00),
    ('SUMMARIZE_SPOKEN_TEXT',            1,  1,  0,  600,  8.25,  0.00, 23.00,  0.00, 10.00),
    ('MC_LISTENING_MULTIPLE',            2,  3,  0,   90,  1.00,  0.00,  0.00,  0.00,  4.00),
    ('FILL_IN_THE_BLANKS_TYPE_IN',       2,  3,  0,  120,  2.25,  0.00,  0.00,  0.00,  9.00),
    ('HIGHLIGHT_CORRECT_SUMMARY',        2,  3,  0,   90,  1.00,  0.00,  0.00,  3.00,  1.00),
    ('MC_LISTENING_SINGLE',              2,  3,  0,   90,  0.25,  0.00,  0.00,  0.00,  1.00),
    ('SELECT_MISSING_WORD',              1,  2,  0,   90,  0.25,  0.00,  0.00,  0.00,  1.00),
    ('HIGHLIGHT_INCORRECT_WORDS',        2,  3,  0,  120,  6.75,  0.00,  0.00, 16.00, 11.00),
    ('WRITE_FROM_DICTATION',             3,  4,  0,  120, 10.00,  0.00, 26.00,  0.00, 14.00);

CREATE TEMP TABLE tmp_previous_active ON COMMIT DROP AS
SELECT id, code, name
FROM exam_definitions
WHERE status = 'ACTIVE' AND tenant_id IS NULL;

-- The single-ACTIVE unique index requires the old version to leave ACTIVE first.
UPDATE exam_definitions
SET status = 'RETIRED', updated_at = now()
WHERE id IN (SELECT id FROM tmp_previous_active);

WITH new_definition AS (
    INSERT INTO exam_definitions (created_at, deleted, public_id, updated_at, code, lock_version, name,
                                  status, template_policy, version)
    SELECT now(), false, gen_random_uuid(), now(), p.code, 0, p.name, 'ACTIVE', 'STANDARD_PTE',
           (SELECT max(d.version) FROM exam_definitions d WHERE d.code = p.code AND d.tenant_id IS NULL) + 1
    FROM tmp_previous_active p
    RETURNING id
)
INSERT INTO exam_definition_items (
    exam_definition_id, listening_weight, max_count, min_count, overall_weight, prep_seconds,
    reading_weight, response_seconds, runtime_answer_schema_version, runtime_authoring_contract_key,
    runtime_authoring_contract_version, runtime_behavior_key, runtime_contract_version,
    runtime_min_app_version, runtime_profile_key, runtime_profile_status, runtime_profile_version,
    runtime_renderer_key, runtime_required_client_capabilities, runtime_scoring_mode,
    runtime_scoring_profile_key, runtime_scoring_profile_version, runtime_screen_key, scoring_method,
    section, sequence, speaking_weight, task_type, task_type_key, writing_weight)
SELECT n.id, s.listening_weight, s.max_count, s.min_count, s.overall_weight, s.prep_seconds,
       s.reading_weight, s.response_seconds, i.runtime_answer_schema_version, i.runtime_authoring_contract_key,
       i.runtime_authoring_contract_version, i.runtime_behavior_key, i.runtime_contract_version,
       i.runtime_min_app_version, i.runtime_profile_key, i.runtime_profile_status, i.runtime_profile_version,
       i.runtime_renderer_key, i.runtime_required_client_capabilities, i.runtime_scoring_mode,
       i.runtime_scoring_profile_key, i.runtime_scoring_profile_version, i.runtime_screen_key, i.scoring_method,
       i.section, i.sequence, s.speaking_weight, i.task_type, i.task_type_key, s.writing_weight
FROM new_definition n
CROSS JOIN tmp_previous_active p
JOIN exam_definition_items i ON i.exam_definition_id = p.id
JOIN tmp_standard_items s ON s.task_type_key = i.task_type_key;
