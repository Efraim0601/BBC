-- Endpoint-specific school-scoped actions used before a student/relationship
-- has been selected.  Resource-scoped guardian link actions remain separate.
INSERT INTO permission_action
    (code,module,group_code,label_fr,label_en,description_fr,description_en,
     risk_level,scope_type,required_level,default_read_action,display_order)
VALUES
    ('GUARDIAN_DIRECTORY_SEARCH','students','Students',
     'Rechercher un responsable','Search guardian directory',
     'Recherche minimisée nécessaire pour préparer un lien familial.',
     'Minimized lookup needed to prepare a family link.',
     'MEDIUM','SCHOOL','read',false,28),
    ('GUARDIAN_DIRECTORY_MANAGE','students','Students',
     'Administrer le répertoire des responsables','Manage guardian directory',
     'Fusion, invitation et cycle de vie des comptes responsables.',
     'Merge, invitation and lifecycle operations for guardian accounts.',
     'CRITICAL','SCHOOL','write',false,29)
ON CONFLICT (code) DO UPDATE SET
    module=EXCLUDED.module, group_code=EXCLUDED.group_code,
    label_fr=EXCLUDED.label_fr, label_en=EXCLUDED.label_en,
    description_fr=EXCLUDED.description_fr, description_en=EXCLUDED.description_en,
    risk_level=EXCLUDED.risk_level, scope_type=EXCLUDED.scope_type,
    required_level=EXCLUDED.required_level,
    default_read_action=EXCLUDED.default_read_action,
    display_order=EXCLUDED.display_order, active=true, updated_at=now();

-- The durable principal template is also used by new-school bootstrapping.
INSERT INTO permission_role_template_rule
    (template_code,action_code,effect,scope_mode,is_permanent,reason,display_order)
VALUES
    ('principal_oversight','GUARDIAN_DIRECTORY_SEARCH','ALLOW','SCHOOL_ALL',true,
     'Principal directory lookup',28),
    ('principal_oversight','GUARDIAN_DIRECTORY_MANAGE','ALLOW','SCHOOL_ALL',true,
     'Principal guardian directory administration',29)
ON CONFLICT DO NOTHING;

-- Existing schools receive the least surprising equivalent of their old
-- staff/settings grant.  The compatibility rollout remains visible and
-- preserves the pre-migration decision until adoption.
INSERT INTO permission_role_action
    (school_id,role_code,action_code,effect,scope_mode,is_permanent,reason)
SELECT s.id,r.code,'GUARDIAN_DIRECTORY_SEARCH','ALLOW','SCHOOL_ALL',true,
       'Permission Policy V2 guardian directory endpoint'
  FROM school s CROSS JOIN role r
  LEFT JOIN permission_grant pg
    ON pg.school_id=s.id AND pg.role_code=r.code
   AND pg.module='students'
 WHERE lower(coalesce(pg.level,'none')) IN ('read','write')
    OR r.code IN ('principal','administrator','admin','school_admin')
ON CONFLICT DO NOTHING;

INSERT INTO permission_role_action
    (school_id,role_code,action_code,effect,scope_mode,is_permanent,reason)
SELECT s.id,r.code,'GUARDIAN_DIRECTORY_MANAGE','ALLOW','SCHOOL_ALL',true,
       'Permission Policy V2 guardian directory administration endpoint'
  FROM school s CROSS JOIN role r
  LEFT JOIN permission_grant pg
    ON pg.school_id=s.id AND pg.role_code=r.code
   AND pg.module IN ('students','settings')
 WHERE lower(coalesce(pg.level,'none'))='write'
    OR r.code IN ('principal','administrator','admin','school_admin')
ON CONFLICT DO NOTHING;
