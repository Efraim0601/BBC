-- Separate the two teaching models at the role boundary. Primary and
-- Kindergarten teachers use the dated homeroom authority; Secondary teachers
-- use their assigned published timetable occurrences.

INSERT INTO role(code,label_fr,label_en,builtin)
VALUES ('secondary_teacher','Enseignant secondaire','Secondary teacher',true)
ON CONFLICT (code) DO UPDATE SET
    label_fr=EXCLUDED.label_fr,label_en=EXCLUDED.label_en,builtin=true;

UPDATE role SET label_fr='Enseignant primaire / maternelle',
                label_en='Primary / Kindergarten teacher'
 WHERE code='teacher';

-- Keep the legacy module/action matrix identical to Teacher.
INSERT INTO permission_grant(school_id,role_code,module,level)
SELECT school_id,'secondary_teacher',module,level
  FROM permission_grant
 WHERE role_code='teacher'
ON CONFLICT (school_id,role_code,module) DO UPDATE SET level=EXCLUDED.level;

INSERT INTO permission_action_grant(school_id,role_code,action_code,allowed)
SELECT school_id,'secondary_teacher',action_code,allowed
  FROM permission_action_grant
 WHERE role_code='teacher'
ON CONFLICT (school_id,role_code,action_code) DO UPDATE SET allowed=EXCLUDED.allowed;

-- Clone all current V2 Teacher rules first so school-specific configuration is
-- preserved, then replace only the attendance rules that differ by level.
INSERT INTO permission_role_action
    (school_id,role_code,action_code,effect,scope_mode,scope_payload,
     effective_from,effective_to,is_permanent,reason,created_by,updated_by)
SELECT school_id,'secondary_teacher',action_code,effect,scope_mode,scope_payload,
       effective_from,effective_to,is_permanent,
       'Secondary teacher copy: ' || reason,created_by,updated_by
  FROM permission_role_action
 WHERE role_code='teacher'
ON CONFLICT DO NOTHING;

DELETE FROM permission_role_action
 WHERE role_code='teacher' AND effect='ALLOW'
   AND action_code IN ('ATTENDANCE_ROSTER_VIEW','ATTENDANCE_MARK','ATTENDANCE_FINALIZE');

DELETE FROM permission_role_action
 WHERE role_code='secondary_teacher' AND effect='ALLOW'
   AND action_code IN ('ATTENDANCE_ROSTER_VIEW','ATTENDANCE_MARK',
                       'ATTENDANCE_FINALIZE','ATTENDANCE_ANALYTICS_VIEW');

INSERT INTO permission_role_action
    (school_id,role_code,action_code,effect,scope_mode,is_permanent,reason)
SELECT s.id,'teacher',a.code,'ALLOW','TITULAIRE_CLASSES',true,
       'Primary and Kindergarten attendance follows the active homeroom teacher'
  FROM school s
 CROSS JOIN (VALUES ('ATTENDANCE_ROSTER_VIEW'),('ATTENDANCE_MARK'),
                    ('ATTENDANCE_FINALIZE')) a(code)
 WHERE NOT EXISTS (
    SELECT 1 FROM permission_role_action r
     WHERE r.school_id=s.id AND r.role_code='teacher' AND r.action_code=a.code
       AND r.scope_mode='TITULAIRE_CLASSES'
 );

INSERT INTO permission_role_action
    (school_id,role_code,action_code,effect,scope_mode,is_permanent,reason)
SELECT s.id,'secondary_teacher',a.code,'ALLOW','TIMETABLE_OCCURRENCES_ASSIGNED',true,
       'Secondary attendance follows assigned published timetable occurrences'
  FROM school s
 CROSS JOIN (VALUES ('ATTENDANCE_ROSTER_VIEW'),('ATTENDANCE_MARK'),
                    ('ATTENDANCE_FINALIZE'),('ATTENDANCE_ANALYTICS_VIEW')) a(code)
 WHERE NOT EXISTS (
    SELECT 1 FROM permission_role_action r
     WHERE r.school_id=s.id AND r.role_code='secondary_teacher' AND r.action_code=a.code
       AND r.scope_mode='TIMETABLE_OCCURRENCES_ASSIGNED'
 );

-- Existing staff already classified in the Secondary cycle receive the new
-- role without a user-specific override.
UPDATE app_user u
   SET role_code='secondary_teacher'
  FROM employee e
 WHERE e.id=u.employee_id AND e.school_id=u.school_id
   AND lower(e.level)='secondary' AND u.role_code='teacher';

UPDATE app_user_role ur
   SET role_code='secondary_teacher',updated_at=now(),version=version+1,
       reason='Migrated from Teacher according to the Secondary staff cycle'
  FROM app_user u JOIN employee e ON e.id=u.employee_id AND e.school_id=u.school_id
 WHERE ur.school_id=u.school_id AND ur.user_id=u.id
   AND lower(e.level)='secondary' AND ur.role_code='teacher';

INSERT INTO employee_role(employee_id,role_code)
SELECT er.employee_id,'secondary_teacher'
  FROM employee_role er JOIN employee e ON e.id=er.employee_id
 WHERE er.role_code='teacher' AND lower(e.level)='secondary'
ON CONFLICT DO NOTHING;

DELETE FROM employee_role er
 USING employee e
 WHERE e.id=er.employee_id AND er.role_code='teacher'
   AND lower(e.level)='secondary';
