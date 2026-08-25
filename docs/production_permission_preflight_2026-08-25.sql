\pset pager off
\echo '=== Flyway version ==='
SELECT version, description, success, installed_on
  FROM flyway_schema_history
 ORDER BY installed_rank DESC
 LIMIT 5;

\echo '=== Missing required tested role rules (zero rows expected) ==='
WITH expected(role_code,action_code,effect,scope_mode) AS (
    VALUES
      ('teacher','STUDENT_DIRECTORY_VIEW','ALLOW','ASSIGNED_CLASSES'),
      ('teacher','STUDENT_PROFILE_VIEW','ALLOW','ASSIGNED_CLASSES'),
      ('teacher','ACADEMIC_ROSTER_VIEW','ALLOW','ASSIGNED_CLASSES'),
      ('teacher','ACADEMIC_ASSESSMENT_VIEW','ALLOW','ASSIGNED_CLASSES'),
      ('teacher','ACADEMIC_ASSESSMENT_MANAGE','ALLOW','ASSIGNED_CLASSES'),
      ('teacher','ACADEMIC_SUBJECT_GRADE_VIEW','ALLOW','ASSIGNED_CLASS_SUBJECTS'),
      ('teacher','ACADEMIC_SUBJECT_GRADE_EDIT','ALLOW','ASSIGNED_CLASS_SUBJECTS'),
      ('teacher','GRADE_SUBMIT','ALLOW','ASSIGNED_CLASS_SUBJECTS'),
      ('teacher','GRADE_EDIT_ANY_SUBJECT_IN_TITULAIRE_CLASS','ALLOW','TITULAIRE_CLASSES'),
      ('teacher','ATTENDANCE_ROSTER_VIEW','ALLOW','TITULAIRE_CLASSES'),
      ('teacher','ATTENDANCE_MARK','ALLOW','TITULAIRE_CLASSES'),
      ('teacher','ATTENDANCE_FINALIZE','ALLOW','TITULAIRE_CLASSES'),
      ('teacher','ACADEMIC_REPORT_CARD_VALIDATE','ALLOW','TITULAIRE_CLASSES'),
      ('teacher','DOCUMENT_GENERATE','ALLOW','TITULAIRE_CLASSES'),
      ('teacher','COURSEBOOK_VIEW','ALLOW','TITULAIRE_CLASSES'),
      ('teacher','COURSEBOOK_MANAGE','ALLOW','TITULAIRE_CLASSES'),
      ('teacher','ACADEMIC_COUNCIL_INPUT_VIEW','ALLOW','TITULAIRE_CLASSES'),
      ('teacher','ACADEMIC_COUNCIL_INPUT_EDIT','ALLOW','TITULAIRE_CLASSES'),
      ('secondary_teacher','ACADEMIC_SUBJECT_GRADE_VIEW','ALLOW','ASSIGNED_CLASS_SUBJECTS'),
      ('secondary_teacher','ACADEMIC_SUBJECT_GRADE_EDIT','ALLOW','ASSIGNED_CLASS_SUBJECTS'),
      ('secondary_teacher','GRADE_SUBMIT','ALLOW','ASSIGNED_CLASS_SUBJECTS'),
      ('secondary_teacher','ATTENDANCE_ROSTER_VIEW','ALLOW','TIMETABLE_OCCURRENCES_ASSIGNED'),
      ('secondary_teacher','ATTENDANCE_MARK','ALLOW','TIMETABLE_OCCURRENCES_ASSIGNED'),
      ('secondary_teacher','ATTENDANCE_FINALIZE','ALLOW','TIMETABLE_OCCURRENCES_ASSIGNED'),
      ('secondary_teacher','ACADEMIC_CLASS_RESULTS_VIEW','ALLOW','TITULAIRE_CLASSES'),
      ('secondary_teacher','ACADEMIC_REPORT_CARD_VIEW','ALLOW','TITULAIRE_CLASSES'),
      ('secondary_teacher','ACADEMIC_GRADE_PACKET_REVIEW','ALLOW','TITULAIRE_CLASSES'),
      ('secondary_teacher','ACADEMIC_COUNCIL_INPUT_VIEW','ALLOW','TITULAIRE_CLASSES'),
      ('secondary_teacher','ATTENDANCE_ROSTER_VIEW','ALLOW','TITULAIRE_CLASSES'),
      ('secondary_teacher','ATTENDANCE_REOPEN','ALLOW','TITULAIRE_CLASSES'),
      ('secondary_teacher','ACADEMIC_REPORT_CARD_VALIDATE','ALLOW','TITULAIRE_CLASSES'),
      ('secondary_teacher','DOCUMENT_GENERATE','ALLOW','TITULAIRE_CLASSES'),
      ('secondary_teacher','COURSEBOOK_VIEW','ALLOW','ASSIGNED_CLASSES'),
      ('secondary_teacher','COURSEBOOK_MANAGE','ALLOW','ASSIGNED_CLASS_SUBJECTS'),
      ('principal','ACADEMIC_ASSESSMENT_VIEW','ALLOW','PARCOURS_ALLOWED'),
      ('principal','ACADEMIC_ASSESSMENT_MANAGE','ALLOW','PARCOURS_ALLOWED'),
      ('principal','ACADEMIC_CLASS_RESULTS_VIEW','ALLOW','PARCOURS_ALLOWED'),
      ('principal','ACADEMIC_COUNCIL_INPUT_VIEW','ALLOW','PARCOURS_ALLOWED'),
      ('principal','ACADEMIC_GRADE_PACKET_REVIEW','ALLOW','PARCOURS_ALLOWED'),
      ('principal','ACADEMIC_REPORT_CARD_PUBLISH','ALLOW','PARCOURS_ALLOWED'),
      ('principal','ACADEMIC_REPORT_CARD_VALIDATE','ALLOW','PARCOURS_ALLOWED'),
      ('principal','ACADEMIC_REPORT_CARD_VIEW','ALLOW','PARCOURS_ALLOWED'),
      ('principal','ACADEMIC_ROSTER_VIEW','ALLOW','PARCOURS_ALLOWED'),
      ('principal','ACADEMIC_SUBJECT_GRADE_VIEW','ALLOW','PARCOURS_ALLOWED'),
      ('principal','STUDENT_DIRECTORY_VIEW','ALLOW','PARCOURS_ALLOWED'),
      ('principal','STUDENT_PROFILE_CREATE','ALLOW','PARCOURS_ALLOWED'),
      ('principal','STUDENT_PROFILE_VIEW','ALLOW','PARCOURS_ALLOWED'),
      ('principal','HR_VIEW','ALLOW','PARCOURS_ALLOWED'),
      ('principal','HR_MANAGE','ALLOW','PARCOURS_ALLOWED'),
      ('principal','PERMISSION_VIEW','DENY','SCHOOL_ALL'),
      ('principal','PERMISSION_MANAGE','DENY','SCHOOL_ALL'),
      ('principal','ROLE_MANAGE','DENY','SCHOOL_ALL')
)
SELECT s.code AS school_code,e.*
  FROM school s
 CROSS JOIN expected e
 WHERE NOT EXISTS (
    SELECT 1
      FROM permission_role_action r
     WHERE r.school_id=s.id
       AND r.role_code=e.role_code
       AND r.action_code=e.action_code
       AND r.effect=e.effect
       AND r.scope_mode=e.scope_mode
       AND (r.is_permanent OR
            (CURRENT_DATE >= COALESCE(r.effective_from,CURRENT_DATE)
             AND CURRENT_DATE <= COALESCE(r.effective_to,CURRENT_DATE)))
 )
 ORDER BY s.code,e.role_code,e.action_code;

