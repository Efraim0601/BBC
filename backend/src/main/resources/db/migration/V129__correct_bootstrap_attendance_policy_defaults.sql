-- Gate 8: align untouched legacy attendance defaults with the documented
-- level-specific policy. Existing customized policies are preserved.
UPDATE attendance_policy
   SET late_after_minutes = CASE
           WHEN level IN ('maternelle', 'primary') THEN 15
           ELSE 10
       END,
       chronic_absence_percent = CASE
           WHEN level IN ('maternelle', 'primary') THEN 15.00
           ELSE 20.00
       END,
       require_absence_reason = true,
       updated_at = now()
 WHERE late_after_minutes = 0
   AND chronic_absence_percent = 20.00
   AND require_absence_reason = false;
