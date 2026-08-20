-- Form teachers can own a daily primary/maternelle roster and can also be
-- titular for a secondary class. Keep both paths resource-scoped: the second
-- path is limited to the teacher's published timetable occurrences.

INSERT INTO permission_role_template_rule
    (template_code, action_code, effect, scope_mode, is_permanent, reason, display_order)
VALUES
    ('form_teacher', 'ACADEMIC_ROSTER_VIEW', 'ALLOW', 'TITULAIRE_CLASSES', true,
     'Titulaire roster scope', 19),
    ('form_teacher', 'ATTENDANCE_ROSTER_VIEW', 'ALLOW', 'TIMETABLE_OCCURRENCES_ASSIGNED', true,
     'Secondary published occurrence attendance', 34),
    ('form_teacher', 'ATTENDANCE_MARK', 'ALLOW', 'TIMETABLE_OCCURRENCES_ASSIGNED', true,
     'Secondary published occurrence attendance', 35),
    ('form_teacher', 'ATTENDANCE_FINALIZE', 'ALLOW', 'TIMETABLE_OCCURRENCES_ASSIGNED', true,
     'Secondary published occurrence attendance', 36),
    ('form_teacher', 'ATTENDANCE_ANALYTICS_VIEW', 'ALLOW', 'TIMETABLE_OCCURRENCES_ASSIGNED', true,
     'Secondary published occurrence analytics', 37)
ON CONFLICT DO NOTHING;

INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode, is_permanent, reason)
SELECT s.id, 'form_teacher', x.action_code, 'ALLOW', x.scope_mode, true, x.reason
FROM school s
CROSS JOIN (VALUES
    ('ACADEMIC_ROSTER_VIEW', 'TITULAIRE_CLASSES', 'Titulaire roster scope'),
    ('ATTENDANCE_ROSTER_VIEW', 'TIMETABLE_OCCURRENCES_ASSIGNED', 'Secondary published occurrence attendance'),
    ('ATTENDANCE_MARK', 'TIMETABLE_OCCURRENCES_ASSIGNED', 'Secondary published occurrence attendance'),
    ('ATTENDANCE_FINALIZE', 'TIMETABLE_OCCURRENCES_ASSIGNED', 'Secondary published occurrence attendance'),
    ('ATTENDANCE_ANALYTICS_VIEW', 'TIMETABLE_OCCURRENCES_ASSIGNED', 'Secondary published occurrence analytics')
) x(action_code, scope_mode, reason)
ON CONFLICT DO NOTHING;
