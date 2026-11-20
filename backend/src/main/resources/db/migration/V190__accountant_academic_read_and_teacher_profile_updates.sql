-- Accountants may consult academic records across the school.  This is a
-- read-only workspace: grade sheets/grade entry and every academic mutation
-- remain absent from the role's rules and legacy action envelope.
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT s.id, 'accountant', 'academic', 'read'
  FROM school s
ON CONFLICT (school_id, role_code, module)
DO UPDATE SET level = EXCLUDED.level;

DELETE FROM permission_role_action
 WHERE role_code = 'accountant'
   AND action_code IN (
       'ACADEMIC_STRUCTURE_VIEW', 'ACADEMIC_ROSTER_VIEW',
       'ACADEMIC_CLASS_RESULTS_VIEW', 'ACADEMIC_REPORT_CARD_VIEW',
       'ACADEMIC_COUNCIL_INPUT_VIEW'
   )
   AND effective_from IS NULL AND effective_to IS NULL;

WITH accountant_read(action_code, reason) AS (VALUES
    ('ACADEMIC_STRUCTURE_VIEW',    'Accountant reads academic class references'),
    ('ACADEMIC_ROSTER_VIEW',       'Accountant reads academic rosters'),
    ('ACADEMIC_CLASS_RESULTS_VIEW','Accountant reads class academic results'),
    ('ACADEMIC_REPORT_CARD_VIEW',  'Accountant reads report cards'),
    ('ACADEMIC_COUNCIL_INPUT_VIEW','Accountant reads attendance and council inputs')
)
INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode, is_permanent, reason)
SELECT s.id, 'accountant', a.action_code, 'ALLOW', 'SCHOOL_ALL', true, a.reason
  FROM school s CROSS JOIN accountant_read a;

WITH accountant_read(action_code) AS (VALUES
    ('ACADEMIC_STRUCTURE_VIEW'), ('ACADEMIC_ROSTER_VIEW'),
    ('ACADEMIC_CLASS_RESULTS_VIEW'), ('ACADEMIC_REPORT_CARD_VIEW'),
    ('ACADEMIC_COUNCIL_INPUT_VIEW')
)
INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT s.id, 'accountant', a.action_code, true
  FROM school s CROSS JOIN accountant_read a
ON CONFLICT (school_id, role_code, action_code)
DO UPDATE SET allowed = EXCLUDED.allowed;

DELETE FROM permission_role_template_rule
 WHERE template_code = 'accountant'
   AND action_code IN (
       'ACADEMIC_STRUCTURE_VIEW', 'ACADEMIC_ROSTER_VIEW',
       'ACADEMIC_CLASS_RESULTS_VIEW', 'ACADEMIC_REPORT_CARD_VIEW',
       'ACADEMIC_COUNCIL_INPUT_VIEW'
   );

INSERT INTO permission_role_template_rule
    (template_code, action_code, effect, scope_mode, is_permanent, reason, display_order)
VALUES
    ('accountant','ACADEMIC_STRUCTURE_VIEW','ALLOW','SCHOOL_ALL',true,
     'Accountant reads academic class references',84),
    ('accountant','ACADEMIC_ROSTER_VIEW','ALLOW','SCHOOL_ALL',true,
     'Accountant reads academic rosters',85),
    ('accountant','ACADEMIC_CLASS_RESULTS_VIEW','ALLOW','SCHOOL_ALL',true,
     'Accountant reads class academic results',86),
    ('accountant','ACADEMIC_REPORT_CARD_VIEW','ALLOW','SCHOOL_ALL',true,
     'Accountant reads report cards',87),
    ('accountant','ACADEMIC_COUNCIL_INPUT_VIEW','ALLOW','SCHOOL_ALL',true,
     'Accountant reads attendance and council inputs',88);

-- Teachers may update profile information for pupils in their dated homeroom
-- class.  Secondary subject assignments do not satisfy TITULAIRE_CLASSES.
-- Deactivation and enrollment/transfer actions are deliberately untouched.
DELETE FROM permission_role_action
 WHERE role_code IN ('teacher', 'secondary_teacher', 'form_teacher')
   AND action_code = 'STUDENT_PROFILE_EDIT'
   AND effective_from IS NULL AND effective_to IS NULL;

WITH teacher_roles(role_code, reason) AS (VALUES
    ('teacher',           'Primary and Kindergarten titulaire updates own student profiles'),
    ('secondary_teacher', 'Secondary titulaire updates own student profiles'),
    ('form_teacher',      'Titulaire updates own student profiles')
)
INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode, is_permanent, reason)
SELECT s.id, r.role_code, 'STUDENT_PROFILE_EDIT', 'ALLOW', 'TITULAIRE_CLASSES', true, r.reason
  FROM school s CROSS JOIN teacher_roles r;

WITH teacher_roles(role_code) AS (VALUES ('teacher'), ('secondary_teacher'), ('form_teacher'))
INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT s.id, r.role_code, 'STUDENT_PROFILE_EDIT', true
  FROM school s CROSS JOIN teacher_roles r
ON CONFLICT (school_id, role_code, action_code)
DO UPDATE SET allowed = EXCLUDED.allowed;

DELETE FROM permission_role_template_rule
 WHERE template_code IN ('primary_teacher', 'secondary_teacher', 'form_teacher')
   AND action_code = 'STUDENT_PROFILE_EDIT';

INSERT INTO permission_role_template_rule
    (template_code, action_code, effect, scope_mode, is_permanent, reason, display_order)
VALUES
    ('primary_teacher','STUDENT_PROFILE_EDIT','ALLOW','TITULAIRE_CLASSES',true,
     'Primary and Kindergarten titulaire updates own student profiles',14),
    ('secondary_teacher','STUDENT_PROFILE_EDIT','ALLOW','TITULAIRE_CLASSES',true,
     'Secondary titulaire updates own student profiles',14),
    ('form_teacher','STUDENT_PROFILE_EDIT','ALLOW','TITULAIRE_CLASSES',true,
     'Titulaire updates own student profiles',14);
