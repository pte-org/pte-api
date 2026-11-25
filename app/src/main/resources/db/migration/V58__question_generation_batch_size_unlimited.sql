-- A batch is no longer capped by its own size: only the daily limits (and the technical cap of 500 in the
-- total_requested CHECK) bound it, so the setting leaves the settings page and is set to 0, which means unlimited.
UPDATE platform_settings
SET value = '0', updated_at = now()
WHERE setting_key = 'question_generation_max_items_per_batch';

-- The daily limits apply to everyone who generates (academic staff and managers too), not only admins.
UPDATE platform_settings SET description = 'Questions one person may generate per day', updated_at = now()
WHERE setting_key = 'question_generation_max_items_per_user_per_day';
UPDATE platform_settings SET description = 'Questions all users together may generate per day', updated_at = now()
WHERE setting_key = 'question_generation_max_items_per_day';
