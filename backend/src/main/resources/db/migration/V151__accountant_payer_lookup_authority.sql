-- V151: accountants need read-only payer lookup for the legacy payment form.
-- This does not grant student editing, transfer, or academic access.

INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode, is_permanent, reason)
SELECT s.id,
       'accountant',
       'STUDENT_DIRECTORY_VIEW',
       'ALLOW',
       'SCHOOL_ALL',
       true,
       'Accountant payment workflow requires minimal payer lookup'
  FROM school s
 WHERE NOT EXISTS (
       SELECT 1
         FROM permission_role_action p
        WHERE p.school_id = s.id
          AND p.role_code = 'accountant'
          AND p.action_code = 'STUDENT_DIRECTORY_VIEW'
          AND p.effective_from IS NULL
          AND p.effective_to IS NULL
 )
ON CONFLICT DO NOTHING;

INSERT INTO permission_role_template_rule
    (template_code, action_code, effect, scope_mode, is_permanent, reason, display_order)
VALUES
    ('accountant',
     'STUDENT_DIRECTORY_VIEW',
     'ALLOW',
     'SCHOOL_ALL',
     true,
     'Accountant payment workflow requires minimal payer lookup',
     13)
ON CONFLICT DO NOTHING;
