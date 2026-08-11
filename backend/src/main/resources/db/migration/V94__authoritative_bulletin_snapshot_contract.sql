-- BAY-35: authoritative, immutable snapshot contract metadata.
-- All changes are additive and tenant scoped.  The JSON document remains the
-- source of truth for printable/evidence values; these columns make the
-- contract version and generation metadata queryable without parsing JSON.

ALTER TABLE bulletin_version
    ADD COLUMN IF NOT EXISTS snapshot_contract_version INT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS generation_actor_id UUID REFERENCES app_user(id),
    ADD COLUMN IF NOT EXISTS generation_time TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS canonical_snapshot_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS source_version_fingerprint VARCHAR(64);

ALTER TABLE profile_photo_version
    ADD COLUMN IF NOT EXISTS width_px INT,
    ADD COLUMN IF NOT EXISTS height_px INT,
    ADD COLUMN IF NOT EXISTS fallback_decision VARCHAR(32) NOT NULL DEFAULT 'PHOTO';

CREATE INDEX IF NOT EXISTS idx_bulletin_version_contract_scope
    ON bulletin_version(school_id, academic_session_id, reporting_period_id,
                        snapshot_contract_version, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_profile_photo_version_dimensions
    ON profile_photo_version(school_id, owner_type, owner_id, captured_at DESC,
                             width_px, height_px);

-- The existing V74 rows are real frozen assets.  A missing asset is a
-- deliberate fallback, not permission to read profile_photo at render time.
UPDATE profile_photo_version
   SET fallback_decision = 'PHOTO'
 WHERE fallback_decision IS NULL;
