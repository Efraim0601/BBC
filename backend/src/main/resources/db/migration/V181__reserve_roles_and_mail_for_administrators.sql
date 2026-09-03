-- Roles, Access Control, and outgoing e-mail configuration are technical
-- administrator surfaces.  Principals keep operational oversight inside their
-- assigned parcours but must not read these school-wide security settings.

DELETE FROM permission_role_action
 WHERE role_code IN ('principal', 'principal_legacy_compat')
   AND action_code IN ('ROLE_VIEW', 'ROLE_MANAGE', 'PERMISSION_VIEW',
                       'PERMISSION_MANAGE', 'MAIL_CONFIG_VIEW', 'MAIL_CONFIG_MANAGE')
   AND effective_from IS NULL
   AND effective_to IS NULL;

INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode,
     is_permanent, reason)
SELECT s.id, r.code, a.code, 'DENY', 'SCHOOL_ALL', true,
       'Technical administrator settings are not part of Principal oversight'
  FROM school s
  JOIN role r ON r.code IN ('principal', 'principal_legacy_compat')
  JOIN permission_action a
    ON a.code IN ('ROLE_VIEW', 'ROLE_MANAGE', 'PERMISSION_VIEW',
                  'PERMISSION_MANAGE', 'MAIL_CONFIG_VIEW', 'MAIL_CONFIG_MANAGE');

DELETE FROM permission_role_template_rule
 WHERE template_code='principal_oversight'
   AND action_code IN ('ROLE_VIEW', 'ROLE_MANAGE', 'PERMISSION_VIEW',
                       'PERMISSION_MANAGE', 'MAIL_CONFIG_VIEW', 'MAIL_CONFIG_MANAGE');

INSERT INTO permission_role_template_rule
    (template_code, action_code, effect, scope_mode,
     is_permanent, reason, display_order)
SELECT 'principal_oversight', a.code, 'DENY', 'SCHOOL_ALL', true,
       'Reserved for the technical administrator',
       900 + row_number() OVER (ORDER BY a.code)
  FROM permission_action a
 WHERE a.code IN ('ROLE_VIEW', 'ROLE_MANAGE', 'PERMISSION_VIEW',
                  'PERMISSION_MANAGE', 'MAIL_CONFIG_VIEW', 'MAIL_CONFIG_MANAGE');

INSERT INTO permission_action_grant
    (school_id, role_code, action_code, allowed)
SELECT s.id, r.code, a.code, false
  FROM school s
  JOIN role r ON r.code IN ('principal', 'principal_legacy_compat')
  JOIN permission_action a
    ON a.code IN ('ROLE_VIEW', 'ROLE_MANAGE', 'PERMISSION_VIEW',
                  'PERMISSION_MANAGE', 'MAIL_CONFIG_VIEW', 'MAIL_CONFIG_MANAGE')
ON CONFLICT (school_id, role_code, action_code)
DO UPDATE SET allowed=false;
