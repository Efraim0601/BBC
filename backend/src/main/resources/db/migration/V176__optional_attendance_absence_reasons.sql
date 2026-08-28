-- Absence and excused reasons are useful context, but teachers must be able to
-- save an attendance call before a justification is known. Keep the legacy
-- column for API/schema compatibility while making the school-wide rule
-- explicitly optional.
UPDATE attendance_policy
   SET require_absence_reason = false,
       updated_at = now()
 WHERE require_absence_reason = true;

COMMENT ON COLUMN attendance_policy.require_absence_reason IS
    'Legacy compatibility flag. Absence and excused reasons are optional.';
