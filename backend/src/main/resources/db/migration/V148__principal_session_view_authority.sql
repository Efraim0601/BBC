-- V148: align Direction's read-only academic oversight with the shared
-- current-session lookup used by the academic workspace. This is read-only;
-- it does not grant session creation, mutation, or academic setup authority.
INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT s.id, 'principal', 'SESSION_VIEW', true
  FROM school s
ON CONFLICT (school_id, role_code, action_code)
DO UPDATE SET allowed = EXCLUDED.allowed;

INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode, is_permanent, reason)
SELECT s.id, x.role_code, 'SESSION_VIEW', 'ALLOW', 'SCHOOL_ALL', true,
       'Direction academic oversight needs current-session read only'
  FROM school s
 CROSS JOIN (VALUES ('principal'), ('principal_legacy_compat')) x(role_code)
ON CONFLICT DO NOTHING;

INSERT INTO permission_role_template_rule
    (template_code, action_code, effect, scope_mode, is_permanent, reason, display_order)
VALUES
 ('principal_oversight', 'SESSION_VIEW', 'ALLOW', 'SCHOOL_ALL', true,
  'Direction current-session read only', 18)
ON CONFLICT DO NOTHING;
