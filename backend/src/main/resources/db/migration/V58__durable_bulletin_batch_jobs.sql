CREATE TABLE bulletin_batch_job (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id),
    academic_session_id UUID NOT NULL REFERENCES academic_session(id),
    reporting_period_id UUID NOT NULL REFERENCES academic_reporting_period(id),
    class_id UUID NOT NULL REFERENCES school_class(id),
    locale VARCHAR(8) NOT NULL DEFAULT 'fr',
    status VARCHAR(24) NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED','RUNNING','COMPLETED','COMPLETED_ERRORS','FAILED')),
    total_items INT NOT NULL DEFAULT 0,
    processed_items INT NOT NULL DEFAULT 0,
    published_items INT NOT NULL DEFAULT 0,
    blocked_items INT NOT NULL DEFAULT 0,
    error_items INT NOT NULL DEFAULT 0,
    requested_by UUID REFERENCES app_user(id),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    archive_storage_key VARCHAR(500),
    archive_sha256 CHAR(64),
    archive_size_bytes BIGINT,
    last_error TEXT,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_bulletin_batch_job_school_requested
    ON bulletin_batch_job(school_id, requested_at DESC);
CREATE INDEX idx_bulletin_batch_job_scope
    ON bulletin_batch_job(school_id, class_id, reporting_period_id);

CREATE TABLE bulletin_batch_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id),
    job_id UUID NOT NULL REFERENCES bulletin_batch_job(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES student(id),
    status VARCHAR(16) NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED','RUNNING','PUBLISHED','BLOCKED','ERROR')),
    attempts INT NOT NULL DEFAULT 0,
    file_name VARCHAR(260),
    file_storage_key VARCHAR(500),
    sha256 CHAR(64),
    size_bytes BIGINT,
    error TEXT,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (school_id, job_id, student_id)
);

CREATE INDEX idx_bulletin_batch_item_job_status
    ON bulletin_batch_item(school_id, job_id, status, created_at);
