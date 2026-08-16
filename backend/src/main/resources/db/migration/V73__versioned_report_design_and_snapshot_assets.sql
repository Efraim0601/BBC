-- BAY-10/BAY-36: freeze report-card design inputs alongside the academic
-- snapshot.  Existing generic templates remain compatible; the published
-- branding row gives every school a deterministic fallback reference.

ALTER TABLE document_template ADD COLUMN IF NOT EXISTS checksum VARCHAR(64);
ALTER TABLE document_template ADD COLUMN IF NOT EXISTS reference_family VARCHAR(32) NOT NULL DEFAULT 'GENERIC';
ALTER TABLE document_template ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ;
ALTER TABLE document_template ADD COLUMN IF NOT EXISTS published_by UUID REFERENCES app_user(id);
ALTER TABLE document_template ADD COLUMN IF NOT EXISTS config_json JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE TABLE IF NOT EXISTS document_branding_version (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    locale VARCHAR(8) NOT NULL DEFAULT 'fr',
    version INT NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','PUBLISHED','RETIRED')),
    school_name VARCHAR(180) NOT NULL,
    school_name_en VARCHAR(180),
    motto VARCHAR(240),
    ministry_text VARCHAR(240),
    delegation_text VARCHAR(240),
    address VARCHAR(240),
    city VARCHAR(120),
    country VARCHAR(120),
    phone VARCHAR(80),
    email VARCHAR(180),
    website VARCHAR(180),
    logo_content_type VARCHAR(120),
    logo_bytes BYTEA,
    stamp_content_type VARCHAR(120),
    stamp_bytes BYTEA,
    principal_name VARCHAR(180),
    principal_title VARCHAR(180),
    class_master_title VARCHAR(180),
    council_title VARCHAR(180),
    signatory_manifest JSONB NOT NULL DEFAULT '{}'::jsonb,
    asset_manifest JSONB NOT NULL DEFAULT '{}'::jsonb,
    content_hash VARCHAR(64) NOT NULL,
    created_by UUID REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_by UUID REFERENCES app_user(id),
    published_at TIMESTAMPTZ,
    UNIQUE (school_id, locale, version)
);

CREATE INDEX IF NOT EXISTS idx_document_branding_selection
    ON document_branding_version(school_id, locale, status, version DESC);

-- Seed a versioned, read-only-safe branding baseline from the current school
-- profile.  Operators can publish a richer version later without changing
-- historical snapshots.
INSERT INTO document_branding_version (
    school_id, locale, version, status, school_name, motto, city, country,
    address, phone, email, website, content_hash, published_at
)
SELECT s.id, 'fr', 1, 'PUBLISHED', s.name, s.motto, s.city, s.country,
       s.address, s.phone, s.email, s.website,
       md5(concat_ws('|', s.name, s.motto, s.city, s.country, s.address,
                     s.phone, s.email, s.website)), now()
  FROM school s
ON CONFLICT (school_id, locale, version) DO NOTHING;

-- Fill deterministic checksums for pre-existing templates without rewriting
-- their content or lifecycle.
UPDATE document_template
   SET checksum = md5(body_template)
 WHERE checksum IS NULL;

INSERT INTO document_template (
    school_id, type, locale, name, template_version, body_template, active,
    template_family, product, subsystem, status, reference_family, checksum,
    published_at
)
SELECT s.id, 'REPORT_CARD', x.locale, x.name, 1,
       'Reference-derived report card {{studentName}} / {{className}}', true,
       'REFERENCE', x.product, x.subsystem, 'PUBLISHED', x.reference_family,
       md5('Reference-derived report card {{studentName}} / {{className}}'), now()
  FROM school s
  CROSS JOIN (VALUES
      ('fr','SEQUENCE','PRI','PRIMARY reference report card','PRIMARY'),
      ('fr','TERM','PRI','PRIMARY term report card','PRIMARY'),
      ('fr','ANNUAL','PRI','PRIMARY annual report card','PRIMARY'),
      ('en','SEQUENCE','PRI','PRIMARY sequence report card','PRIMARY'),
      ('en','TERM','PRI','PRIMARY term report card','PRIMARY'),
      ('en','ANNUAL','PRI','PRIMARY annual report card','PRIMARY'),
      ('fr','SEQUENCE','SEC','SECONDARY reference report card','SECONDARY'),
      ('fr','TERM','SEC','SECONDARY term report card','SECONDARY'),
      ('fr','ANNUAL','SEC','SECONDARY annual report card','SECONDARY'),
      ('en','SEQUENCE','SEC','SECONDARY sequence report card','SECONDARY'),
      ('en','TERM','SEC','SECONDARY term report card','SECONDARY'),
      ('en','ANNUAL','SEC','SECONDARY annual report card','SECONDARY')
  ) AS x(locale, product, subsystem, name, reference_family)
ON CONFLICT (school_id, type, locale, template_version) DO NOTHING;
