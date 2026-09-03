-- Role-boundary repair following the full local QA walkthrough.
--
-- The policy engine evaluates V2 role rules.  Legacy module grants remain a
-- navigation/compatibility envelope, so both layers are aligned here.

-- -------------------------------------------------------------------------
-- Principal: finance is oversight only.  Remove operational and payroll
-- authority without creating an explicit DENY that would also block a user
-- who is deliberately assigned the Accountant role in addition to Principal.

WITH removed AS (
    SELECT code
      FROM permission_action
     WHERE (module = 'finance' AND required_level = 'write')
        OR code IN ('FINANCE_REPORT_VIEW','FINANCE_EXPORT',
                    'PAYROLL_VIEW','PAYROLL_PERIOD_MANAGE','PAYROLL_COMPONENT_MANAGE',
                    'PAYROLL_CALCULATE','PAYROLL_ADJUST','PAYROLL_REVIEW',
                    'PAYROLL_APPROVE','PAYROLL_PAY','PAYROLL_VOID',
                    'PAYSLIP_VIEW_ALL','PAYSLIP_REGENERATE')
)
UPDATE permission_role_action p
   SET effect='INHERIT', scope_mode='NONE', scope_payload=NULL,
       reason='Principal finance is read-only', version=p.version+1,
       updated_at=now()
  FROM removed r
 WHERE p.role_code='principal'
   AND p.action_code=r.code
   AND p.effective_from IS NULL
   AND p.effective_to IS NULL;

WITH removed AS (
    SELECT code
      FROM permission_action
     WHERE (module = 'finance' AND required_level = 'write')
        OR code IN ('FINANCE_REPORT_VIEW','FINANCE_EXPORT',
                    'PAYROLL_VIEW','PAYROLL_PERIOD_MANAGE','PAYROLL_COMPONENT_MANAGE',
                    'PAYROLL_CALCULATE','PAYROLL_ADJUST','PAYROLL_REVIEW',
                    'PAYROLL_APPROVE','PAYROLL_PAY','PAYROLL_VOID',
                    'PAYSLIP_VIEW_ALL','PAYSLIP_REGENERATE')
)
UPDATE permission_action_grant legacy
   SET allowed=false
  FROM removed r
 WHERE legacy.role_code='principal'
   AND legacy.action_code=r.code;

UPDATE permission_grant
   SET level='read'
 WHERE role_code='principal' AND module='finance';

-- Principal oversight pages must actually work inside the active parcours.
-- Replace stale broad/deny defaults with one parcours-scoped read rule.
DELETE FROM permission_role_action
 WHERE role_code='principal'
   AND effective_from IS NULL AND effective_to IS NULL
   AND action_code IN ('STUDENT_PROFILE_VIEW','STUDENT_PHOTO_VIEW','GUARDIAN_VIEW',
                       'ENROLLMENT_VIEW','STUDENT_DOCUMENT_VIEW','DOCUMENT_VIEW',
                       'HEALTH_VIEW','JOURNEY_VIEW','PROGRESSION_VIEW','CLASSKIT_VIEW');

WITH allowed(action_code) AS (VALUES
    ('STUDENT_PROFILE_VIEW'),('STUDENT_PHOTO_VIEW'),('GUARDIAN_VIEW'),
    ('ENROLLMENT_VIEW'),('STUDENT_DOCUMENT_VIEW'),('DOCUMENT_VIEW'),
    ('HEALTH_VIEW'),('JOURNEY_VIEW'),('PROGRESSION_VIEW'),('CLASSKIT_VIEW')
)
INSERT INTO permission_role_action
    (school_id,role_code,action_code,effect,scope_mode,is_permanent,reason)
SELECT school.id,'principal',allowed.action_code,'ALLOW','PARCOURS_ALLOWED',true,
       'Principal parcours oversight'
  FROM school CROSS JOIN allowed;

WITH allowed(action_code) AS (VALUES
    ('STUDENT_PROFILE_VIEW'),('STUDENT_PHOTO_VIEW'),('GUARDIAN_VIEW'),
    ('ENROLLMENT_VIEW'),('STUDENT_DOCUMENT_VIEW'),('DOCUMENT_VIEW'),
    ('HEALTH_VIEW'),('JOURNEY_VIEW'),('PROGRESSION_VIEW'),('CLASSKIT_VIEW')
)
INSERT INTO permission_action_grant (school_id,role_code,action_code,allowed)
SELECT school.id,'principal',allowed.action_code,true FROM school CROSS JOIN allowed
ON CONFLICT (school_id,role_code,action_code)
DO UPDATE SET allowed=EXCLUDED.allowed;

