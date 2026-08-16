-- V145: complete the fresh-school bootstrap administrator's student setup
-- surface. This is a user exception for the one emergency policy
-- administrator, not a grant to the ordinary principal role or template.
WITH bootstrap_admin AS (
    SELECT DISTINCT school_id, user_id
      FROM permission_user_action
     WHERE action_code = 'PERMISSION_MANAGE'
       AND effect = 'ALLOW'
       AND scope_mode = 'SCHOOL_ALL'
       AND reason = 'Initial emergency policy administrator; review and replace during access-control setup'
), student_setup_actions(action_code) AS (
    VALUES ('STUDENT_PROFILE_CREATE'), ('STUDENT_IMPORT')
)
INSERT INTO permission_user_action
    (school_id, user_id, action_code, effect, scope_mode, is_permanent, reason)
SELECT b.school_id, b.user_id, a.action_code, 'ALLOW', 'SCHOOL_ALL', true,
       'Fresh-school bootstrap student setup authority; replace during access-control setup'
  FROM bootstrap_admin b
 CROSS JOIN student_setup_actions a
 JOIN permission_action pa ON pa.code = a.action_code AND pa.active = true
ON CONFLICT DO NOTHING;
