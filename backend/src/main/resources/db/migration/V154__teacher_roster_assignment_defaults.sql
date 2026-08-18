-- A teacher's ability to read a roster is a baseline role capability; the
-- class boundary itself is resolved from teaching assignments at request
-- time. For Primary and Kindergarten that authority is the active dated
-- HOMEROOM assignment. Secondary remains bound to RESPONSIBLE subjects.
--
-- Keep these ASSIGNED_CLASSES rules separate from timetable permissions so a
-- homeroom teacher does not need a timetable config or published slot before
-- seeing their class and students.

INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode, is_permanent, reason)
SELECT s.id, 'teacher', action.code, 'ALLOW', 'ASSIGNED_CLASSES', true,
       'Teacher roster access derived from active academic assignments'
  FROM school s
 CROSS JOIN (VALUES
    ('ACADEMIC_ROSTER_VIEW'),
    ('STUDENT_DIRECTORY_VIEW'),
    ('STUDENT_PROFILE_VIEW')
 ) AS action(code)
ON CONFLICT DO NOTHING;
