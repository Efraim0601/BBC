-- BAY-10: an explicit correction creates a new draft snapshot and keeps the
-- validated/published version immutable until the replacement is published.
ALTER TABLE bulletin_version
    ADD COLUMN IF NOT EXISTS corrects_bulletin_version_id UUID REFERENCES bulletin_version(id),
    ADD COLUMN IF NOT EXISTS correction_reason TEXT,
    ADD COLUMN IF NOT EXISTS correction_requested_by UUID REFERENCES app_user(id),
    ADD COLUMN IF NOT EXISTS correction_requested_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_bulletin_version_correction
    ON bulletin_version(school_id, corrects_bulletin_version_id, state);
