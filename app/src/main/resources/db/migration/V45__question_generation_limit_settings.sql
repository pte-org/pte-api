-- The AI question generation limits become platform settings, edited on the AI overview page. The values are the
-- QUESTION_GEN_* defaults used until now. Each row is added only when missing.
INSERT INTO platform_settings (public_id, created_at, updated_at, deleted, setting_key, value, description)
SELECT gen_random_uuid(), now(), now(), false, 'question_generation_max_items_per_job', '10', 'Questions one generation job may ask for'
WHERE NOT EXISTS (SELECT 1 FROM platform_settings WHERE setting_key = 'question_generation_max_items_per_job');

INSERT INTO platform_settings (public_id, created_at, updated_at, deleted, setting_key, value, description)
SELECT gen_random_uuid(), now(), now(), false, 'question_generation_max_items_per_user_per_day', '100', 'Questions one admin may generate per day'
WHERE NOT EXISTS (SELECT 1 FROM platform_settings WHERE setting_key = 'question_generation_max_items_per_user_per_day');

INSERT INTO platform_settings (public_id, created_at, updated_at, deleted, setting_key, value, description)
SELECT gen_random_uuid(), now(), now(), false, 'question_generation_max_items_per_day', '300', 'Questions all admins together may generate per day'
WHERE NOT EXISTS (SELECT 1 FROM platform_settings WHERE setting_key = 'question_generation_max_items_per_day');

INSERT INTO platform_settings (public_id, created_at, updated_at, deleted, setting_key, value, description)
SELECT gen_random_uuid(), now(), now(), false, 'question_generation_max_concurrent_jobs', '3', 'Generation jobs that may run at the same time'
WHERE NOT EXISTS (SELECT 1 FROM platform_settings WHERE setting_key = 'question_generation_max_concurrent_jobs');

INSERT INTO platform_settings (public_id, created_at, updated_at, deleted, setting_key, value, description)
SELECT gen_random_uuid(), now(), now(), false, 'question_generation_max_calls_per_item', '40', 'AI calls one generated question may use (writing, review, media)'
WHERE NOT EXISTS (SELECT 1 FROM platform_settings WHERE setting_key = 'question_generation_max_calls_per_item');
