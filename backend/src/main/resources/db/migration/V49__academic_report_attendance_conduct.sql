-- BAY-10/BAY-67: audited report-period adjustments and conduct/council inputs.

CREATE TABLE attendance_period_adjustment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    reporting_period_id UUID NOT NULL REFERENCES academic_reporting_period(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    justified_absence_hours NUMERIC(8,2) NOT NULL DEFAULT 0 CHECK (justified_absence_hours >= 0),
    unjustified_absence_hours NUMERIC(8,2) NOT NULL DEFAULT 0 CHECK (unjustified_absence_hours >= 0),
    late_minutes INT NOT NULL DEFAULT 0 CHECK (late_minutes >= 0),
    reason VARCHAR(500) NOT NULL,
    evidence_reference VARCHAR(240),
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED')),
    created_by UUID REFERENCES app_user(id),
    reviewed_by UUID REFERENCES app_user(id),
    reviewed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_attendance_adjustment_student_period ON attendance_period_adjustment(school_id, student_id, reporting_period_id, status);

CREATE TABLE student_period_conduct (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    reporting_period_id UUID NOT NULL REFERENCES academic_reporting_period(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    work_warning BOOLEAN NOT NULL DEFAULT false,
    work_blame BOOLEAN NOT NULL DEFAULT false,
    conduct_warning BOOLEAN NOT NULL DEFAULT false,
    conduct_blame BOOLEAN NOT NULL DEFAULT false,
    honor_roll BOOLEAN NOT NULL DEFAULT false,
    encouragement BOOLEAN NOT NULL DEFAULT false,
    congratulations BOOLEAN NOT NULL DEFAULT false,
    exclusion_days INT NOT NULL DEFAULT 0 CHECK (exclusion_days >= 0),
    decision_code VARCHAR(64),
    council_observation TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','RETURNED','LOCKED')),
    created_by UUID REFERENCES app_user(id),
    reviewed_by UUID REFERENCES app_user(id),
    reviewed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, student_id, reporting_period_id)
);
