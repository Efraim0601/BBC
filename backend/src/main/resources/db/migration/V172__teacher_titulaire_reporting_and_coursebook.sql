-- Teacher workflows that are class-wide must follow the dated homeroom
-- assignment. Secondary subject work remains subject-scoped.

INSERT INTO permission_role_action
    (school_id,role_code,action_code,effect,scope_mode,is_permanent,reason)
SELECT s.id,x.role_code,x.action_code,'ALLOW',x.scope_mode,true,x.reason
  FROM school s
 CROSS JOIN (VALUES
    ('teacher','ACADEMIC_REPORT_CARD_VALIDATE','TITULAIRE_CLASSES',
     'Primary and Kindergarten homeroom teachers create and validate their class report cards'),
    ('secondary_teacher','ACADEMIC_REPORT_CARD_VALIDATE','TITULAIRE_CLASSES',
     'Secondary Titulaires create and validate their class report cards'),
    ('teacher','DOCUMENT_GENERATE','TITULAIRE_CLASSES',
     'Primary and Kindergarten homeroom teachers generate official report cards'),
    ('secondary_teacher','DOCUMENT_GENERATE','TITULAIRE_CLASSES',
     'Secondary Titulaires generate official report cards'),
    ('teacher','COURSEBOOK_VIEW','TITULAIRE_CLASSES',
     'Primary and Kindergarten homeroom teachers view their class coursebook'),
    ('teacher','COURSEBOOK_MANAGE','TITULAIRE_CLASSES',
     'Primary and Kindergarten homeroom teachers manage their class coursebook'),
    ('secondary_teacher','COURSEBOOK_VIEW','ASSIGNED_CLASSES',
     'Secondary teachers view coursebooks for their assigned classes'),
    ('secondary_teacher','COURSEBOOK_MANAGE','ASSIGNED_CLASS_SUBJECTS',
     'Secondary teachers manage coursebook entries only for assigned class subjects')
 ) x(role_code,action_code,scope_mode,reason)
 WHERE NOT EXISTS (
    SELECT 1 FROM permission_role_action r
     WHERE r.school_id=s.id AND r.role_code=x.role_code
       AND r.action_code=x.action_code AND r.effect='ALLOW'
       AND r.scope_mode=x.scope_mode
 );

INSERT INTO permission_role_template_rule
    (template_code,action_code,effect,scope_mode,is_permanent,reason,display_order)
VALUES
    ('primary_teacher','ACADEMIC_REPORT_CARD_VALIDATE','ALLOW','TITULAIRE_CLASSES',true,
     'Primary and Kindergarten homeroom teachers create and validate their class report cards',45),
    ('secondary_teacher','ACADEMIC_REPORT_CARD_VALIDATE','ALLOW','TITULAIRE_CLASSES',true,
     'Secondary Titulaires create and validate their class report cards',45),
    ('primary_teacher','DOCUMENT_GENERATE','ALLOW','TITULAIRE_CLASSES',true,
     'Primary and Kindergarten homeroom teachers generate official report cards',46),
    ('secondary_teacher','DOCUMENT_GENERATE','ALLOW','TITULAIRE_CLASSES',true,
     'Secondary Titulaires generate official report cards',46),
    ('primary_teacher','COURSEBOOK_VIEW','ALLOW','TITULAIRE_CLASSES',true,
     'Primary and Kindergarten homeroom teachers view their class coursebook',70),
    ('primary_teacher','COURSEBOOK_MANAGE','ALLOW','TITULAIRE_CLASSES',true,
     'Primary and Kindergarten homeroom teachers manage their class coursebook',71),
    ('secondary_teacher','COURSEBOOK_VIEW','ALLOW','ASSIGNED_CLASSES',true,
     'Secondary teachers view coursebooks for their assigned classes',70),
    ('secondary_teacher','COURSEBOOK_MANAGE','ALLOW','ASSIGNED_CLASS_SUBJECTS',true,
     'Secondary teachers manage coursebook entries only for assigned class subjects',71)
ON CONFLICT DO NOTHING;
