-- Settings surfaces use precise catalogue actions so a calendar, role,
-- discipline catalogue, mail configuration, or school profile grant is not
-- inferred from the legacy settings module bit.
INSERT INTO permission_action
    (code,module,group_code,label_fr,label_en,description_fr,description_en,
     risk_level,scope_type,required_level,default_read_action,display_order)
VALUES
    ('DISCIPLINE_CATALOG_VIEW','settings','Settings',
     'Catalogue disciplinaire — consultation','Discipline catalogue — view',
     'Consulter les listes disciplinaires de l’établissement.',
     'View the school discipline catalogues.',
     'LOW','SCHOOL','read',true,412),
    ('MAIL_CONFIG_VIEW','settings','Settings',
     'Messagerie — consultation','Mail configuration — view',
     'Consulter la configuration non secrète de la messagerie scolaire.',
     'View non-secret school mail configuration.',
     'MEDIUM','SCHOOL','read',false,418)
ON CONFLICT (code) DO UPDATE SET
    module=EXCLUDED.module, group_code=EXCLUDED.group_code,
    label_fr=EXCLUDED.label_fr, label_en=EXCLUDED.label_en,
    description_fr=EXCLUDED.description_fr, description_en=EXCLUDED.description_en,
    risk_level=EXCLUDED.risk_level, scope_type=EXCLUDED.scope_type,
    required_level=EXCLUDED.required_level,
    default_read_action=EXCLUDED.default_read_action,
    display_order=EXCLUDED.display_order, active=true, updated_at=now();

INSERT INTO permission_role_template_rule
    (template_code,action_code,effect,scope_mode,is_permanent,reason,display_order)
VALUES
    ('principal_oversight','DISCIPLINE_CATALOG_VIEW','ALLOW','SCHOOL_ALL',true,
     'Direction — catalogue disciplinaire',18),
    ('principal_oversight','MAIL_CONFIG_VIEW','ALLOW','SCHOOL_ALL',true,
     'Direction — visibilité de la messagerie',19),
    ('principal_oversight','CALENDAR_VIEW','ALLOW','SCHOOL_ALL',true,
     'Direction — calendrier scolaire',20),
    ('principal_oversight','ROLE_VIEW','ALLOW','SCHOOL_ALL',true,
     'Direction — catalogue des rôles',21),
    ('principal_oversight','PERMISSION_VIEW','ALLOW','SCHOOL_ALL',true,
     'Direction — lecture des droits',22)
ON CONFLICT DO NOTHING;

-- Existing schools retain the old settings/discipline read authority while
-- the rollout remains visible and reviewable.
INSERT INTO permission_role_action
    (school_id,role_code,action_code,effect,scope_mode,is_permanent,reason)
SELECT s.id,r.code,'DISCIPLINE_CATALOG_VIEW','ALLOW','SCHOOL_ALL',true,
       'Permission Policy V2 discipline catalogue compatibility backfill'
  FROM school s CROSS JOIN role r
  LEFT JOIN permission_grant settings_grant
    ON settings_grant.school_id=s.id AND settings_grant.role_code=r.code
   AND settings_grant.module='settings'
  LEFT JOIN permission_grant discipline_grant
    ON discipline_grant.school_id=s.id AND discipline_grant.role_code=r.code
   AND discipline_grant.module='discipline'
 WHERE lower(coalesce(settings_grant.level,'none')) IN ('read','write')
    OR lower(coalesce(discipline_grant.level,'none')) IN ('read','write')
ON CONFLICT DO NOTHING;

INSERT INTO permission_role_action
    (school_id,role_code,action_code,effect,scope_mode,is_permanent,reason)
SELECT s.id,r.code,'MAIL_CONFIG_VIEW','ALLOW','SCHOOL_ALL',true,
       'Permission Policy V2 mail configuration compatibility backfill'
  FROM school s CROSS JOIN role r
  JOIN permission_grant pg
    ON pg.school_id=s.id AND pg.role_code=r.code AND pg.module='settings'
 WHERE lower(pg.level) IN ('read','write')
ON CONFLICT DO NOTHING;
