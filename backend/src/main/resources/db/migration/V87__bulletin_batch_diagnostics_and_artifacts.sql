-- Batch report-card diagnostics and recovery contract.
-- This migration is additive. Existing jobs remain readable through the
-- compatibility mapping in ReportCardBatchJobService; no historical rows or
-- stored artifacts are deleted.

ALTER TABLE bulletin_batch_job
    ADD COLUMN IF NOT EXISTS policy VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED_ONLY';
ALTER TABLE bulletin_batch_job
    ADD COLUMN IF NOT EXISTS scope_fingerprint CHAR(64);
ALTER TABLE bulletin_batch_job
    ADD COLUMN IF NOT EXISTS diagnostic_storage_key VARCHAR(500);
ALTER TABLE bulletin_batch_job
    ADD COLUMN IF NOT EXISTS diagnostic_sha256 CHAR(64);
ALTER TABLE bulletin_batch_job
    ADD COLUMN IF NOT EXISTS diagnostic_size_bytes BIGINT;

ALTER TABLE bulletin_batch_item
    ADD COLUMN IF NOT EXISTS result_code VARCHAR(64);
ALTER TABLE bulletin_batch_item
    ADD COLUMN IF NOT EXISTS result_details JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE bulletin_batch_item
    ADD COLUMN IF NOT EXISTS snapshot_published_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_bulletin_batch_job_scope_fingerprint
    ON bulletin_batch_job(school_id, class_id, reporting_period_id, scope_fingerprint);
CREATE INDEX IF NOT EXISTS idx_bulletin_batch_item_result_code
    ON bulletin_batch_item(school_id, job_id, result_code);

-- Preserve the old text for audit/history while giving legacy blocker rows a
-- stable read contract. The service may refine this to the current exact
-- lifecycle state without rewriting the historical item.
UPDATE bulletin_batch_item
   SET result_code = 'REPORT_NOT_PUBLISHED_LEGACY',
       result_details = jsonb_build_object(
           'legacy', true,
           'legacyError', error,
           'retryableNow', false
       )
 WHERE result_code IS NULL
   AND status = 'BLOCKED'
   AND lower(trim(error)) = 'no validated or published snapshot';
