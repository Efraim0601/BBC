-- BAY-35: normalized source-version index for formula drill-down and diffs.
-- The payload remains complete and immutable; this table is a tenant-scoped
-- query/index surface, never a second calculation source.

CREATE TABLE IF NOT EXISTS bulletin_snapshot_source_version (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    bulletin_version_id UUID NOT NULL REFERENCES bulletin_version(id) ON DELETE CASCADE,
    source_type VARCHAR(40) NOT NULL,
    source_id UUID,
    source_version BIGINT,
    source_hash VARCHAR(64),
    source_label VARCHAR(160),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, bulletin_version_id, source_type, source_id, source_version)
);

CREATE INDEX IF NOT EXISTS idx_bulletin_snapshot_source_lookup
    ON bulletin_snapshot_source_version(school_id, bulletin_version_id, source_type, source_id);

CREATE INDEX IF NOT EXISTS idx_bulletin_snapshot_source_hash
    ON bulletin_snapshot_source_version(school_id, source_hash);

-- Bulk indexers use this clause when replaying a version after a transient
-- worker failure; duplicate evidence is intentionally harmless.
-- ON CONFLICT DO NOTHING

CREATE OR REPLACE FUNCTION reject_bulletin_snapshot_source_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP <> 'INSERT' THEN
        RAISE EXCEPTION 'bulletin snapshot source evidence is immutable';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_bulletin_snapshot_source_immutable
    ON bulletin_snapshot_source_version;
CREATE TRIGGER trg_bulletin_snapshot_source_immutable
BEFORE UPDATE OR DELETE ON bulletin_snapshot_source_version
FOR EACH ROW EXECUTE FUNCTION reject_bulletin_snapshot_source_mutation();
