-- Review of a submitted council/conduct input returns the reviewer queue.
-- Direction therefore needs the class-wide read action, while council editing
-- remains a separate bounded capability and is not granted here.
INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT s.id, 'principal', 'ACADEMIC_COUNCIL_INPUT_VIEW', true
  FROM school s
ON CONFLICT (school_id, role_code, action_code)
DO UPDATE SET allowed = EXCLUDED.allowed;

INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode, is_permanent, reason)
SELECT s.id, 'principal', 'ACADEMIC_COUNCIL_INPUT_VIEW', 'ALLOW', 'SCHOOL_ALL', true,
       'Direction read-only access to submitted council inputs'
  FROM school s
ON CONFLICT DO NOTHING;

INSERT INTO permission_role_template_rule
    (template_code, action_code, effect, scope_mode, is_permanent, reason, display_order)
VALUES
 ('principal_oversight','ACADEMIC_COUNCIL_INPUT_VIEW','ALLOW','SCHOOL_ALL',true,
  'Direction reviewer queue read access',34)
ON CONFLICT DO NOTHING;

INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode, is_permanent, reason)
SELECT s.id, 'principal_legacy_compat', 'ACADEMIC_COUNCIL_INPUT_VIEW', 'ALLOW', 'SCHOOL_ALL', true,
       'Direction council input read compatibility authority'
  FROM school s
ON CONFLICT DO NOTHING;
