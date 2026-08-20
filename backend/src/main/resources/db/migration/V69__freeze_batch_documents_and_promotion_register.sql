-- BAY-10/BAY-11 audit completion after V68.
-- Preserve the exact bulletin/document evidence used by a batch and retain a
-- durable promotion register.  Applied migrations remain immutable.

ALTER TABLE bulletin_batch_job
    DROP CONSTRAINT IF EXISTS bulletin_batch_job_status_check;
ALTER TABLE bulletin_batch_job
    ADD CONSTRAINT bulletin_batch_job_status_check
    CHECK (status IN ('QUEUED','RUNNING','COMPLETED','COMPLETED_ERRORS','FAILED','CANCELLED'));
ALTER TABLE bulletin_batch_job ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ;
ALTER TABLE bulletin_batch_job ADD COLUMN IF NOT EXISTS cancelled_by UUID REFERENCES app_user(id);
ALTER TABLE bulletin_batch_job ADD COLUMN IF NOT EXISTS cancel_reason VARCHAR(500);

ALTER TABLE bulletin_batch_item ADD COLUMN IF NOT EXISTS snapshot_id UUID REFERENCES bulletin_version(id);
ALTER TABLE bulletin_batch_item ADD COLUMN IF NOT EXISTS snapshot_version BIGINT;
ALTER TABLE bulletin_batch_item ADD COLUMN IF NOT EXISTS snapshot_hash CHAR(64);
ALTER TABLE bulletin_batch_item ADD COLUMN IF NOT EXISTS generated_document_id UUID REFERENCES generated_document(id);
CREATE INDEX IF NOT EXISTS idx_bulletin_batch_item_snapshot
    ON bulletin_batch_item(school_id, snapshot_id, generated_document_id);

CREATE TABLE IF NOT EXISTS promotion_register (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    batch_id UUID NOT NULL REFERENCES promotion_batch(id) ON DELETE RESTRICT,
    manifest JSONB NOT NULL,
    sha256 CHAR(64) NOT NULL,
    created_by UUID REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, batch_id)
);
CREATE INDEX IF NOT EXISTS idx_promotion_register_school_created
    ON promotion_register(school_id, created_at DESC);
