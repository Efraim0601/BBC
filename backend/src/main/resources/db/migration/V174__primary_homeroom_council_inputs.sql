-- Primary and Kindergarten homeroom teachers own the non-grade inputs that
-- appear on their class report cards: attendance corrections, conduct and
-- council decisions. Keep that authority on the dated HOMEROOM assignment.
--
-- Secondary remains intentionally unchanged here: V163 grants a Secondary
-- Titulaire read-only council view, while ordinary subject teachers do not
-- receive class-wide council authority.

DELETE FROM permission_role_action
 WHERE role_code='teacher'
   AND action_code IN ('ACADEMIC_COUNCIL_INPUT_VIEW','ACADEMIC_COUNCIL_INPUT_EDIT')
   AND effect='INHERIT';

INSERT INTO permission_role_action
    (school_id,role_code,action_code,effect,scope_mode,is_permanent,reason)
SELECT s.id,'teacher',a.action_code,'ALLOW','TITULAIRE_CLASSES',true,a.reason
  FROM school s
 CROSS JOIN (VALUES
    ('ACADEMIC_COUNCIL_INPUT_VIEW',
     'Primary and Kindergarten homeroom teachers view council and attendance inputs'),
    ('ACADEMIC_COUNCIL_INPUT_EDIT',
     'Primary and Kindergarten homeroom teachers edit council and attendance inputs')
 ) a(action_code,reason)
 WHERE NOT EXISTS (
    SELECT 1
      FROM permission_role_action existing
     WHERE existing.school_id=s.id
       AND existing.role_code='teacher'
       AND existing.action_code=a.action_code
       AND existing.effect='ALLOW'
       AND existing.scope_mode='TITULAIRE_CLASSES'
 );

INSERT INTO permission_role_template_rule
    (template_code,action_code,effect,scope_mode,is_permanent,reason,display_order)
VALUES
    ('primary_teacher','ACADEMIC_COUNCIL_INPUT_VIEW','ALLOW','TITULAIRE_CLASSES',true,
     'Primary and Kindergarten homeroom teachers view council and attendance inputs',47),
    ('primary_teacher','ACADEMIC_COUNCIL_INPUT_EDIT','ALLOW','TITULAIRE_CLASSES',true,
     'Primary and Kindergarten homeroom teachers edit council and attendance inputs',48)
ON CONFLICT DO NOTHING;
