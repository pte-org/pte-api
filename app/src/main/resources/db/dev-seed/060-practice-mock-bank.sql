-- Local only: tops the practice pool up so the mock tests can be built and taken end to end.
-- A mock test takes min_count questions of every task type of the active template (65 in all), and the bank only
-- builds as many tests as every type can fill, so a type with too few practice questions means no mock test at all.
-- For each task type this copies approved questions (the practice ones first, then the exam ones, so the copies are
-- as varied as the bank allows) into the practice pool until it holds three tests' worth. The copies keep the audio and
-- image the original points at. Idempotent: a type that already holds enough is left alone, so a second run adds nothing.
DO $$
DECLARE
    needed RECORD;
    sources bigint[];
    have integer;
    copies integer;
    source_id bigint;
    new_id bigint;
BEGIN
    FOR needed IN
        SELECT i.task_type AS task_key, i.min_count * 3 AS target
        FROM public.exam_definition_items i
        JOIN public.exam_definitions d ON d.id = i.exam_definition_id AND d.status = 'ACTIVE' AND d.tenant_id IS NULL
        WHERE i.task_type IS NOT NULL
    LOOP
        SELECT count(*) INTO have FROM public.questions q
        WHERE q.task_type_key = needed.task_key AND q.deleted = false AND q.is_current = true
          AND q.status = 'APPROVED' AND q.available_for_practice = true;
        copies := needed.target - have;
        CONTINUE WHEN copies <= 0;

        SELECT array_agg(q.id ORDER BY (q.pool = 'EXAM'), q.id) INTO sources FROM public.questions q
        WHERE q.task_type_key = needed.task_key AND q.deleted = false AND q.is_current = true
          AND q.status = 'APPROVED' AND q.tenant_id IS NULL;
        CONTINUE WHEN sources IS NULL;

        FOR n IN 0 .. copies - 1 LOOP
            source_id := sources[(n % array_length(sources, 1)) + 1];
            INSERT INTO public.questions (created_at, updated_at, deleted, public_id, audio_prompt_ref, image_prompt_ref,
                    author_user_public_id, availability_flags_explicit, available_for_exam, available_for_practice,
                    correct_answer_text, is_current, ever_published, max_word_count, min_word_count, pool, prompt_text,
                    pte_task_type, reference_answer_text, rejection_reason, revision_group_public_id, revision_number,
                    status, supersedes_public_id, task_type_key, task_type_section, tenant_id, version, cefr_level,
                    content_hash, generation_item_index, generation_job_public_id, generation_meta,
                    answer_hint_en, answer_hint_vi)
            SELECT now(), now(), false, gen_random_uuid(), s.audio_prompt_ref, s.image_prompt_ref,
                    s.author_user_public_id, true, false, true,
                    s.correct_answer_text, true, true, s.max_word_count, s.min_word_count, 'PRACTICE', s.prompt_text,
                    s.pte_task_type, s.reference_answer_text, NULL, gen_random_uuid(), 1,
                    'APPROVED', NULL, s.task_type_key, s.task_type_section, NULL, 0, s.cefr_level,
                    s.content_hash, NULL, NULL, s.generation_meta,
                    s.answer_hint_en, s.answer_hint_vi
            FROM public.questions s WHERE s.id = source_id
            RETURNING id INTO new_id;

            INSERT INTO public.question_options (question_id, blank_index, correct, correct_gap_index, order_index,
                    public_id, text)
            SELECT new_id, o.blank_index, o.correct, o.correct_gap_index, o.order_index, gen_random_uuid(), o.text
            FROM public.question_options o WHERE o.question_id = source_id;
        END LOOP;
    END LOOP;
END
$$
@@
