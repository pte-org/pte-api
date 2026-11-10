-- Every question belongs to the platform bank, so `visibility` only ever held 'SHARED'. Its index
-- and check constraint go with the column.
ALTER TABLE questions DROP COLUMN visibility;