-- Match the documented Principal module levels: discipline/coursebook/alerts
-- are operational, promotion is an oversight view.
UPDATE permission_grant SET level='write'
 WHERE role_code='principal' AND module IN ('discipline','coursebook','alerts');
UPDATE permission_grant SET level='read'
 WHERE role_code='principal' AND module='promotion';

-- -------------------------------------------------------------------------
-- Teachers: student registration is never a teacher authority.  INHERIT is
-- intentional (rather than DENY) so a genuine multi-role Registrar/Principal
-- account can still receive that authority from its management role.

UPDATE permission_role_action
   SET effect='INHERIT', scope_mode='NONE', scope_payload=NULL,
       reason='Teachers do not register students', version=version+1,
       updated_at=now()
 WHERE role_code IN ('teacher','secondary_teacher','form_teacher')
   AND action_code='STUDENT_PROFILE_CREATE'
   AND effective_from IS NULL AND effective_to IS NULL;

UPDATE permission_action_grant
   SET allowed=false
 WHERE role_code IN ('teacher','secondary_teacher','form_teacher')
   AND action_code='STUDENT_PROFILE_CREATE';

-- Teachers may use the correspondence page, but not administer it.
WITH teacher_roles(role_code) AS (VALUES
    ('teacher'),('secondary_teacher'),('form_teacher')
), updated AS (
    UPDATE permission_role_action p
       SET effect='ALLOW', scope_mode='SCHOOL_ALL', scope_payload=NULL,
           is_permanent=true, reason='Teacher correspondence view',
           version=p.version+1, updated_at=now()
      FROM teacher_roles r
     WHERE p.role_code=r.role_code AND p.action_code='MESSAGES_VIEW'
       AND p.scope_mode='SCHOOL_ALL'
       AND p.effective_from IS NULL AND p.effective_to IS NULL
    RETURNING p.school_id,p.role_code
)
INSERT INTO permission_role_action
    (school_id,role_code,action_code,effect,scope_mode,is_permanent,reason)
SELECT school.id,r.role_code,'MESSAGES_VIEW','ALLOW','SCHOOL_ALL',true,
       'Teacher correspondence view'
  FROM school CROSS JOIN teacher_roles r
 WHERE NOT EXISTS (
       SELECT 1 FROM permission_role_action p
        WHERE p.school_id=school.id AND p.role_code=r.role_code
          AND p.action_code='MESSAGES_VIEW' AND p.scope_mode='SCHOOL_ALL'
          AND p.effective_from IS NULL AND p.effective_to IS NULL
 );

WITH teacher_roles(role_code) AS (VALUES
    ('teacher'),('secondary_teacher'),('form_teacher')
)
INSERT INTO permission_action_grant (school_id,role_code,action_code,allowed)
SELECT school.id,r.role_code,'MESSAGES_VIEW',true FROM school CROSS JOIN teacher_roles r
ON CONFLICT (school_id,role_code,action_code)
DO UPDATE SET allowed=EXCLUDED.allowed;

-- A normal Secondary subject teacher may not open class-wide results or
-- validate/generate report cards.  Those rules remain available only through
-- a current titulaire assignment.
UPDATE permission_role_action
   SET effect='INHERIT', scope_mode='NONE', scope_payload=NULL,
       reason='Secondary report cards require titulaire scope',
       version=version+1, updated_at=now()
 WHERE role_code='secondary_teacher'
   AND action_code IN ('ACADEMIC_CLASS_RESULTS_VIEW',
                       'ACADEMIC_REPORT_CARD_VIEW',
                       'ACADEMIC_REPORT_CARD_VALIDATE')
   AND scope_mode='ASSIGNED_CLASSES'
   AND effective_from IS NULL AND effective_to IS NULL;

-- -------------------------------------------------------------------------
-- Prefect: one coherent school-life role.  Reset only undated role defaults;
-- dated delegations and per-user overrides remain explicit audit records.

DELETE FROM permission_grant WHERE role_code='prefect';

WITH modules(module,level) AS (VALUES
    ('dashboard','read'),('students','read'),('academic','read'),
    ('presence','write'),('discipline','write'),('alerts','write'),
    ('timetable','read'),('coursebook','read'),('journey','read'),
    ('health','read'),('documents','read'),('messages','write'),
    ('reports','read'),('library','read'),('classkit','read')
)
INSERT INTO permission_grant (school_id,role_code,module,level)
SELECT school.id,'prefect',modules.module,modules.level FROM school CROSS JOIN modules;

DELETE FROM permission_role_action
 WHERE role_code='prefect'
   AND effective_from IS NULL AND effective_to IS NULL;

