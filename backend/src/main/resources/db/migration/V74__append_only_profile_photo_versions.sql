-- BAY-10/BAY-36: keep the exact profile image asset referenced by an
-- immutable bulletin snapshot.  Replacing the current profile photo must not
-- change a later re-render of an already issued result.

CREATE TABLE IF NOT EXISTS profile_photo_version (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    owner_type VARCHAR(16) NOT NULL,
    owner_id UUID NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    bytes BYTEA NOT NULL,
    byte_size INT NOT NULL CHECK (byte_size > 0),
    sha256 VARCHAR(64) NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, owner_type, owner_id, sha256)
);

CREATE INDEX IF NOT EXISTS idx_profile_photo_version_owner
    ON profile_photo_version(school_id, owner_type, owner_id, captured_at DESC);

INSERT INTO profile_photo_version (
    school_id, owner_type, owner_id, content_type, bytes, byte_size, sha256, captured_at
)
SELECT school_id, owner_type, owner_id, content_type, bytes, byte_size,
       encode(digest(bytes, 'sha256'), 'hex'), updated_at
  FROM profile_photo
ON CONFLICT (school_id, owner_type, owner_id, sha256) DO NOTHING;
