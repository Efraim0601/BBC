-- Direction's grade-entry review page is read-only, but the shared grade
-- workflow first loads the class/subject sheet through the stable subject-grade
-- view action. Keep that read gate aligned with the existing review authority.
INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT s.id, 'principal', 'ACADEMIC_SUBJECT_GRADE_VIEW', true
  FROM school s
ON CONFLICT (school_id, role_code, action_code)
DO UPDATE SET allowed = EXCLUDED.allowed;

INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode, is_permanent, reason)
SELECT s.id, 'principal', 'ACADEMIC_SUBJECT_GRADE_VIEW', 'ALLOW', 'SCHOOL_ALL', true,
       'Direction read-only access to grade packets under review'
  FROM school s
ON CONFLICT DO NOTHING;

INSERT INTO permission_role_template_rule
    (template_code, action_code, effect, scope_mode, is_permanent, reason, display_order)
VALUES
 ('principal_oversight','ACADEMIC_SUBJECT_GRADE_VIEW','ALLOW','SCHOOL_ALL',true,
  'Direction read-only grade packet access',33)
ON CONFLICT DO NOTHING;

INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode, is_permanent, reason)
SELECT s.id, 'principal_legacy_compat', 'ACADEMIC_SUBJECT_GRADE_VIEW', 'ALLOW', 'SCHOOL_ALL', true,
       'Direction grade packet read compatibility authority'
  FROM school s
ON CONFLICT DO NOTHING;
