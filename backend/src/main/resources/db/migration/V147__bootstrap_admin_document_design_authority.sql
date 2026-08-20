-- V147: let the emergency bootstrap administrator publish the initial
-- document-branding snapshot. This is a user exception for first-school setup,
-- not an ordinary principal/template grant.
WITH bootstrap_admin AS (
    SELECT DISTINCT school_id, user_id
      FROM permission_user_action
     WHERE action_code = 'PERMISSION_MANAGE'
       AND effect = 'ALLOW'
       AND scope_mode = 'SCHOOL_ALL'
       AND reason = 'Initial emergency policy administrator; review and replace during access-control setup'
)
INSERT INTO permission_user_action
    (school_id, user_id, action_code, effect, scope_mode, is_permanent, reason)
SELECT b.school_id, b.user_id, 'DOCUMENT_DESIGN_PUBLISH', 'ALLOW', 'SCHOOL_ALL', true,
       'Fresh-school bootstrap document design authority; replace during access-control setup'
  FROM bootstrap_admin b
  JOIN permission_action pa ON pa.code = 'DOCUMENT_DESIGN_PUBLISH' AND pa.active = true
ON CONFLICT DO NOTHING;
