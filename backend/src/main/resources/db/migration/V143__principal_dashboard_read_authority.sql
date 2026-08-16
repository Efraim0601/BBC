-- Align the Direction/principal oversight profile with the read-only modules
-- already exposed by the legacy dashboard grants.  This does not add setup,
-- mutation, finance collection, or academic editing authority.
INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT s.id, 'principal', x.action_code, true
  FROM school s
 CROSS JOIN (VALUES
    ('STUDENT_DIRECTORY_VIEW'),
    ('STUDENT_PROFILE_VIEW'),
    ('ATTENDANCE_ROSTER_VIEW'),
    ('FINANCE_OVERVIEW_VIEW'),
    ('FINANCE_REPORT_VIEW'),
    ('FINANCE_EXPORT')
 ) x(action_code)
ON CONFLICT (school_id, role_code, action_code)
DO UPDATE SET allowed = EXCLUDED.allowed;

INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode, is_permanent, reason)
SELECT s.id, 'principal', x.action_code, 'ALLOW', 'SCHOOL_ALL', true,
       'Direction dashboard and oversight read alignment'
  FROM school s
 CROSS JOIN (VALUES
    ('STUDENT_DIRECTORY_VIEW'),
    ('STUDENT_PROFILE_VIEW'),
    ('ATTENDANCE_ROSTER_VIEW'),
    ('FINANCE_OVERVIEW_VIEW'),
    ('FINANCE_REPORT_VIEW'),
    ('FINANCE_EXPORT')
 ) x(action_code)
ON CONFLICT DO NOTHING;

INSERT INTO permission_role_template_rule
    (template_code, action_code, effect, scope_mode, is_permanent, reason, display_order)
VALUES
 ('principal_oversight','STUDENT_DIRECTORY_VIEW','ALLOW','SCHOOL_ALL',true,
  'Direction student directory oversight',18),
 ('principal_oversight','STUDENT_PROFILE_VIEW','ALLOW','SCHOOL_ALL',true,
  'Direction student profile oversight',19),
 ('principal_oversight','ATTENDANCE_ROSTER_VIEW','ALLOW','SCHOOL_ALL',true,
  'Direction attendance board oversight',20),
 ('principal_oversight','FINANCE_OVERVIEW_VIEW','ALLOW','SCHOOL_ALL',true,
  'Direction finance overview',21),
 ('principal_oversight','FINANCE_REPORT_VIEW','ALLOW','SCHOOL_ALL',true,
  'Direction finance reporting',22),
 ('principal_oversight','FINANCE_EXPORT','ALLOW','SCHOOL_ALL',true,
  'Direction finance export read',23)
ON CONFLICT DO NOTHING;
