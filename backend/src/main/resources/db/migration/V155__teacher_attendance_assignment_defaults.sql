-- The generic Teacher role is used across all school levels. Its attendance
-- scope therefore needs to follow the level-specific assignment authority:
-- active HOMEROOM assignments in Primary/Maternelle, and assigned published
-- timetable occurrences in Secondary. The resolver enforces that boundary at
-- request time; this role rule only enables that combined assignment scope.

INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode, is_permanent, reason)
SELECT s.id, 'teacher', action.code, 'ALLOW', 'ASSIGNED_CLASSES', true,
       'Teacher attendance access derived from level-specific active assignments'
  FROM school s
 CROSS JOIN (VALUES
    ('ATTENDANCE_ROSTER_VIEW'),
    ('ATTENDANCE_MARK'),
    ('ATTENDANCE_FINALIZE')
 ) AS action(code)
ON CONFLICT DO NOTHING;
