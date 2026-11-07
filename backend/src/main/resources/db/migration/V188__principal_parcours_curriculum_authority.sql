-- A Director manages the teaching configuration of classes in the parcours
-- assigned to them.  The global catalogue can be extended or corrected within
-- that parcours, but subjects cannot be deleted and school-wide catalogue
-- operations remain reserved for the administrator.

INSERT INTO permission_action
    (code,module,group_code,label_fr,label_en,description_fr,description_en,
     risk_level,scope_type,required_level,default_read_action,display_order)
VALUES
    ('SUBJECT_CATALOG_WRITE','settings','Settings',
     'Matières — créer et modifier','Subjects — create and edit',
     'Créer ou modifier une matière dans le parcours actif, sans supprimer le catalogue scolaire.',
     'Create or edit a subject in the active parcours without deleting the school catalogue.',
     'HIGH','SCHOOL','write',false,406)
ON CONFLICT (code) DO UPDATE SET
    module=EXCLUDED.module, group_code=EXCLUDED.group_code,
    label_fr=EXCLUDED.label_fr, label_en=EXCLUDED.label_en,
    description_fr=EXCLUDED.description_fr, description_en=EXCLUDED.description_en,
    risk_level=EXCLUDED.risk_level, scope_type=EXCLUDED.scope_type,
    required_level=EXCLUDED.required_level, default_read_action=EXCLUDED.default_read_action,
    display_order=EXCLUDED.display_order, active=true, updated_at=now();

-- Replace only the undated role defaults.  Historical, dated delegations and
-- user-specific exceptions remain untouched and auditable.
DELETE FROM permission_role_action
 WHERE role_code IN ('principal', 'principal_legacy_compat')
   AND action_code IN ('SUBJECT_CATALOG_WRITE', 'SUBJECT_MANAGE',
                       'CURRICULUM_MANAGE', 'CURRICULUM_CLASS_MANAGE')
   AND effective_from IS NULL AND effective_to IS NULL;

WITH director_actions(action_code, effect, scope_mode, reason) AS (VALUES
    ('SUBJECT_CATALOG_WRITE', 'ALLOW',   'PARCOURS_ALLOWED',
     'Director manages subject catalogue entries in the assigned parcours'),
    ('CURRICULUM_MANAGE',     'ALLOW',   'PARCOURS_ALLOWED',
     'Director manages class subjects and report-card rules in the assigned parcours'),
    ('CURRICULUM_CLASS_MANAGE','ALLOW',  'PARCOURS_ALLOWED',
     'Director manages class coefficients in the assigned parcours'),
    ('SUBJECT_MANAGE',        'INHERIT', 'NONE',
     'Deleting a school subject remains administrator-only')
)
INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode, is_permanent, reason)
SELECT school.id, role.code, actions.action_code, actions.effect, actions.scope_mode,
       true, actions.reason
  FROM school
 CROSS JOIN role
 CROSS JOIN director_actions actions
 WHERE role.code IN ('principal', 'principal_legacy_compat');

-- New ordinary principal accounts inherit the same constrained authority.
DELETE FROM permission_role_template_rule
 WHERE template_code='principal_oversight'
   AND action_code IN ('SUBJECT_CATALOG_WRITE', 'SUBJECT_MANAGE',
                       'CURRICULUM_MANAGE', 'CURRICULUM_CLASS_MANAGE');

WITH director_template(action_code, effect, scope_mode, reason, display_order) AS (VALUES
    ('SUBJECT_CATALOG_WRITE', 'ALLOW', 'PARCOURS_ALLOWED',
     'Direction — matières du parcours actif', 23),
    ('CURRICULUM_MANAGE', 'ALLOW', 'PARCOURS_ALLOWED',
     'Direction — matières et règles de bulletin des classes du parcours actif', 24),
    ('CURRICULUM_CLASS_MANAGE', 'ALLOW', 'PARCOURS_ALLOWED',
     'Direction — coefficients des classes du parcours actif', 25)
)
INSERT INTO permission_role_template_rule
    (template_code, action_code, effect, scope_mode, is_permanent, reason, display_order)
SELECT 'principal_oversight', action_code, effect, scope_mode, true, reason, display_order
  FROM director_template;

-- Keep the legacy Access-Control summary in sync with the policy decision.
INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT school.id, role.code, actions.action_code, actions.allowed
  FROM school
 CROSS JOIN role
 CROSS JOIN (VALUES
    ('SUBJECT_CATALOG_WRITE', true),
    ('CURRICULUM_MANAGE', true),
    ('CURRICULUM_CLASS_MANAGE', true),
    ('SUBJECT_MANAGE', false)
 ) AS actions(action_code, allowed)
 WHERE role.code IN ('principal', 'principal_legacy_compat')
ON CONFLICT (school_id, role_code, action_code)
DO UPDATE SET allowed=EXCLUDED.allowed;
