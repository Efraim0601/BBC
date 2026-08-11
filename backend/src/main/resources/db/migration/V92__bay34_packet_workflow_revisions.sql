-- BAY-34: explicit packet review lifecycle, auditable transitions, and
-- revision-safe correction packets. This migration is additive and keeps the
-- original packet/grade/comment evidence attached to revision 1.

ALTER TABLE academic_grade_packet
    DROP CONSTRAINT IF EXISTS academic_grade_packet_status_check;
ALTER TABLE academic_grade_packet
    DROP CONSTRAINT IF EXISTS academic_grade_packet_status_check1;
ALTER TABLE academic_grade_packet
    ADD COLUMN IF NOT EXISTS claimed_by UUID REFERENCES app_user(id),
    ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS returned_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS revision_number INT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS supersedes_packet_id UUID REFERENCES academic_grade_packet(id);
UPDATE academic_grade_packet SET status='IN_REVIEW' WHERE status IN ('REVIEW','IN REVIEW');
ALTER TABLE academic_grade_packet
    ADD CONSTRAINT academic_grade_packet_status_check
    CHECK (status IN ('DRAFT','SUBMITTED','IN_REVIEW','RETURNED','ACCEPTED','LOCKED'));
ALTER TABLE academic_grade_packet
    DROP CONSTRAINT IF EXISTS academic_grade_packet_school_id_reporting_period_id_class_id_subject_code_key;
DO $$
DECLARE c RECORD;
BEGIN
    -- PostgreSQL truncates long generated constraint names. Resolve the
    -- legacy natural-key constraint by its columns so correction revisions
    -- are not blocked on production-shaped databases.
    FOR c IN SELECT conname FROM pg_constraint
              WHERE conrelid='academic_grade_packet'::regclass AND contype='u'
                AND pg_get_constraintdef(oid) LIKE '%school_id%reporting_period_id%class_id%subject_code%'
    LOOP EXECUTE format('ALTER TABLE academic_grade_packet DROP CONSTRAINT %I', c.conname); END LOOP;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS ux_grade_packet_revision
    ON academic_grade_packet(school_id, reporting_period_id, class_id, subject_code, revision_number);
CREATE INDEX IF NOT EXISTS idx_grade_packet_queue
    ON academic_grade_packet(school_id, status, reporting_period_id, class_id, subject_code, revision_number DESC);

ALTER TABLE academic_grade
    ADD COLUMN IF NOT EXISTS packet_id UUID REFERENCES academic_grade_packet(id),
    ADD COLUMN IF NOT EXISTS packet_revision INT NOT NULL DEFAULT 1;
ALTER TABLE subject_result_comment
    ADD COLUMN IF NOT EXISTS packet_id UUID REFERENCES academic_grade_packet(id),
    ADD COLUMN IF NOT EXISTS packet_revision INT NOT NULL DEFAULT 1;

-- Existing evidence is assigned to the deterministic revision-1 packet for
-- its active enrollment/class. Ambiguous legacy rows remain nullable and are
-- still visible through the compatibility queries.
INSERT INTO academic_grade_packet
    (school_id, academic_session_id, reporting_period_id, class_id, subject_code,
     status, revision_number)
SELECT DISTINCT g.school_id, g.academic_session_id, g.reporting_period_id,
       e.school_class_id, upper(g.subject_code), 'DRAFT', 1
  FROM academic_grade g
  JOIN student_enrollment e ON e.id=g.enrollment_id AND e.school_id=g.school_id
 WHERE e.school_class_id IS NOT NULL
ON CONFLICT (school_id, reporting_period_id, class_id, subject_code, revision_number) DO NOTHING;

UPDATE academic_grade g
   SET packet_id=p.id, packet_revision=p.revision_number
  FROM academic_grade_packet p
  JOIN student_enrollment e ON e.school_class_id=p.class_id
 WHERE g.school_id=p.school_id AND g.reporting_period_id=p.reporting_period_id
   AND g.enrollment_id=e.id AND upper(g.subject_code)=upper(p.subject_code)
   AND g.packet_id IS NULL;
UPDATE subject_result_comment c
   SET packet_id=p.id, packet_revision=p.revision_number
  FROM academic_grade_packet p
  JOIN student_enrollment e ON e.school_class_id=p.class_id
 WHERE c.school_id=p.school_id AND c.reporting_period_id=p.reporting_period_id
   AND c.enrollment_id=e.id AND upper(c.subject_code)=upper(p.subject_code)
   AND c.packet_id IS NULL;

ALTER TABLE academic_grade
    DROP CONSTRAINT IF EXISTS academic_grade_school_id_student_id_assessment_id_subject_code_key;
DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint
              WHERE conrelid='academic_grade'::regclass AND contype='u'
                AND pg_get_constraintdef(oid) LIKE '%school_id%student_id%assessment_id%subject_code%'
    LOOP EXECUTE format('ALTER TABLE academic_grade DROP CONSTRAINT %I', c.conname); END LOOP;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS ux_academic_grade_packet_row
    ON academic_grade(packet_id, student_id, assessment_id, subject_code)
    WHERE packet_id IS NOT NULL;
ALTER TABLE subject_result_comment
    DROP CONSTRAINT IF EXISTS subject_result_comment_school_id_student_id_reporting_period_id_subject_code_key;
DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint
              WHERE conrelid='subject_result_comment'::regclass AND contype='u'
                AND pg_get_constraintdef(oid) LIKE '%school_id%student_id%reporting_period_id%subject_code%'
    LOOP EXECUTE format('ALTER TABLE subject_result_comment DROP CONSTRAINT %I', c.conname); END LOOP;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS ux_subject_result_comment_packet_row
    ON subject_result_comment(packet_id, student_id, reporting_period_id, subject_code)
    WHERE packet_id IS NOT NULL;

ALTER TABLE academic_grade
    DROP CONSTRAINT IF EXISTS academic_grade_workflow_status_check;
ALTER TABLE academic_grade
    ADD CONSTRAINT academic_grade_workflow_status_check
    CHECK (workflow_status IN ('DRAFT','SUBMITTED','IN_REVIEW','RETURNED','ACCEPTED','LOCKED'));
ALTER TABLE subject_result_comment
    DROP CONSTRAINT IF EXISTS subject_result_comment_workflow_status_check;
ALTER TABLE subject_result_comment
    ADD CONSTRAINT subject_result_comment_workflow_status_check
    CHECK (workflow_status IN ('DRAFT','SUBMITTED','IN_REVIEW','RETURNED','ACCEPTED','LOCKED'));

ALTER TABLE academic_grade_packet_transition
    ADD COLUMN IF NOT EXISTS event_type VARCHAR(24) NOT NULL DEFAULT 'STATE_CHANGE',
    ADD COLUMN IF NOT EXISTS affected_rows JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS reviewer_user_id UUID REFERENCES app_user(id),
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_grade_packet_transition_review
    ON academic_grade_packet_transition(school_id, packet_id, event_type, created_at);
