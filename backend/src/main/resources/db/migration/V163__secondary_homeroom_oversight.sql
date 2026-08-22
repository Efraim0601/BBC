-- Secondary classes have a dated Titulaire for class-wide oversight, while
-- each subject keeps its own RESPONSIBLE teacher for grade entry and submission.
-- This migration adds only read/review/reopen authority; it does not grant
-- subject-grade editing or submission to the Titulaire.

-- Reopen is a class-scoped resource action for teachers.  Keeping it declared
-- as SCHOOL makes the context-free capabilities endpoint evaluate a teacher's
-- TITULAIRE_CLASSES rule without a class and incorrectly publish DENY.
UPDATE permission_action
   SET scope_type='CLASS', updated_at=now()
 WHERE code='ATTENDANCE_REOPEN';

DELETE FROM permission_role_action
 WHERE role_code='secondary_teacher'
   AND action_code IN ('ACADEMIC_COUNCIL_INPUT_VIEW','ATTENDANCE_REOPEN')
   AND effect='INHERIT';

INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode, is_permanent, reason)
SELECT s.id, 'secondary_teacher', x.action_code, 'ALLOW', 'TITULAIRE_CLASSES', true, x.reason
  FROM school s
 CROSS JOIN (VALUES
    ('ACADEMIC_CLASS_RESULTS_VIEW', 'Secondary Titulaire class results read'),
    ('ACADEMIC_REPORT_CARD_VIEW', 'Secondary Titulaire report-card read'),
    ('ACADEMIC_GRADE_PACKET_REVIEW', 'Secondary Titulaire reviews all class subject packets'),
    ('ACADEMIC_COUNCIL_INPUT_VIEW', 'Secondary Titulaire council and attendance input read'),
    ('ATTENDANCE_ROSTER_VIEW', 'Secondary Titulaire class attendance read'),
    ('ATTENDANCE_REOPEN', 'Secondary Titulaire reopens finalized class attendance')
 ) x(action_code, reason)
 ON CONFLICT DO NOTHING;

INSERT INTO permission_role_template_rule
    (template_code, action_code, effect, scope_mode, is_permanent, reason, display_order)
VALUES
    ('secondary_teacher','ACADEMIC_CLASS_RESULTS_VIEW','ALLOW','TITULAIRE_CLASSES',true,
     'Secondary Titulaire class results read',20),
    ('secondary_teacher','ACADEMIC_REPORT_CARD_VIEW','ALLOW','TITULAIRE_CLASSES',true,
     'Secondary Titulaire report-card read',21),
    ('secondary_teacher','ACADEMIC_GRADE_PACKET_REVIEW','ALLOW','TITULAIRE_CLASSES',true,
     'Secondary Titulaire reviews all class subject packets',24),
    ('secondary_teacher','ACADEMIC_COUNCIL_INPUT_VIEW','ALLOW','TITULAIRE_CLASSES',true,
     'Secondary Titulaire council and attendance input read',25),
    ('secondary_teacher','ATTENDANCE_ROSTER_VIEW','ALLOW','TITULAIRE_CLASSES',true,
     'Secondary Titulaire class attendance read',30),
    ('secondary_teacher','ATTENDANCE_REOPEN','ALLOW','TITULAIRE_CLASSES',true,
     'Secondary Titulaire reopens finalized class attendance',34)
ON CONFLICT DO NOTHING;
