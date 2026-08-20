-- V149: Direction may read the published master timetable, without timetable
-- editing, resource, substitution, export, or version-lifecycle authority.
INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT s.id, 'principal', 'TIMETABLE_MASTER_VIEW', true
  FROM school s
ON CONFLICT (school_id, role_code, action_code)
DO UPDATE SET allowed = EXCLUDED.allowed;

INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode, is_permanent, reason)
SELECT s.id, x.role_code, 'TIMETABLE_MASTER_VIEW', 'ALLOW', 'SCHOOL_ALL', true,
       'Direction oversight needs published master timetable read only'
  FROM school s
 CROSS JOIN (VALUES ('principal'), ('principal_legacy_compat')) x(role_code)
ON CONFLICT DO NOTHING;

INSERT INTO permission_role_template_rule
    (template_code, action_code, effect, scope_mode, is_permanent, reason, display_order)
VALUES
 ('principal_oversight', 'TIMETABLE_MASTER_VIEW', 'ALLOW', 'SCHOOL_ALL', true,
  'Direction published master timetable read only', 19)
ON CONFLICT DO NOTHING;
