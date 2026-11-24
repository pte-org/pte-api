-- The avatar a student picks in practice. Nullable with no default and no backfill: existing profiles stay exactly
-- as they are, and a student who never picks one keeps the app's default avatar.
ALTER TABLE practice_student_profiles
    ADD COLUMN IF NOT EXISTS gender VARCHAR(16);

ALTER TABLE practice_student_profiles
    DROP CONSTRAINT IF EXISTS practice_student_profiles_gender_check;

ALTER TABLE practice_student_profiles
    ADD CONSTRAINT practice_student_profiles_gender_check
        CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE'));
