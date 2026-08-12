-- BAY-67: attendance evidence, council recommendations, and row-safe workflow
-- metadata.  This migration is additive and keeps legacy input rows readable.

ALTER TABLE attendance_policy
    ADD COLUMN IF NOT EXISTS policy_version VARCHAR(64) NOT NULL DEFAULT 'attendance-policy-v1';

ALTER TABLE attendance_session
    ADD COLUMN IF NOT EXISTS cancelled BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS duration_source VARCHAR(32),
    ADD COLUMN IF NOT EXISTS duration_policy_version VARCHAR(64);

UPDATE attendance_session a
   SET cancelled = COALESCE(e.cancelled, false),
       duration_source = CASE WHEN a.model = 'DAILY' THEN 'SCHOOL_CALENDAR_DAY' ELSE 'TIMETABLE_PERIOD' END,
       duration_policy_version = COALESCE((
           SELECT p.policy_version
             FROM school_class c
             LEFT JOIN attendance_policy p ON p.school_id = a.school_id AND p.level = lower(c.level)
            WHERE c.id = a.school_class_id
            LIMIT 1
       ), 'attendance-policy-v1')
  FROM expected_school_session e
 WHERE e.id = a.expected_session_id
   AND (a.duration_source IS NULL OR a.duration_policy_version IS NULL);

ALTER TABLE attendance_period_adjustment
    ADD COLUMN IF NOT EXISTS submitted_by UUID REFERENCES app_user(id),
    ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS returned_by UUID REFERENCES app_user(id),
    ADD COLUMN IF NOT EXISTS return_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS corrects_adjustment_id UUID REFERENCES attendance_period_adjustment(id),
    ADD COLUMN IF NOT EXISTS correction_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS correction_evidence_reference VARCHAR(240),
    ADD COLUMN IF NOT EXISTS locked_by_publication_id UUID REFERENCES bulletin_version(id),
    ADD COLUMN IF NOT EXISTS locked_at TIMESTAMPTZ;

ALTER TABLE attendance_period_adjustment
    DROP CONSTRAINT IF EXISTS attendance_period_adjustment_status_check;
ALTER TABLE attendance_period_adjustment
    ADD CONSTRAINT attendance_period_adjustment_status_check
    CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','RETURNED','REJECTED','LOCKED_BY_PUBLICATION'));

ALTER TABLE student_period_conduct
    ADD COLUMN IF NOT EXISTS submitted_by UUID REFERENCES app_user(id),
    ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS returned_by UUID REFERENCES app_user(id),
    ADD COLUMN IF NOT EXISTS return_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS override_by UUID REFERENCES app_user(id),
    ADD COLUMN IF NOT EXISTS override_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS corrects_conduct_id UUID REFERENCES student_period_conduct(id),
    ADD COLUMN IF NOT EXISTS correction_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS correction_evidence_reference VARCHAR(240),
    ADD COLUMN IF NOT EXISTS locked_by_publication_id UUID REFERENCES bulletin_version(id),
    ADD COLUMN IF NOT EXISTS locked_at TIMESTAMPTZ;

ALTER TABLE student_period_conduct
    DROP CONSTRAINT IF EXISTS student_period_conduct_status_check;
ALTER TABLE student_period_conduct
    ADD CONSTRAINT student_period_conduct_status_check
    CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','RETURNED','LOCKED','LOCKED_BY_PUBLICATION'));

CREATE TABLE IF NOT EXISTS attendance_period_adjustment_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    adjustment_id UUID NOT NULL REFERENCES attendance_period_adjustment(id) ON DELETE CASCADE,
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    actor_user_id UUID REFERENCES app_user(id),
    actor_username VARCHAR(64),
    reason VARCHAR(500),
    evidence_reference VARCHAR(240),
    source_version BIGINT NOT NULL DEFAULT 0,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_attendance_adjustment_history
    ON attendance_period_adjustment_history(school_id, adjustment_id, occurred_at DESC);

CREATE TABLE IF NOT EXISTS student_period_conduct_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    conduct_id UUID NOT NULL REFERENCES student_period_conduct(id) ON DELETE CASCADE,
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    actor_user_id UUID REFERENCES app_user(id),
    actor_username VARCHAR(64),
    reason VARCHAR(500),
    override_reason VARCHAR(500),
    source_version BIGINT NOT NULL DEFAULT 0,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_student_period_conduct_history
    ON student_period_conduct_history(school_id, conduct_id, occurred_at DESC);

CREATE OR REPLACE FUNCTION reject_bay67_history_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'BAY-67 workflow history is append-only';
END;
$$;

