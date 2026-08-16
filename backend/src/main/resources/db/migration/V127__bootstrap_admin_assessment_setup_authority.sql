-- V127 — assessment-default generation is part of first-school academic setup.
-- Grant it only to the existing fresh-bootstrap exception; do not broaden the
-- ordinary principal role template.
WITH bootstrap_admin AS (
    SELECT school_id, user_id
      FROM permission_user_action
     WHERE action_code = 'PERMISSION_MANAGE'
       AND effect = 'ALLOW'
       AND scope_mode = 'SCHOOL_ALL'
       AND reason = 'Initial emergency policy administrator; review and replace during access-control setup'
)
INSERT INTO permission_user_action
    (id, school_id, user_id, action_code, effect, scope_mode, scope_payload,
    effective_from, effective_to, is_permanent, reason)
SELECT gen_random_uuid(), school_id, user_id, x.action_code, 'ALLOW', 'SCHOOL_ALL', NULL,
       CURRENT_DATE, NULL, TRUE,
       'Fresh-school bootstrap setup authority; replace during access-control setup'
  FROM bootstrap_admin
 CROSS JOIN (VALUES ('ACADEMIC_ASSESSMENT_VIEW'), ('ACADEMIC_ASSESSMENT_MANAGE')) x(action_code)
ON CONFLICT DO NOTHING;
