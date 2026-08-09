-- BAY-10: retain the reason and actor for the publication decision.
ALTER TABLE bulletin_version
    ADD COLUMN IF NOT EXISTS publication_reason TEXT;

CREATE INDEX IF NOT EXISTS idx_bulletin_version_parent_visibility
    ON bulletin_version(school_id, student_id, reporting_period_id, state, published_at DESC);