DROP TRIGGER IF EXISTS trg_attendance_adjustment_history_immutable
    ON attendance_period_adjustment_history;
CREATE TRIGGER trg_attendance_adjustment_history_immutable
BEFORE UPDATE OR DELETE ON attendance_period_adjustment_history
FOR EACH ROW EXECUTE FUNCTION reject_bay67_history_mutation();

DROP TRIGGER IF EXISTS trg_student_period_conduct_history_immutable
    ON student_period_conduct_history;
CREATE TRIGGER trg_student_period_conduct_history_immutable
BEFORE UPDATE OR DELETE ON student_period_conduct_history
FOR EACH ROW EXECUTE FUNCTION reject_bay67_history_mutation();

CREATE TABLE IF NOT EXISTS conduct_recommendation_policy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    policy_version VARCHAR(64) NOT NULL DEFAULT 'conduct-policy-v1',
    absence_warning_percent NUMERIC(5,2) NOT NULL DEFAULT 20.00 CHECK (absence_warning_percent BETWEEN 0 AND 100),
    late_warning_minutes INT NOT NULL DEFAULT 60 CHECK (late_warning_minutes >= 0),
    honor_max_absence_percent NUMERIC(5,2) NOT NULL DEFAULT 5.00 CHECK (honor_max_absence_percent BETWEEN 0 AND 100),
    honor_max_late_minutes INT NOT NULL DEFAULT 15 CHECK (honor_max_late_minutes >= 0),
    require_decision_code BOOLEAN NOT NULL DEFAULT true,
    work_blame_absence_percent NUMERIC(5,2) NOT NULL DEFAULT 40.00 CHECK (work_blame_absence_percent BETWEEN 0 AND 100),
    discipline_warning_count INT NOT NULL DEFAULT 1 CHECK (discipline_warning_count >= 0),
    discipline_blame_count INT NOT NULL DEFAULT 2 CHECK (discipline_blame_count >= 0),
    award_min_coverage_percent NUMERIC(5,2) NOT NULL DEFAULT 80.00 CHECK (award_min_coverage_percent BETWEEN 0 AND 100),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id)
);

INSERT INTO conduct_recommendation_policy (school_id)
SELECT id FROM school
ON CONFLICT (school_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS student_period_conduct_recommendation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    reporting_period_id UUID NOT NULL REFERENCES academic_reporting_period(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    policy_version VARCHAR(64) NOT NULL,
    work_warning BOOLEAN NOT NULL DEFAULT false,
    work_blame BOOLEAN NOT NULL DEFAULT false,
    conduct_warning BOOLEAN NOT NULL DEFAULT false,
    conduct_blame BOOLEAN NOT NULL DEFAULT false,
    honor_roll BOOLEAN NOT NULL DEFAULT false,
    encouragement BOOLEAN NOT NULL DEFAULT false,
    congratulations BOOLEAN NOT NULL DEFAULT false,
    exclusion_days INT NOT NULL DEFAULT 0 CHECK (exclusion_days >= 0),
    recommendation_reason TEXT,
    source_fingerprint VARCHAR(64),
    version BIGINT NOT NULL DEFAULT 0,
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    calculated_by UUID REFERENCES app_user(id),
    UNIQUE (school_id, reporting_period_id, student_id)
);
CREATE INDEX IF NOT EXISTS idx_conduct_recommendation_period
    ON student_period_conduct_recommendation(school_id, reporting_period_id, student_id);

INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT s.id, r.role_code, a.action_code, true
FROM school s
CROSS JOIN (VALUES ('principal'),('prefect')) r(role_code)
CROSS JOIN (VALUES
    ('ATTENDANCE_ADJUSTMENT_VIEW'),('ATTENDANCE_ADJUSTMENT_EDIT'),
    ('ATTENDANCE_ADJUSTMENT_REVIEW'),('COUNCIL_INPUT_VIEW'),
    ('COUNCIL_INPUT_EDIT'),('COUNCIL_INPUT_REVIEW'),('COUNCIL_OVERRIDE')
) a(action_code)
ON CONFLICT (school_id, role_code, action_code) DO UPDATE SET allowed=true;

INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT s.id, r.role_code, a.action_code, true
FROM school s
CROSS JOIN (VALUES ('form_teacher'),('teacher')) r(role_code)
CROSS JOIN (VALUES ('ATTENDANCE_ADJUSTMENT_VIEW'),('ATTENDANCE_ADJUSTMENT_EDIT'),('COUNCIL_INPUT_VIEW'),('COUNCIL_INPUT_EDIT')) a(action_code)
ON CONFLICT (school_id, role_code, action_code) DO UPDATE SET allowed=true;
