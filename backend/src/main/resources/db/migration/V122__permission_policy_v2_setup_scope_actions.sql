-- Setup has both school-wide catalog operations and class-bound curriculum /
-- class-teacher operations.  The latter are not represented by a settings
-- module boolean and must carry a server-resolved class context.
INSERT INTO permission_action
    (code,module,group_code,label_fr,label_en,description_fr,description_en,
     risk_level,scope_type,required_level,default_read_action,display_order)
VALUES
    ('CURRICULUM_CLASS_MANAGE','settings','Settings',
     'Curriculum de classe — gérer','Class curriculum — manage',
     'Modifier les groupes et paramètres du curriculum d’une classe ciblée.',
     'Manage groups and settings for a targeted class curriculum.',
     'HIGH','CLASS','write',false,420),
    ('CURRICULUM_CATALOG_MANAGE','settings','Settings',
     'Catalogue du curriculum — gérer','Curriculum catalogue — manage',
     'Gérer les groupes et le catalogue commun des sessions académiques.',
     'Manage shared subject groups and session curriculum catalogue.',
     'HIGH','SCHOOL','write',false,422),
    ('TEACHING_CLASS_ASSIGNMENT_MANAGE','settings','Settings',
     'Affectations de classe — gérer','Class teaching assignments — manage',
     'Gérer les enseignants rattachés à une classe ciblée.',
     'Manage teachers assigned to a targeted class.',
     'CRITICAL','CLASS','write',false,421)
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
    ('principal_oversight','CURRICULUM_CLASS_MANAGE','DENY','SCHOOL_ALL',true,
     'Direction — gestion du curriculum explicitement accordée seulement par délégation',23),
    ('principal_oversight','CURRICULUM_CATALOG_MANAGE','DENY','SCHOOL_ALL',true,
     'Direction — catalogue du curriculum explicitement accordé seulement par délégation',25),
    ('principal_oversight','TEACHING_CLASS_ASSIGNMENT_MANAGE','DENY','SCHOOL_ALL',true,
     'Direction — affectations de classe explicitement accordées seulement par délégation',24)
ON CONFLICT DO NOTHING;

INSERT INTO permission_role_action
    (school_id,role_code,action_code,effect,scope_mode,is_permanent,reason)
SELECT s.id,r.code,'CURRICULUM_CATALOG_MANAGE','ALLOW','SCHOOL_ALL',true,
       'Permission Policy V2 curriculum catalogue compatibility backfill'
  FROM school s CROSS JOIN role r
  JOIN permission_grant pg ON pg.school_id=s.id AND pg.role_code=r.code
                          AND pg.module='settings' AND lower(pg.level)='write'
ON CONFLICT DO NOTHING;

-- Existing settings writers retain their current effective authority in the
-- visible compatibility window; the central domain layer still checks tenant
-- and class context when these actions are used.
INSERT INTO permission_role_action
    (school_id,role_code,action_code,effect,scope_mode,is_permanent,reason)
SELECT s.id,r.code,'CURRICULUM_CLASS_MANAGE','ALLOW','SCHOOL_ALL',true,
       'Permission Policy V2 curriculum class compatibility backfill'
  FROM school s CROSS JOIN role r
  JOIN permission_grant pg ON pg.school_id=s.id AND pg.role_code=r.code
                          AND pg.module='settings' AND lower(pg.level)='write'
ON CONFLICT DO NOTHING;

INSERT INTO permission_role_action
    (school_id,role_code,action_code,effect,scope_mode,is_permanent,reason)
SELECT s.id,r.code,'TEACHING_CLASS_ASSIGNMENT_MANAGE','ALLOW','SCHOOL_ALL',true,
       'Permission Policy V2 class-teacher assignment compatibility backfill'
  FROM school s CROSS JOIN role r
  JOIN permission_grant pg ON pg.school_id=s.id AND pg.role_code=r.code
                          AND pg.module='settings' AND lower(pg.level)='write'
ON CONFLICT DO NOTHING;
