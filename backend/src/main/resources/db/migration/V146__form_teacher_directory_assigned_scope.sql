-- Gate 14: a form teacher's student directory must follow the same
-- assigned-class boundary as the academic roster.  The existing
-- TITULAIRE_CLASSES rule remains authoritative for class-result and
-- attendance actions; this additive rule only permits directory read-back
-- for an actively assigned class.  It does not grant enrollment-history
-- access or broaden any ordinary role.

INSERT INTO permission_role_template_rule
    (template_code, action_code, effect, scope_mode, is_permanent, reason, display_order)
VALUES
    ('form_teacher', 'STUDENT_DIRECTORY_VIEW', 'ALLOW', 'ASSIGNED_CLASSES', true,
     'Assigned-class student directory read', 12)
ON CONFLICT DO NOTHING;

INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode, is_permanent, reason)
SELECT s.id, 'form_teacher', 'STUDENT_DIRECTORY_VIEW', 'ALLOW', 'ASSIGNED_CLASSES', true,
       'Assigned-class student directory read'
  FROM school s
ON CONFLICT DO NOTHING;
