-- BAY-50 forward-only permission grant for controlled payroll void/reversal.
INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT s.id, r.code, 'PAYROLL_VOID',
       CASE WHEN r.code IN ('principal','accountant') THEN true ELSE false END
FROM school s
JOIN role r ON r.code IN ('principal','accountant','hr','payroll')
ON CONFLICT (school_id, role_code, action_code)
DO UPDATE SET allowed = EXCLUDED.allowed;
