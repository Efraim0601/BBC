-- BAY-37: explicit, auditable bulletin lifecycle and parent visibility.
--
-- This migration is additive.  Existing bulletin snapshots and generated
-- documents remain readable; already-published versions are registered as
-- parent-visible evidence so the upgrade does not silently hide history.

ALTER TABLE bulletin_version
    ADD COLUMN IF NOT EXISTS publication_product VARCHAR(16),
    ADD COLUMN IF NOT EXISTS publication_locale VARCHAR(8),
    ADD COLUMN IF NOT EXISTS generated_document_id UUID REFERENCES generated_document(id),
    ADD COLUMN IF NOT EXISTS superseded_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS superseded_by UUID REFERENCES bulletin_version(id);

UPDATE bulletin_version v
   SET publication_product = CASE
       WHEN p.period_type = 'ANNUAL_RESULT' THEN 'ANNUAL'
       WHEN p.code = 'T3_RESULT' THEN 'T3'
       WHEN p.period_type = 'TERM_RESULT' THEN 'TERM'
       ELSE 'SEQUENCE'
   END,
       publication_locale = COALESCE(v.snapshot_locale, 'fr')
  FROM academic_reporting_period p
 WHERE p.id = v.reporting_period_id
   AND (v.publication_product IS NULL OR v.publication_locale IS NULL);

ALTER TABLE bulletin_version
    ALTER COLUMN publication_product SET DEFAULT 'SEQUENCE',
    ALTER COLUMN publication_product SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_bulletin_lifecycle_scope
    ON bulletin_version(school_id, student_id, reporting_period_id,
                        publication_product, state, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_bulletin_generated_document
    ON bulletin_version(school_id, generated_document_id);

CREATE TABLE IF NOT EXISTS bulletin_lifecycle_transition (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    bulletin_version_id UUID NOT NULL REFERENCES bulletin_version(id) ON DELETE CASCADE,
    source_version_id UUID REFERENCES bulletin_version(id),
    from_state VARCHAR(24),
    to_state VARCHAR(24) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    actor_user_id UUID REFERENCES app_user(id),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reason VARCHAR(1000),
    source_versions JSONB NOT NULL DEFAULT '{}'::jsonb,
    optimistic_version BIGINT NOT NULL DEFAULT 0,
    calculation_snapshot_hash VARCHAR(64),
    template_version VARCHAR(128),
    generated_document_id UUID REFERENCES generated_document(id),
    audit_event_id UUID REFERENCES audit_event(id),
    affected_rows JSONB NOT NULL DEFAULT '[]'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_bulletin_lifecycle_history
    ON bulletin_lifecycle_transition(school_id, bulletin_version_id, occurred_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_bulletin_lifecycle_source
    ON bulletin_lifecycle_transition(school_id, source_version_id, occurred_at DESC);

CREATE OR REPLACE FUNCTION reject_bulletin_lifecycle_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'bulletin lifecycle history is append-only';
END;
$$;

DROP TRIGGER IF EXISTS trg_bulletin_lifecycle_immutable
    ON bulletin_lifecycle_transition;
CREATE TRIGGER trg_bulletin_lifecycle_immutable
BEFORE UPDATE OR DELETE ON bulletin_lifecycle_transition
FOR EACH ROW EXECUTE FUNCTION reject_bulletin_lifecycle_mutation();

CREATE TABLE IF NOT EXISTS bulletin_parent_visibility (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    bulletin_version_id UUID NOT NULL REFERENCES bulletin_version(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    reporting_period_id UUID NOT NULL REFERENCES academic_reporting_period(id) ON DELETE CASCADE,
    publication_product VARCHAR(16) NOT NULL,
    generated_document_id UUID REFERENCES generated_document(id),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE','SUPERSEDED','REVOKED')),
    authorized_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ,
    revoked_reason VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (school_id, bulletin_version_id)
);

CREATE INDEX IF NOT EXISTS idx_bulletin_parent_visibility_scope
    ON bulletin_parent_visibility(school_id, student_id, reporting_period_id,
                                  publication_product, status, authorized_at DESC);
CREATE INDEX IF NOT EXISTS idx_bulletin_parent_visibility_document
    ON bulletin_parent_visibility(school_id, generated_document_id, status);

-- Preserve the parent-visible history that existed before the explicit
-- authorization row was introduced.  New publication always writes the row
-- transactionally with its document and outbox event.
INSERT INTO bulletin_parent_visibility
    (school_id, bulletin_version_id, student_id, reporting_period_id,
     publication_product, generated_document_id, status, authorized_at)
SELECT v.school_id, v.id, v.student_id, v.reporting_period_id,
       v.publication_product,
       d.id,
       'ACTIVE',
       COALESCE(v.published_at, v.created_at)
  FROM bulletin_version v
  LEFT JOIN LATERAL (
      SELECT g.id
        FROM generated_document g
       WHERE g.school_id = v.school_id
         AND g.aggregate_type = 'BulletinVersion'
         AND g.aggregate_id = v.id::text
         AND g.status IN ('GENERATED','ISSUED')
       ORDER BY g.issued_at DESC NULLS LAST, g.generated_at DESC
       LIMIT 1
  ) d ON true
 WHERE v.state = 'PUBLISHED'
   AND NOT EXISTS (
       SELECT 1 FROM bulletin_parent_visibility x
        WHERE x.school_id = v.school_id AND x.bulletin_version_id = v.id
   );

UPDATE bulletin_version v
   SET generated_document_id = x.generated_document_id
  FROM bulletin_parent_visibility x
 WHERE x.school_id = v.school_id
   AND x.bulletin_version_id = v.id
   AND v.generated_document_id IS NULL
   AND x.generated_document_id IS NOT NULL;
