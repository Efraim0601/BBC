-- Keep the demo's four secondary report-card selections usable even when the
-- broader demo progression seeds have already moved legacy students to later
-- sessions.  This is additive and only creates a missing active roster row.
DO $$
DECLARE
    v_school_id UUID := '11111111-1111-1111-1111-111111111111';
    v_session_id UUID;
BEGIN
    SELECT id INTO v_session_id FROM academic_session
     WHERE academic_session.school_id=v_school_id AND code='2025-2026' LIMIT 1;
    IF v_session_id IS NULL THEN RETURN; END IF;

    INSERT INTO student_enrollment
      (school_id,student_id,academic_session_id,school_class_id,class_name_snapshot,
       level_snapshot,subsystem_snapshot,status,enrolled_on,source)
    SELECT s.school_id,s.id,v_session_id,s.class_id,c.name,c.level,c.subsystem,'ACTIVE',
           GREATEST('2025-09-01'::date,s.created_at::date),'REPORT_CARD_DEMO'
      FROM student s JOIN school_class c ON c.id=s.class_id
     WHERE s.school_id=v_school_id AND s.matricule IN ('BBC-1001','BBC-1002','BBC-1003')
       AND c.level='secondary'
    ON CONFLICT DO NOTHING;

    -- V77 created the model/version matrix before the missing roster was
    -- repaired.  Add its marks now, retaining the exact model/competency
    -- versions and canonical enrollment IDs in the evidence.
    INSERT INTO secondary_competency_mark
      (school_id,model_id,competency_id,reporting_period_id,student_id,enrollment_id,teacher_id,mark,value_status)
    SELECT v_school_id,m.id,c.id,m.reporting_period_id,e.student_id,e.id,
           (SELECT employee_id FROM academic_class_subject_teacher a
             WHERE a.school_id=v_school_id AND a.academic_session_id=v_session_id
               AND a.class_id=m.class_id AND a.subject_id=m.subject_id
               AND a.role='RESPONSIBLE' AND a.active LIMIT 1),
           CASE WHEN e.student_id='cccccccc-0000-0000-0000-000000000002'::uuid THEN 16 ELSE 14 END,
           'SCORED'
      FROM secondary_competency_model m
      JOIN secondary_competency c ON c.model_id=m.id AND c.active
      JOIN student_enrollment e ON e.school_id=v_school_id AND e.academic_session_id=v_session_id
       AND e.school_class_id=m.class_id AND e.status='ACTIVE'
     WHERE m.school_id=v_school_id AND m.academic_session_id=v_session_id
    ON CONFLICT (school_id,model_id,competency_id,reporting_period_id,student_id) DO NOTHING;

    INSERT INTO student_period_conduct
      (school_id,academic_session_id,reporting_period_id,student_id,honor_roll,encouragement,
       congratulations,decision_code,council_observation,status)
    SELECT v_school_id,v_session_id,p.id,e.student_id,
           e.student_id='cccccccc-0000-0000-0000-000000000001'::uuid,
           e.student_id='cccccccc-0000-0000-0000-000000000002'::uuid,
           e.student_id='cccccccc-0000-0000-0000-000000000001'::uuid,
           CASE WHEN p.code='ANNUAL' THEN 'PROMOTED' ELSE 'MEETS_EXPECTATIONS' END,
           'Positive participation in the bilingual demo cohort.','APPROVED'
      FROM academic_reporting_period p JOIN student_enrollment e
        ON e.school_id=v_school_id AND e.academic_session_id=v_session_id AND e.status='ACTIVE'
     WHERE p.school_id=v_school_id AND p.academic_session_id=v_session_id AND p.code IN ('T1_RESULT','ANNUAL')
    ON CONFLICT (school_id,student_id,reporting_period_id) DO NOTHING;
END $$;
