-- A form teacher may be a secondary subject-responsible teacher without a
-- dated homeroom row. Keep roster visibility class-scoped and let the
-- academic assignment resolver prove the active class/subject assignment.

INSERT INTO permission_role_template_rule
    (template_code, action_code, effect, scope_mode, is_permanent, reason, display_order)
VALUES
    ('form_teacher', 'ACADEMIC_ROSTER_VIEW', 'ALLOW', 'ASSIGNED_CLASSES', true,
     'Secondary assigned-class roster scope', 20)
ON CONFLICT DO NOTHING;

INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode, is_permanent, reason)
SELECT s.id, 'form_teacher', 'ACADEMIC_ROSTER_VIEW', 'ALLOW', 'ASSIGNED_CLASSES', true,
       'Secondary assigned-class roster scope'
FROM school s
ON CONFLICT DO NOTHING;
