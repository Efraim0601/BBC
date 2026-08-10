-- Make the additive secondary demo cohort publishable through the complete
-- frozen evidence workflow.  Sequence conduct is intentionally explicit so
-- validation cannot silently bypass the administrative approval gate.
DO $$
DECLARE
    v_school_id UUID := '11111111-1111-1111-1111-111111111111';
    v_session_id UUID;
BEGIN
    SELECT id INTO v_session_id
      FROM academic_session
     WHERE school_id=v_school_id AND code='2025-2026'
     LIMIT 1;
    IF v_session_id IS NULL THEN RETURN; END IF;

    INSERT INTO student_period_conduct
      (school_id,academic_session_id,reporting_period_id,student_id,
       decision_code,council_observation,status)
    SELECT v_school_id,v_session_id,p.id,e.student_id,
           'MEETS_EXPECTATIONS',
           'Sequence evidence reviewed for the bilingual secondary demo cohort.',
           'APPROVED'
      FROM academic_reporting_period p
      JOIN student_enrollment e
        ON e.school_id=v_school_id
       AND e.academic_session_id=v_session_id
       AND e.status='ACTIVE'
     WHERE p.school_id=v_school_id
       AND p.academic_session_id=v_session_id
       AND p.code IN ('S1','S2','S3','S4','S5','S6')
    ON CONFLICT (school_id,student_id,reporting_period_id) DO UPDATE
      SET status='APPROVED',
          decision_code=COALESCE(student_period_conduct.decision_code,'MEETS_EXPECTATIONS'),
          council_observation=COALESCE(student_period_conduct.council_observation,
              'Sequence evidence reviewed for the bilingual secondary demo cohort.'),
          reviewed_at=COALESCE(student_period_conduct.reviewed_at,now()),
          updated_at=now();
END $$;
