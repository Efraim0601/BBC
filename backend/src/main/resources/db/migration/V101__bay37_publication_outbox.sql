-- BAY-37: transactional, retry-safe parent publication notification outbox.
-- The row is written in the same transaction as publication and visibility;
-- delivery is deliberately separate and idempotent.

CREATE TABLE IF NOT EXISTS bulletin_publication_outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    bulletin_version_id UUID NOT NULL REFERENCES bulletin_version(id) ON DELETE CASCADE,
    parent_visibility_id UUID NOT NULL REFERENCES bulletin_parent_visibility(id) ON DELETE CASCADE,
    event_key VARCHAR(180) NOT NULL,
    event_type VARCHAR(48) NOT NULL DEFAULT 'BULLETIN_PUBLISHED',
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','PROCESSING','SENT','FAILED','CANCELLED')),
    attempt_count INT NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    UNIQUE (school_id, event_key)
);

CREATE INDEX IF NOT EXISTS idx_bulletin_publication_outbox_delivery
    ON bulletin_publication_outbox(school_id, status, available_at, created_at);
CREATE INDEX IF NOT EXISTS idx_bulletin_publication_outbox_bulletin
    ON bulletin_publication_outbox(school_id, bulletin_version_id, created_at DESC);

CREATE OR REPLACE FUNCTION reject_bulletin_outbox_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'bulletin publication outbox is append-only';
    END IF;
    IF OLD.school_id IS DISTINCT FROM NEW.school_id
       OR OLD.bulletin_version_id IS DISTINCT FROM NEW.bulletin_version_id
       OR OLD.parent_visibility_id IS DISTINCT FROM NEW.parent_visibility_id
       OR OLD.event_key IS DISTINCT FROM NEW.event_key
       OR OLD.event_type IS DISTINCT FROM NEW.event_type
       OR OLD.payload IS DISTINCT FROM NEW.payload THEN
        RAISE EXCEPTION 'bulletin publication outbox event is immutable';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_bulletin_outbox_immutable
    ON bulletin_publication_outbox;
CREATE TRIGGER trg_bulletin_outbox_immutable
BEFORE UPDATE OR DELETE ON bulletin_publication_outbox
FOR EACH ROW EXECUTE FUNCTION reject_bulletin_outbox_mutation();
