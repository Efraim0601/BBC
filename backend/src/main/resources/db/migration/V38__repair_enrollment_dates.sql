-- V37 used the student creation date for migrated enrollments, but legacy students
-- created after the configured session end could therefore fall outside the session.
-- Clamp only migration-created records; manually entered historical dates are left intact.
UPDATE student_enrollment e
SET enrolled_on = LEAST(GREATEST(e.enrolled_on, s.start_date), s.end_date),
    updated_at = now()
FROM academic_session s
WHERE e.academic_session_id = s.id
  AND e.school_id = s.school_id
  AND e.source = 'MIGRATION'
  AND (e.enrolled_on < s.start_date OR e.enrolled_on > s.end_date);