\echo '=== Active DENY conflicts for expected ALLOW rules (zero rows expected) ==='
WITH expected(role_code,action_code) AS (
    VALUES
      ('teacher','ACADEMIC_COUNCIL_INPUT_VIEW'),
      ('teacher','ACADEMIC_COUNCIL_INPUT_EDIT'),
      ('teacher','GRADE_EDIT_ANY_SUBJECT_IN_TITULAIRE_CLASS'),
      ('secondary_teacher','ACADEMIC_COUNCIL_INPUT_VIEW'),
      ('secondary_teacher','ACADEMIC_REPORT_CARD_VALIDATE'),
      ('principal','HR_VIEW'),
      ('principal','HR_MANAGE')
)
SELECT s.code AS school_code,r.role_code,r.action_code,r.scope_mode,r.reason
  FROM permission_role_action r
  JOIN expected e ON e.role_code=r.role_code AND e.action_code=r.action_code
  JOIN school s ON s.id=r.school_id
 WHERE r.effect='DENY'
   AND (r.is_permanent OR
        (CURRENT_DATE >= COALESCE(r.effective_from,CURRENT_DATE)
         AND CURRENT_DATE <= COALESCE(r.effective_to,CURRENT_DATE)))
 ORDER BY s.code,r.role_code,r.action_code;

