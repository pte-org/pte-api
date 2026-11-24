-- The plain-text copy of an email is kept in history and the richer application emails exceed 255 characters,
-- which made saving the log fail and the mail never get sent.
ALTER TABLE notification_logs ALTER COLUMN body TYPE text;
