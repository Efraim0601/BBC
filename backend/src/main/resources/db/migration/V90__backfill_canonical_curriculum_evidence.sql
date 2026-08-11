-- BAY-33 follow-up: attach existing assessment/grade evidence to the
-- published version created by V89. Rows that still cannot be resolved are
-- exported as exceptions for repair; no legacy evidence is discarded.

UPDATE academic_assessment a
   SET curriculum_version_id = x.curriculum_version_id,
       curriculum_subject_id = x.curriculum_subject_id
  FROM (
      SELECT a2.id, v.id AS curriculum_version_id, c.id AS curriculum_subject_id
        FROM academic_assessment a2
        JOIN academic_curriculum_subject c ON c.school_id=a2.school_id
         AND c.academic_session_id=a2.academic_session_id AND c.class_id=a2.class_id
         AND a2.subject_code IS NOT NULL
        JOIN subject s ON s.id=c.subject_id AND upper(s.code)=upper(a2.subject_code)
        JOIN academic_curriculum_version v ON v.id=c.curriculum_version_id AND v.state='PUBLISHED'
       WHERE a2.class_id IS NOT NULL
  ) x
 WHERE a.id=x.id AND a.curriculum_version_id IS NULL;

UPDATE academic_grade g
   SET curriculum_version_id = a.curriculum_version_id,
       curriculum_subject_id = a.curriculum_subject_id
  FROM academic_assessment a
 WHERE a.id=g.assessment_id
   AND a.school_id=g.school_id
   AND a.curriculum_version_id IS NOT NULL
   AND g.curriculum_version_id IS NULL;

INSERT INTO legacy_grade_migration_exception
    (school_id,source_table,source_id,reason_code,candidates,source_payload)
SELECT g.school_id,'academic_grade',g.id,'ACADEMIC_GRADE_CANONICAL_MAPPING_REQUIRED',
       '[]'::jsonb,
       jsonb_build_object('studentId',g.student_id,'assessmentId',g.assessment_id,
                          'reportingPeriodId',g.reporting_period_id,'subjectCode',g.subject_code)
  FROM academic_grade g
 WHERE g.curriculum_version_id IS NULL
 ON CONFLICT (school_id,source_table,source_id) DO NOTHING;