WITH allowed(action_code) AS (VALUES
    ('DASHBOARD_VIEW'),
    ('STUDENT_DIRECTORY_VIEW'),('STUDENT_PROFILE_VIEW'),('STUDENT_PHOTO_VIEW'),
    ('GUARDIAN_VIEW'),('ENROLLMENT_VIEW'),
    ('ACADEMIC_ROSTER_VIEW'),('ACADEMIC_CLASS_RESULTS_VIEW'),
    ('ACADEMIC_REPORT_CARD_VIEW'),('ACADEMIC_COUNCIL_INPUT_VIEW'),
    ('ATTENDANCE_ROSTER_VIEW'),('ATTENDANCE_MARK'),('ATTENDANCE_FINALIZE'),
    ('ATTENDANCE_REOPEN'),('ATTENDANCE_ANALYTICS_VIEW'),
    ('ATTENDANCE_POLICY_VIEW'),('ATTENDANCE_RECONCILE'),
    ('ATTENDANCE_DEVICE_VIEW'),('ATTENDANCE_NOTIFICATION_VIEW'),
    ('DISCIPLINE_VIEW'),('DISCIPLINE_MANAGE'),
    ('ALERTS_VIEW'),('ALERTS_MANAGE'),
    ('TIMETABLE_CLASS_SCHEDULE_VIEW'),('TIMETABLE_MASTER_VIEW'),
    ('TIMETABLE_TEACHER_SCHEDULE_VIEW_ALL'),('TIMETABLE_ROOM_VIEW'),
    ('TIMETABLE_RESOURCE_VIEW'),('TIMETABLE_SUBSTITUTION_VIEW'),
    ('COURSEBOOK_VIEW'),('MESSAGES_VIEW'),('MESSAGES_MANAGE'),
    ('REPORTS_VIEW'),('JOURNEY_VIEW'),('HEALTH_VIEW'),
    ('STUDENT_DOCUMENT_VIEW'),('DOCUMENT_VIEW'),('CLASSKIT_VIEW'),
    ('SESSION_VIEW'),('ACADEMIC_STRUCTURE_VIEW'),('CALENDAR_VIEW')
)
INSERT INTO permission_role_action
    (school_id,role_code,action_code,effect,scope_mode,is_permanent,reason)
SELECT school.id,'prefect',allowed.action_code,'ALLOW','SCHOOL_ALL',true,
       'Prefect school-life role alignment'
  FROM school CROSS JOIN allowed;

UPDATE permission_action_grant SET allowed=false WHERE role_code='prefect';

WITH allowed(action_code) AS (VALUES
    ('DASHBOARD_VIEW'),
    ('STUDENT_DIRECTORY_VIEW'),('STUDENT_PROFILE_VIEW'),('STUDENT_PHOTO_VIEW'),
    ('GUARDIAN_VIEW'),('ENROLLMENT_VIEW'),
    ('ACADEMIC_ROSTER_VIEW'),('ACADEMIC_CLASS_RESULTS_VIEW'),
    ('ACADEMIC_REPORT_CARD_VIEW'),('ACADEMIC_COUNCIL_INPUT_VIEW'),
    ('ATTENDANCE_ROSTER_VIEW'),('ATTENDANCE_MARK'),('ATTENDANCE_FINALIZE'),
    ('ATTENDANCE_REOPEN'),('ATTENDANCE_ANALYTICS_VIEW'),
    ('ATTENDANCE_POLICY_VIEW'),('ATTENDANCE_RECONCILE'),
    ('ATTENDANCE_DEVICE_VIEW'),('ATTENDANCE_NOTIFICATION_VIEW'),
    ('DISCIPLINE_VIEW'),('DISCIPLINE_MANAGE'),
    ('ALERTS_VIEW'),('ALERTS_MANAGE'),
    ('TIMETABLE_CLASS_SCHEDULE_VIEW'),('TIMETABLE_MASTER_VIEW'),
    ('TIMETABLE_TEACHER_SCHEDULE_VIEW_ALL'),('TIMETABLE_ROOM_VIEW'),
    ('TIMETABLE_RESOURCE_VIEW'),('TIMETABLE_SUBSTITUTION_VIEW'),
    ('COURSEBOOK_VIEW'),('MESSAGES_VIEW'),('MESSAGES_MANAGE'),
    ('REPORTS_VIEW'),('JOURNEY_VIEW'),('HEALTH_VIEW'),
    ('STUDENT_DOCUMENT_VIEW'),('DOCUMENT_VIEW'),('CLASSKIT_VIEW'),
    ('SESSION_VIEW'),('ACADEMIC_STRUCTURE_VIEW'),('CALENDAR_VIEW')
)
INSERT INTO permission_action_grant (school_id,role_code,action_code,allowed)
SELECT school.id,'prefect',allowed.action_code,true FROM school CROSS JOIN allowed
ON CONFLICT (school_id,role_code,action_code)
DO UPDATE SET allowed=EXCLUDED.allowed;
