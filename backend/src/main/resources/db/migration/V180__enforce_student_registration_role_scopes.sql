-- Student registration is a role-level responsibility in every deployed
-- environment.  Principals remain limited by their assigned parcours, while
-- accountants work across the whole school register.  Replace any timeless
-- rule for this one action so an older explicit DENY cannot win over the
-- intended role policy.  Dated delegations and user overrides are untouched.

DELETE FROM permission_role_action
 WHERE action_code = 'STUDENT_PROFILE_CREATE'
   AND role_code IN ('principal', 'principal_legacy_compat', 'accountant')
   AND effective_from IS NULL
   AND effective_to IS NULL;

INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode,
     is_permanent, reason)
SELECT s.id, r.code, 'STUDENT_PROFILE_CREATE', 'ALLOW', x.scope_mode,
       true, x.reason
  FROM school s
 CROSS JOIN (VALUES
    ('principal',               'PARCOURS_ALLOWED', 'Principal student registration in assigned parcours'),
    ('principal_legacy_compat', 'PARCOURS_ALLOWED', 'Principal compatibility student registration in assigned parcours'),
    ('accountant',              'SCHOOL_ALL',       'Accountant school-wide individual student registration')
 ) AS x(role_code, scope_mode, reason)
  JOIN role r ON r.code = x.role_code;

-- Keep the legacy action map aligned for code paths that still consult it.
-- The V2 role rules above remain authoritative for Principal parcours scope.
INSERT INTO permission_action_grant
    (school_id, role_code, action_code, allowed)
SELECT s.id, r.code, 'STUDENT_PROFILE_CREATE', true
  FROM school s
 CROSS JOIN (VALUES ('principal'), ('accountant')) AS x(role_code)
  JOIN role r ON r.code = x.role_code
ON CONFLICT (school_id, role_code, action_code)
DO UPDATE SET allowed = EXCLUDED.allowed;
