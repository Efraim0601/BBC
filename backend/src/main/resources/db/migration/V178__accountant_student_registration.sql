-- Accountants maintain the school-wide student register as part of enrollment
-- and fee-account setup.  Keep the authority intentionally narrow: they may
-- create a student and the family links required by the atomic registration
-- wizard, but they do not receive student import, profile editing, deletion,
-- enrollment administration, or academic-structure management.

WITH authorities(action_code, scope_mode) AS (VALUES
    ('STUDENT_PROFILE_CREATE', 'SCHOOL_ALL'),
    ('GUARDIAN_LINK_MANAGE',   'SCHOOL_ALL')
), updated AS (
    UPDATE permission_role_action p
       SET effect = 'ALLOW',
           scope_mode = a.scope_mode,
           scope_payload = NULL,
           is_permanent = true,
           reason = 'Accountant student registration authority',
           version = p.version + 1,
           updated_at = now()
      FROM authorities a
     WHERE p.role_code = 'accountant'
       AND p.action_code = a.action_code
       AND p.effective_from IS NULL
       AND p.effective_to IS NULL
    RETURNING p.school_id, p.action_code
)
INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode,
     is_permanent, reason)
SELECT s.id, 'accountant', a.action_code, 'ALLOW', a.scope_mode,
       true, 'Accountant student registration authority'
  FROM school s
 CROSS JOIN authorities a
 WHERE NOT EXISTS (
       SELECT 1
         FROM permission_role_action p
        WHERE p.school_id = s.id
          AND p.role_code = 'accountant'
          AND p.action_code = a.action_code
          AND p.effective_from IS NULL
          AND p.effective_to IS NULL
 )
ON CONFLICT DO NOTHING;

INSERT INTO permission_action_grant
    (school_id, role_code, action_code, allowed)
SELECT s.id, 'accountant', a.action_code, true
  FROM school s
 CROSS JOIN (VALUES
    ('STUDENT_PROFILE_CREATE'),
    ('GUARDIAN_LINK_MANAGE')
 ) AS a(action_code)
ON CONFLICT (school_id, role_code, action_code)
DO UPDATE SET allowed = EXCLUDED.allowed;
