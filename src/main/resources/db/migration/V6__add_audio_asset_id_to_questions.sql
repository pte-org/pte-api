-- Listening/Speaking authoring: single audio attachment per question.
ALTER TABLE questions ADD COLUMN IF NOT EXISTS audio_asset_id UUID;