\echo '=== Secondary council edit grants (zero rows expected) ==='
SELECT s.code AS school_code,r.role_code,r.action_code,r.effect,r.scope_mode,r.reason
  FROM permission_role_action r
  JOIN school s ON s.id=r.school_id
 WHERE r.role_code='secondary_teacher'
   AND r.action_code='ACADEMIC_COUNCIL_INPUT_EDIT'
   AND r.effect='ALLOW';

\echo '=== Teacher accounts with wrong role or scope mode (zero rows expected) ==='
SELECT u.username,u.display_name,u.role_code,e.level,u.parcours_scope_mode,u.active
  FROM app_user u
  JOIN employee e ON e.id=u.employee_id AND e.school_id=u.school_id
 WHERE u.role_code IN ('teacher','secondary_teacher','form_teacher')
   AND (
       u.parcours_scope_mode <> 'ASSIGNMENT_DERIVED'
       OR (u.role_code='teacher' AND lower(COALESCE(e.level,''))='secondary')
       OR (u.role_code='secondary_teacher' AND lower(COALESCE(e.level,''))<>'secondary')
   )
 ORDER BY u.username;

\echo '=== Principal explicit cycle assignments ==='
SELECT u.username,u.display_name,u.parcours_scope_mode,
       string_agg(p.level || '/' || p.subsystem, ', ' ORDER BY p.level,p.subsystem) AS cycles
  FROM app_user u
  LEFT JOIN app_user_parcours p ON p.user_id=u.id
 WHERE u.role_code='principal'
 GROUP BY u.id
 ORDER BY u.username;

\echo '=== Principal access-control invariant (three DENY rows per school expected) ==='
SELECT s.code AS school_code,r.action_code,r.effect,r.scope_mode,r.reason
  FROM permission_role_action r
  JOIN school s ON s.id=r.school_id
 WHERE r.role_code='principal'
   AND r.action_code IN ('PERMISSION_VIEW','PERMISSION_MANAGE','ROLE_MANAGE')
 ORDER BY s.code,r.action_code;

\echo '=== Per-user override counts (Administrator rows are redundant after V168) ==='
SELECT u.username,u.role_code,count(*) AS override_count
  FROM permission_user_action p
  JOIN app_user u ON u.id=p.user_id AND u.school_id=p.school_id
 WHERE u.username NOT LIKE 'qa.%'
 GROUP BY u.id
 ORDER BY u.username;

\echo '=== Detailed non-QA, non-Administrator overrides (review every row) ==='
SELECT u.username,u.role_code,p.action_code,p.effect,p.scope_mode,p.is_permanent,p.reason
  FROM permission_user_action p
  JOIN app_user u ON u.id=p.user_id AND u.school_id=p.school_id
 WHERE u.username NOT LIKE 'qa.%'
   AND u.role_code<>'administrator'
 ORDER BY u.username,p.action_code;

\echo '=== Permission policy versions ==='
SELECT s.code,v.version,v.updated_at
  FROM school_permission_version v
  JOIN school s ON s.id=v.school_id
 ORDER BY s.code;
