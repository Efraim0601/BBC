-- BAY-10/BAY-35: immutable calculation snapshots used by previews, PDFs, PVs,
-- parent visibility, and promotion evidence.

CREATE TABLE bulletin_version (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    reporting_period_id UUID NOT NULL REFERENCES academic_reporting_period(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    enrollment_id UUID REFERENCES student_enrollment(id),
    state VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (state IN ('DRAFT','TEACHER_SUBMITTED','REVIEW','VALIDATED','PUBLISHED','SUPERSEDED','RETURNED')),
    snapshot_json JSONB NOT NULL,
    snapshot_hash VARCHAR(64) NOT NULL,
    average NUMERIC(8,4) NOT NULL DEFAULT 0,
    rank INT,
    class_size INT NOT NULL DEFAULT 0,
    calculation_policy VARCHAR(64) NOT NULL DEFAULT 'DEFAULT',
    template_version VARCHAR(64),
    general_appreciation TEXT,
    supersedes_id UUID REFERENCES bulletin_version(id),
    created_by UUID REFERENCES app_user(id),
    validated_by UUID REFERENCES app_user(id),
    published_by UUID REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    validated_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_bulletin_version_student_period
    ON bulletin_version(school_id, student_id, reporting_period_id, created_at DESC);
CREATE INDEX idx_bulletin_version_publication
    ON bulletin_version(school_id, reporting_period_id, state);
