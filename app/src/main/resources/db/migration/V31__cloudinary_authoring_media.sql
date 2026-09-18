ALTER TABLE media_objects ALTER COLUMN tenant_id DROP NOT NULL;
ALTER TABLE media_objects ADD COLUMN IF NOT EXISTS cloudinary_public_id VARCHAR(512);
ALTER TABLE media_objects ADD COLUMN IF NOT EXISTS cloudinary_resource_type VARCHAR(32);
ALTER TABLE media_objects ADD COLUMN IF NOT EXISTS secure_url TEXT;
ALTER TABLE media_objects ADD COLUMN IF NOT EXISTS asset_id VARCHAR(255);
ALTER TABLE media_objects ADD COLUMN IF NOT EXISTS size_bytes BIGINT;

CREATE INDEX IF NOT EXISTS idx_media_objects_cloudinary_public_id
    ON media_objects (cloudinary_public_id);
