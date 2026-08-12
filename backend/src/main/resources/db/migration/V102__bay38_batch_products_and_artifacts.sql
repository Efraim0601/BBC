-- BAY-38: additive product-aware batch scope and durable artifact ledger.
-- Existing single-period jobs are backfilled as one product and remain readable.

ALTER TABLE bulletin_batch_job
    ADD COLUMN IF NOT EXISTS product_set JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS product_fingerprint CHAR(64);

ALTER TABLE bulletin_batch_item
    ADD COLUMN IF NOT EXISTS reporting_period_id UUID REFERENCES academic_reporting_period(id),
    ADD COLUMN IF NOT EXISTS reporting_period_code VARCHAR(32),
    ADD COLUMN IF NOT EXISTS reporting_period_label VARCHAR(255),
    ADD COLUMN IF NOT EXISTS product_code VARCHAR(16);

UPDATE bulletin_batch_item i
   SET reporting_period_id = j.reporting_period_id,
       reporting_period_code = p.code,
       reporting_period_label = p.label,
       product_code = CASE WHEN p.period_type = 'ANNUAL_RESULT' THEN 'ANNUAL'
                           WHEN p.code = 'T3_RESULT' THEN 'T3'
                           WHEN p.period_type = 'TERM_RESULT' THEN 'TERM'
                           ELSE 'SEQUENCE' END
  FROM bulletin_batch_job j
  JOIN academic_reporting_period p ON p.id = j.reporting_period_id AND p.school_id = j.school_id
 WHERE i.job_id = j.id
   AND (i.reporting_period_id IS NULL OR i.product_code IS NULL);

UPDATE bulletin_batch_job j
   SET product_set = jsonb_build_array(jsonb_build_object(
       'reportingPeriodId', j.reporting_period_id,
       'reportingPeriodCode', p.code,
       'reportingPeriodLabel', p.label,
       'product', CASE WHEN p.period_type = 'ANNUAL_RESULT' THEN 'ANNUAL'
                       WHEN p.code = 'T3_RESULT' THEN 'T3'
                       WHEN p.period_type = 'TERM_RESULT' THEN 'TERM'
                       ELSE 'SEQUENCE' END)),
       product_fingerprint = j.scope_fingerprint
  FROM academic_reporting_period p
 WHERE p.id = j.reporting_period_id
   AND (j.product_set = '[]'::jsonb OR j.product_set IS NULL);

ALTER TABLE bulletin_batch_item
    ALTER COLUMN reporting_period_id SET NOT NULL,
    ALTER COLUMN reporting_period_code SET NOT NULL,
    ALTER COLUMN product_code SET NOT NULL;

ALTER TABLE bulletin_batch_item
    DROP CONSTRAINT IF EXISTS bulletin_batch_item_school_id_job_id_student_id_key;
ALTER TABLE bulletin_batch_item
    ADD CONSTRAINT bulletin_batch_item_scope_product_key
    UNIQUE (school_id, job_id, student_id, reporting_period_id);

ALTER TABLE bulletin_batch_job
    DROP CONSTRAINT IF EXISTS bulletin_batch_job_status_check;
ALTER TABLE bulletin_batch_job
    ADD CONSTRAINT bulletin_batch_job_status_check
    CHECK (status IN ('QUEUED','RUNNING','COMPLETED','COMPLETED_ERRORS','FAILED','CANCELLED'));

CREATE TABLE IF NOT EXISTS bulletin_batch_artifact (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    job_id UUID NOT NULL REFERENCES bulletin_batch_job(id) ON DELETE CASCADE,
    item_id UUID REFERENCES bulletin_batch_item(id) ON DELETE CASCADE,
    artifact_type VARCHAR(24) NOT NULL
        CHECK (artifact_type IN ('ARCHIVE','MANIFEST','PV','DIAGNOSTIC','DOCUMENT')),
    file_name VARCHAR(260) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, job_id, artifact_type, file_name)
);

CREATE INDEX IF NOT EXISTS idx_bulletin_batch_artifact_job
    ON bulletin_batch_artifact(school_id, job_id, artifact_type, created_at);
