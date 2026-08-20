-- The bootstrap administrator needs the read side of the session screen as
-- well as SESSION_MANAGE.  Keep this as a user exception: adding the action
-- to the ordinary principal profile would broaden setup authority.
WITH bootstrap_admin AS (
    SELECT school_id, user_id
      FROM permission_user_action
     WHERE action_code = 'PERMISSION_MANAGE'
       AND effect = 'ALLOW'
       AND scope_mode = 'SCHOOL_ALL'
       AND reason = 'Initial emergency policy administrator; review and replace during access-control setup'
)
INSERT INTO permission_user_action
    (school_id, user_id, action_code, effect, scope_mode, is_permanent, reason)
SELECT school_id, user_id, 'SESSION_VIEW', 'ALLOW', 'SCHOOL_ALL', true,
       'Fresh-school bootstrap setup authority; replace during access-control setup'
  FROM bootstrap_admin
ON CONFLICT DO NOTHING;
