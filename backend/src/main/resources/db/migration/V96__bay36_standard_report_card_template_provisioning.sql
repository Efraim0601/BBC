-- BAY-36: runtime-provisioned standard report-card families.
--
-- This migration deliberately does not INSERT ... SELECT from school.  A
-- restored/production school may be created after migrations have run; the
-- application provisioning service installs the standard rows at runtime.
-- Existing template rows are preserved exactly as found.

ALTER TABLE document_template
    ADD COLUMN IF NOT EXISTS standard_key VARCHAR(80);

CREATE UNIQUE INDEX IF NOT EXISTS uq_document_template_standard_version
    ON document_template(school_id, standard_key, template_version)
    WHERE standard_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_document_template_effective_selection
    ON document_template(school_id, type, locale, product, subsystem, status,
                         effective_from, effective_to, template_version DESC);

-- Legacy rows that predate the runtime installer remain valid.  Give rows
-- without an explicit effective date a stable historical start date without
-- touching their body, status, checksum, or version identity.
UPDATE document_template
   SET effective_from = COALESCE(effective_from, created_at::date, CURRENT_DATE)
 WHERE effective_from IS NULL;
