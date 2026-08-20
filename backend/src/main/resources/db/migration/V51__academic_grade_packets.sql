-- BAY-10/BAY-34: class + subject grade-entry packets.
-- A packet is the auditable workflow boundary for one teacher's roster.
CREATE TABLE academic_grade_packet (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    reporting_period_id UUID NOT NULL REFERENCES academic_reporting_period(id) ON DELETE CASCADE,
    class_id UUID NOT NULL REFERENCES school_class(id) ON DELETE CASCADE,
    subject_code VARCHAR(32) NOT NULL,
    teacher_id UUID REFERENCES employee(id),
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','SUBMITTED','RETURNED','ACCEPTED','LOCKED')),
    submitted_by UUID REFERENCES app_user(id),
    submitted_at TIMESTAMPTZ,
    reviewed_by UUID REFERENCES app_user(id),
    reviewed_at TIMESTAMPTZ,
    review_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, reporting_period_id, class_id, subject_code)
);

CREATE INDEX idx_academic_grade_packet_class_period
    ON academic_grade_packet(school_id, academic_session_id, reporting_period_id, class_id);

