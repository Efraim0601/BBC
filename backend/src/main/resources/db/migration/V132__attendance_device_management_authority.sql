-- Device provisioning is a distinct high-risk setup action.  Do not grant it
-- to the ordinary principal template: a fresh bootstrap administrator receives
-- the narrow user exception alongside the other first-school setup writes.
INSERT INTO permission_action
    (code,module,group_code,label_fr,label_en,description_fr,description_en,
     risk_level,scope_type,required_level,default_read_action,display_order)
VALUES
    ('ATTENDANCE_DEVICE_MANAGE','presence','Attendance',
     'Terminaux de présence — gérer','Attendance devices — manage',
     'Enregistrer les lecteurs de présence de l’établissement.',
     'Register the school attendance readers.',
     'HIGH','SCHOOL','write',false,210)
ON CONFLICT (code) DO UPDATE SET
    module=EXCLUDED.module,
    group_code=EXCLUDED.group_code,
    label_fr=EXCLUDED.label_fr,
    label_en=EXCLUDED.label_en,
    description_fr=EXCLUDED.description_fr,
    description_en=EXCLUDED.description_en,
    risk_level=EXCLUDED.risk_level,
    scope_type=EXCLUDED.scope_type,
    required_level=EXCLUDED.required_level,
    default_read_action=EXCLUDED.default_read_action,
    display_order=EXCLUDED.display_order,
    active=true,
    updated_at=now();

-- V124 marks a bootstrap user without relying on a username or role label.
-- Ordinary principals do not carry this reason and remain denied.
INSERT INTO permission_user_action
    (school_id,user_id,action_code,effect,scope_mode,is_permanent,reason)
SELECT u.school_id,u.id,'ATTENDANCE_DEVICE_MANAGE','ALLOW','SCHOOL_ALL',true,
       'Fresh-school bootstrap setup authority; replace during access-control setup'
  FROM app_user u
 WHERE EXISTS (
       SELECT 1
         FROM permission_user_action existing
        WHERE existing.school_id=u.school_id
          AND existing.user_id=u.id
          AND existing.action_code='SESSION_MANAGE'
          AND existing.effect='ALLOW'
          AND existing.reason='Fresh-school bootstrap setup authority; replace during access-control setup'
   )
ON CONFLICT DO NOTHING;
