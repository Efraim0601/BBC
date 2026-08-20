-- Repair the one fresh-school administrator exception introduced by
-- ProductionBootstrap. V118-V123 run before a fresh school exists, so the
-- bootstrap runner must seed the same policy after creating the tenant. This
-- forward-only migration also repairs a database first started on a candidate
-- where only PERMISSION_MANAGE was seeded.
--
-- The selector is deliberately narrow: it targets only the emergency policy
-- administrator exception created by ProductionBootstrap. Ordinary principal
-- users and the principal_oversight template are not broadened.
WITH bootstrap_admin AS (
    SELECT DISTINCT school_id, user_id
      FROM permission_user_action
     WHERE action_code = 'PERMISSION_MANAGE'
       AND effect = 'ALLOW'
       AND scope_mode = 'SCHOOL_ALL'
       AND reason = 'Initial emergency policy administrator; review and replace during access-control setup'
), setup_actions(action_code) AS (
    VALUES
        ('SESSION_MANAGE'),
        ('CALENDAR_MANAGE'),
        ('SCHOOL_PROFILE_MANAGE'),
        ('CLASS_MANAGE'),
        ('SUBJECT_MANAGE'),
        ('CURRICULUM_MANAGE'),
        ('CURRICULUM_CLASS_MANAGE'),
        ('CURRICULUM_CATALOG_MANAGE'),
        ('TEACHING_ASSIGNMENT_MANAGE'),
        ('TEACHING_CLASS_ASSIGNMENT_MANAGE'),
        ('MAIL_CONFIG_MANAGE'),
        ('DISCIPLINE_CATALOG_MANAGE'),
        ('ROLE_MANAGE')
)
INSERT INTO permission_user_action
    (school_id, user_id, action_code, effect, scope_mode, is_permanent, reason)
SELECT b.school_id, b.user_id, a.action_code, 'ALLOW', 'SCHOOL_ALL', true,
       'Fresh-school bootstrap setup authority; replace during access-control setup'
  FROM bootstrap_admin b
  CROSS JOIN setup_actions a
  JOIN permission_action pa ON pa.code = a.action_code AND pa.active = true
ON CONFLICT DO NOTHING;
