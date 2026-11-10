-- Principals are operational academic leaders inside the parcours explicitly
-- assigned to them.  They may complete and submit grade sheets and operate
-- attendance for any class in that parcours; neither permission crosses into
-- another parcours or grants school-wide attendance configuration.

-- Replace only the standing role defaults.  Historical, dated grants and
-- per-user overrides remain auditable and are deliberately left intact.
DELETE FROM permission_role_action
 WHERE role_code IN ('principal', 'principal_legacy_compat')
   AND action_code IN (
       'ACADEMIC_SUBJECT_GRADE_VIEW',
       'ACADEMIC_SUBJECT_GRADE_EDIT',
       'GRADE_SUBMIT',
       'ATTENDANCE_ROSTER_VIEW',
       'ATTENDANCE_MARK',
       'ATTENDANCE_FINALIZE'
   )
   AND effective_from IS NULL
   AND effective_to IS NULL;

WITH director_actions(action_code, reason) AS (VALUES
    ('ACADEMIC_SUBJECT_GRADE_VIEW',
     'Principal views grade sheets in the assigned parcours'),
    ('ACADEMIC_SUBJECT_GRADE_EDIT',
     'Principal enters grades in the assigned parcours'),
    ('GRADE_SUBMIT',
     'Principal submits grade sheets in the assigned parcours'),
    ('ATTENDANCE_ROSTER_VIEW',
     'Principal views attendance rosters in the assigned parcours'),
    ('ATTENDANCE_MARK',
     'Principal marks attendance in the assigned parcours'),
    ('ATTENDANCE_FINALIZE',
     'Principal finalizes attendance in the assigned parcours')
)
INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode, is_permanent, reason)
SELECT school.id, role.code, actions.action_code, 'ALLOW', 'PARCOURS_ALLOWED',
       true, actions.reason
  FROM school
 CROSS JOIN role
 CROSS JOIN director_actions actions
 WHERE role.code IN ('principal', 'principal_legacy_compat');

-- New Principal accounts inherit the same restricted operational authority.
DELETE FROM permission_role_template_rule
 WHERE template_code='principal_oversight'
   AND action_code IN (
       'ACADEMIC_SUBJECT_GRADE_VIEW',
       'ACADEMIC_SUBJECT_GRADE_EDIT',
       'GRADE_SUBMIT',
       'ATTENDANCE_ROSTER_VIEW',
       'ATTENDANCE_MARK',
       'ATTENDANCE_FINALIZE'
   );

WITH director_template(action_code, reason, display_order) AS (VALUES
    ('ACADEMIC_SUBJECT_GRADE_VIEW',
     'Direction — consulter les notes du parcours actif', 33),
    ('ACADEMIC_SUBJECT_GRADE_EDIT',
     'Direction — saisir les notes du parcours actif', 34),
    ('GRADE_SUBMIT',
     'Direction — envoyer les notes du parcours actif', 35),
    ('ATTENDANCE_ROSTER_VIEW',
     'Direction — consulter les présences du parcours actif', 36),
    ('ATTENDANCE_MARK',
     'Direction — marquer les présences du parcours actif', 37),
    ('ATTENDANCE_FINALIZE',
     'Direction — finaliser les présences du parcours actif', 38)
)
INSERT INTO permission_role_template_rule
    (template_code, action_code, effect, scope_mode, is_permanent, reason, display_order)
SELECT 'principal_oversight', action_code, 'ALLOW', 'PARCOURS_ALLOWED',
       true, reason, display_order
  FROM director_template;

-- Controllers use these coarse gates before the resource-aware service.  The
-- service is the final authority and evaluates the PARCOURS_ALLOWED rules
-- above against the real class, subject and attendance occurrence.
WITH director_actions(action_code) AS (VALUES
    ('ACADEMIC_SUBJECT_GRADE_VIEW'),
    ('ACADEMIC_SUBJECT_GRADE_EDIT'),
    ('GRADE_SUBMIT'),
    ('ATTENDANCE_ROSTER_VIEW'),
    ('ATTENDANCE_MARK'),
    ('ATTENDANCE_FINALIZE')
)
INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT school.id, role.code, actions.action_code, true
  FROM school
 CROSS JOIN role
 CROSS JOIN director_actions actions
 WHERE role.code IN ('principal', 'principal_legacy_compat')
ON CONFLICT (school_id, role_code, action_code)
DO UPDATE SET allowed=EXCLUDED.allowed;
