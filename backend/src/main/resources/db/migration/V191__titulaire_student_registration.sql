-- A titulaire may register a new pupil only into their own dated homeroom
-- class.  This changes the action from school-scoped to class-scoped so the
-- policy evaluator can enforce the selected target class server-side.
UPDATE permission_action
   SET scope_type='CLASS',
       description_fr='Créer un dossier élève uniquement dans une classe autorisée.',
       description_en='Create a student profile only in an authorized class.',
       updated_at=now()
 WHERE code='STUDENT_PROFILE_CREATE';

DELETE FROM permission_role_action
 WHERE role_code IN ('teacher', 'secondary_teacher', 'form_teacher')
   AND action_code='STUDENT_PROFILE_CREATE'
   AND effective_from IS NULL AND effective_to IS NULL;

WITH teacher_roles(role_code, reason) AS (VALUES
    ('teacher',           'Primary and Kindergarten titulaire registers pupils in own class'),
    ('secondary_teacher', 'Secondary titulaire registers pupils in own class'),
    ('form_teacher',      'Titulaire registers pupils in own class')
)
INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode, is_permanent, reason)
SELECT s.id, r.role_code, 'STUDENT_PROFILE_CREATE', 'ALLOW', 'TITULAIRE_CLASSES', true, r.reason
  FROM school s CROSS JOIN teacher_roles r;

WITH teacher_roles(role_code) AS (VALUES ('teacher'), ('secondary_teacher'), ('form_teacher'))
INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT s.id, r.role_code, 'STUDENT_PROFILE_CREATE', true
  FROM school s CROSS JOIN teacher_roles r
ON CONFLICT (school_id, role_code, action_code)
DO UPDATE SET allowed=EXCLUDED.allowed;

DELETE FROM permission_role_template_rule
 WHERE template_code IN ('primary_teacher', 'secondary_teacher', 'form_teacher')
   AND action_code='STUDENT_PROFILE_CREATE';

INSERT INTO permission_role_template_rule
    (template_code, action_code, effect, scope_mode, is_permanent, reason, display_order)
VALUES
    ('primary_teacher','STUDENT_PROFILE_CREATE','ALLOW','TITULAIRE_CLASSES',true,
     'Primary and Kindergarten titulaire registers pupils in own class',13),
    ('secondary_teacher','STUDENT_PROFILE_CREATE','ALLOW','TITULAIRE_CLASSES',true,
     'Secondary titulaire registers pupils in own class',13),
    ('form_teacher','STUDENT_PROFILE_CREATE','ALLOW','TITULAIRE_CLASSES',true,
     'Titulaire registers pupils in own class',13);
