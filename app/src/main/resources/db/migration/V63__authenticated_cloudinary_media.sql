ALTER TABLE media_objects
    ADD COLUMN cloudinary_delivery_type VARCHAR(24) NOT NULL DEFAULT 'UPLOAD';
