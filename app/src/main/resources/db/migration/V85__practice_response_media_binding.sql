-- Bind student practice recordings to the owning session item before upload.
-- Rollback policy: retain media rows and disable the practice entry point;
-- do not detach historical recordings from their audit boundary.

ALTER TABLE media_objects
    ADD COLUMN IF NOT EXISTS practice_session_public_id UUID,
    ADD COLUMN IF NOT EXISTS practice_item_public_id UUID,
    ADD COLUMN IF NOT EXISTS purpose VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_media_objects_practice_binding
    ON media_objects (practice_session_public_id, practice_item_public_id, purpose);
