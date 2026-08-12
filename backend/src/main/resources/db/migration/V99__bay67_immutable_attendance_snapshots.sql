-- BAY-67: immutable official attendance evidence consumed by report cards.
-- Annual evidence is assembled from the latest official T1/T2/T3 rows; raw
-- roll calls are never rewritten by this aggregate.

CREATE TABLE IF NOT EXISTS attendance_official_snapshot (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    reporting_period_id UUID NOT NULL REFERENCES academic_reporting_period(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    enrollment_id UUID REFERENCES student_enrollment(id),
    class_id UUID REFERENCES school_class(id),
    period_type VARCHAR(20) NOT NULL,
    snapshot_version BIGINT NOT NULL DEFAULT 1,
    bulletin_version_id UUID REFERENCES bulletin_version(id),
    supersedes_snapshot_id UUID REFERENCES attendance_official_snapshot(id),
    source_fingerprint VARCHAR(64) NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    expected_session_count INT NOT NULL DEFAULT 0,
    expected_hours NUMERIC(14,6) NOT NULL DEFAULT 0,
    finalized_session_count INT NOT NULL DEFAULT 0,
    finalized_hours NUMERIC(14,6) NOT NULL DEFAULT 0,
    coverage_percent NUMERIC(8,4) NOT NULL DEFAULT 0,
    present_count INT NOT NULL DEFAULT 0,
    absent_count INT NOT NULL DEFAULT 0,
    excused_count INT NOT NULL DEFAULT 0,
    late_count INT NOT NULL DEFAULT 0,
    total_absence_minutes NUMERIC(14,4) NOT NULL DEFAULT 0,
    justified_absence_minutes NUMERIC(14,4) NOT NULL DEFAULT 0,
    unjustified_absence_minutes NUMERIC(14,4) NOT NULL DEFAULT 0,
    late_minutes NUMERIC(14,4) NOT NULL DEFAULT 0,
    exclusion_days INT NOT NULL DEFAULT 0,
    source_roll_call_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    source_snapshot_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    missing_sessions JSONB NOT NULL DEFAULT '[]'::jsonb,
    approved_adjustments JSONB NOT NULL DEFAULT '[]'::jsonb,
    raw_values JSONB NOT NULL DEFAULT '{}'::jsonb,
    display_values JSONB NOT NULL DEFAULT '{}'::jsonb,
    blockers JSONB NOT NULL DEFAULT '[]'::jsonb,
    warnings JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_by UUID REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, reporting_period_id, student_id, snapshot_version)
);

CREATE INDEX IF NOT EXISTS idx_attendance_official_snapshot_latest
    ON attendance_official_snapshot(school_id, academic_session_id, reporting_period_id,
                                    student_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_attendance_official_snapshot_source
    ON attendance_official_snapshot(school_id, source_fingerprint);

CREATE OR REPLACE FUNCTION reject_attendance_official_snapshot_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'official attendance snapshots are immutable; create a correction version';
END;
$$;

DROP TRIGGER IF EXISTS trg_attendance_official_snapshot_immutable
    ON attendance_official_snapshot;
CREATE TRIGGER trg_attendance_official_snapshot_immutable
BEFORE UPDATE OR DELETE ON attendance_official_snapshot
FOR EACH ROW EXECUTE FUNCTION reject_attendance_official_snapshot_mutation();
