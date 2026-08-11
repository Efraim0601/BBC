-- BAY-66: capture the trimester-window authorization made when a batch job is created.
-- This is additive and intentionally does not reintroduce per-action windows.
ALTER TABLE bulletin_batch_job
    ADD COLUMN IF NOT EXISTS window_authorization JSONB NOT NULL DEFAULT '{}'::jsonb;

COMMENT ON COLUMN bulletin_batch_job.window_authorization IS
    'Creation-time academic trimester window decision; permits an authorized job to finish after closure.';
