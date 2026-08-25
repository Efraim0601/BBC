ALTER TABLE subject_result_comment
    ADD COLUMN IF NOT EXISTS programme_class_id UUID REFERENCES school_class(id);

UPDATE subject_result_comment comment
   SET programme_class_id=enrollment.school_class_id
  FROM student_enrollment enrollment
 WHERE comment.programme_class_id IS NULL
   AND enrollment.id=comment.enrollment_id;

UPDATE subject_result_comment comment
   SET programme_class_id=enrollment.school_class_id
  FROM student_enrollment enrollment
 WHERE comment.programme_class_id IS NULL
   AND enrollment.school_id=comment.school_id
   AND enrollment.academic_session_id=comment.academic_session_id
   AND enrollment.student_id=comment.student_id
   AND enrollment.status='ACTIVE';

ALTER TABLE subject_result_comment
    DROP CONSTRAINT IF EXISTS subject_result_comment_school_id_student_id_reporting_perio_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_subject_result_comment_programme
    ON subject_result_comment(school_id,student_id,reporting_period_id,programme_class_id,subject_code)
    WHERE programme_class_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_subject_result_comment_legacy
    ON subject_result_comment(school_id,student_id,reporting_period_id,subject_code)
    WHERE programme_class_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_subject_result_comment_programme
    ON subject_result_comment(school_id,reporting_period_id,programme_class_id,subject_code);
