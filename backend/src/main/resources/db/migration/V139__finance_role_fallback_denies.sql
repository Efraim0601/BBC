-- Close the legacy module-bridge fallback for the reviewed Finance personas.
--
-- V137 intentionally keeps the finance module grant so the remaining legacy
-- Finance controllers can authenticate the three personas.  PermissionService
-- falls back to that module grant when an action row is absent, however, which
-- would let a collector invoke an unlisted finance command.  Materialize the
-- reviewed V2 allow-list as explicit legacy denies for every other Finance/HR
-- action.  No ordinary role is changed by this migration.

-- Keep employee self-service available to the finance personas that may have
-- an employee account.  Ownership is still enforced by PayrollService and the
-- V2 SELF rule; this row only satisfies the legacy controller compatibility
-- guard used by the three self-payslip routes.
WITH self_roles(role_code) AS (VALUES
    ('accountant'),
    ('econome'),
    ('finance_collector')
)
INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode, is_permanent, reason)
SELECT s.id, r.role_code, 'PAYSLIP_VIEW_SELF', 'ALLOW', 'SELF', true,
       'V139 reviewed finance-persona employee self-service'
  FROM school s
 CROSS JOIN self_roles r
 WHERE EXISTS (SELECT 1 FROM role WHERE code=r.role_code)
ON CONFLICT DO NOTHING;

INSERT INTO permission_action_grant(school_id, role_code, action_code, allowed)
SELECT s.id, r.role_code, 'PAYSLIP_VIEW_SELF', true
  FROM school s
 CROSS JOIN (VALUES ('accountant'),('econome'),('finance_collector')) AS r(role_code)
 WHERE EXISTS (SELECT 1 FROM role WHERE code=r.role_code)
ON CONFLICT (school_id,role_code,action_code)
DO UPDATE SET allowed=EXCLUDED.allowed;

-- Every active Finance/HR action is denied unless the V2 role policy contains
-- an explicit permanent ALLOW for that persona.  User-specific V2 exceptions
-- (for example a fixture-only payroll approval) remain independent and are
-- evaluated by AuthorizationPolicyService on controllers using @policy.
WITH finance_roles(role_code) AS (VALUES
    ('accountant'),
    ('econome'),
    ('finance_collector')
),
allowed_actions AS (
    SELECT DISTINCT pra.school_id, pra.role_code, pra.action_code
      FROM permission_role_action pra
     WHERE pra.role_code IN ('accountant','econome','finance_collector')
       AND pra.effect='ALLOW'
       AND pra.effective_from IS NULL
       AND pra.effective_to IS NULL
)
INSERT INTO permission_action_grant(school_id, role_code, action_code, allowed)
SELECT s.id, r.role_code, pa.code, false
  FROM school s
 CROSS JOIN finance_roles r
 JOIN permission_action pa
   ON pa.module IN ('finance','hr')
  AND pa.active=true
 LEFT JOIN allowed_actions a
   ON a.school_id=s.id
  AND a.role_code=r.role_code
  AND a.action_code=pa.code
 WHERE a.action_code IS NULL
ON CONFLICT (school_id,role_code,action_code)
DO UPDATE SET allowed=EXCLUDED.allowed;
