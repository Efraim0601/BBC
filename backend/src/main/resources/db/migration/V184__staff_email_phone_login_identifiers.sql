-- New staff may use their complete email address as the login identifier.
-- Preserve existing accounts and allow the same identifier in audit records.
ALTER TABLE app_user ALTER COLUMN username TYPE VARCHAR(254);
ALTER TABLE audit_event ALTER COLUMN actor_username TYPE VARCHAR(254);
ALTER TABLE attendance_session_event ALTER COLUMN actor_username TYPE VARCHAR(254);
