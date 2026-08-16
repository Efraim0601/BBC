-- BAY-10/BAY-36: secondary report-card fidelity.  This migration is
-- additive: primary templates, legacy promotion rows, and existing snapshots
-- remain valid and are never rewritten.

CREATE TABLE IF NOT EXISTS secondary_competency_model (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    reporting_period_id UUID NOT NULL REFERENCES academic_reporting_period(id) ON DELETE CASCADE,
    class_id UUID NOT NULL REFERENCES school_class(id) ON DELETE CASCADE,
    subject_id UUID NOT NULL REFERENCES subject(id) ON DELETE CASCADE,
    locale VARCHAR(8) NOT NULL DEFAULT 'fr',
    name VARCHAR(180) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','PUBLISHED','RETIRED')),
    source VARCHAR(16) NOT NULL DEFAULT 'MANUAL'
        CHECK (source IN ('MANUAL','IMPORT','SEED')),
    created_by UUID REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_by UUID REFERENCES app_user(id),
    published_at TIMESTAMPTZ,
    UNIQUE (school_id, academic_session_id, reporting_period_id, class_id,
            subject_id, locale, version)
);

CREATE INDEX IF NOT EXISTS idx_secondary_competency_model_scope
    ON secondary_competency_model(school_id, academic_session_id,
                                   reporting_period_id, class_id, subject_id,
                                   locale, status, version DESC);

CREATE TABLE IF NOT EXISTS secondary_competency (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    model_id UUID NOT NULL REFERENCES secondary_competency_model(id) ON DELETE CASCADE,
    code VARCHAR(80) NOT NULL,
    description TEXT NOT NULL,
    max_score NUMERIC(6,2) NOT NULL DEFAULT 20 CHECK (max_score > 0),
    display_order INT NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT true,
    UNIQUE (school_id, model_id, code),
    UNIQUE (school_id, model_id, display_order)
);

CREATE TABLE IF NOT EXISTS secondary_competency_mark (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    model_id UUID NOT NULL REFERENCES secondary_competency_model(id) ON DELETE CASCADE,
    competency_id UUID NOT NULL REFERENCES secondary_competency(id) ON DELETE CASCADE,
    reporting_period_id UUID NOT NULL REFERENCES academic_reporting_period(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    enrollment_id UUID REFERENCES student_enrollment(id),
    teacher_id UUID REFERENCES employee(id),
    mark NUMERIC(6,2),
    value_status VARCHAR(16) NOT NULL DEFAULT 'MISSING'
        CHECK (value_status IN ('SCORED','ABSENT','EXEMPT','MISSING')),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, model_id, competency_id, reporting_period_id, student_id)
);

CREATE INDEX IF NOT EXISTS idx_secondary_competency_mark_student
    ON secondary_competency_mark(school_id, reporting_period_id, student_id);

CREATE TABLE IF NOT EXISTS bulletin_batch_artifact (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    job_id UUID NOT NULL REFERENCES bulletin_batch_job(id) ON DELETE CASCADE,
    artifact_type VARCHAR(24) NOT NULL
        CHECK (artifact_type IN ('HONOR_CERTIFICATE','CLASS_STATISTICS','PV_REGISTER','MANIFEST')),
    file_name VARCHAR(260) NOT NULL,
    file_storage_key VARCHAR(500) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    generated_document_id UUID REFERENCES generated_document(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, job_id, artifact_type, file_name)
);

ALTER TABLE bulletin_version ADD COLUMN IF NOT EXISTS template_id UUID REFERENCES document_template(id);
ALTER TABLE bulletin_version ADD COLUMN IF NOT EXISTS branding_id UUID REFERENCES document_branding_version(id);
ALTER TABLE bulletin_version ADD COLUMN IF NOT EXISTS snapshot_locale VARCHAR(8);
ALTER TABLE bulletin_version ADD COLUMN IF NOT EXISTS evidence_generated_at TIMESTAMPTZ;

-- A published/validated result may transition state, but its printable inputs
-- are append-only.  This closes accidental ORM updates to snapshot JSON/hash.
CREATE OR REPLACE FUNCTION enforce_bulletin_snapshot_immutable()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.snapshot_json IS DISTINCT FROM NEW.snapshot_json
       OR OLD.snapshot_hash IS DISTINCT FROM NEW.snapshot_hash
       OR OLD.template_id IS DISTINCT FROM NEW.template_id
       OR OLD.branding_id IS DISTINCT FROM NEW.branding_id
       OR OLD.snapshot_locale IS DISTINCT FROM NEW.snapshot_locale THEN
        RAISE EXCEPTION 'bulletin snapshot evidence is immutable';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_bulletin_snapshot_immutable ON bulletin_version;
CREATE TRIGGER trg_bulletin_snapshot_immutable
BEFORE UPDATE ON bulletin_version
FOR EACH ROW EXECUTE FUNCTION enforce_bulletin_snapshot_immutable();

-- Make the four secondary families operational.  Existing rows created by
-- V73 keep their IDs and versions, so already-issued snapshots remain valid.
-- V73's compatibility uniqueness key allowed only one product per locale.  A
-- published design is now selected by product and subsystem as well, so keep
-- historical rows but make the variant key explicit before adding the four
-- secondary families.
ALTER TABLE document_template
    DROP CONSTRAINT IF EXISTS document_template_school_id_type_locale_template_version_key;
CREATE UNIQUE INDEX IF NOT EXISTS uq_document_template_variant
    ON document_template(school_id, type, locale, template_version, product, subsystem, reference_family);

UPDATE document_template
   SET template_family = CASE
       WHEN locale='fr' AND product='TERM' THEN 'FR_TERM'
       WHEN locale='fr' AND product='ANNUAL' THEN 'FR_ANNUAL'
       WHEN locale='en' AND product='TERM' THEN 'EN_TERM'
       WHEN locale='en' AND product='ANNUAL' THEN 'EN_ANNUAL'
       ELSE template_family END,
       config_json = CASE
       WHEN product='TERM' AND subsystem='SEC' THEN '{"layout":"secondary-term","paginateCompetencies":true,"a4":true}'::jsonb
       WHEN product='ANNUAL' AND subsystem='SEC' THEN '{"layout":"secondary-annual","periodColumns":["T1","T2","T3"],"a4":true}'::jsonb
       ELSE config_json END
 WHERE reference_family='SECONDARY' AND subsystem='SEC';

-- A deterministic baseline is available even on a school created after V73.
INSERT INTO document_template (
    school_id, type, locale, name, template_version, body_template, active,
    template_family, product, subsystem, status, reference_family, checksum,
    config_json, published_at
)
SELECT s.id, 'REPORT_CARD', x.locale, x.name, 1, x.body, true,
       x.family, x.product, 'SEC', 'PUBLISHED', 'SECONDARY', md5(x.body),
       x.config::jsonb, now()
  FROM school s
 CROSS JOIN (VALUES
    ('fr','FR_TERM','TERM','Francophone secondary term report card','Secondary term report card {{studentName}}','{"layout":"secondary-term","paginateCompetencies":true,"a4":true}'),
    ('fr','FR_ANNUAL','ANNUAL','Francophone secondary annual report card','Secondary annual report card {{studentName}}','{"layout":"secondary-annual","periodColumns":["T1","T2","T3"],"a4":true}'),
    ('en','EN_TERM','TERM','Anglophone secondary term report card','Secondary term report card {{studentName}}','{"layout":"secondary-term","paginateCompetencies":true,"a4":true}'),
    ('en','EN_ANNUAL','ANNUAL','Anglophone secondary annual report card','Secondary annual report card {{studentName}}','{"layout":"secondary-annual","periodColumns":["T1","T2","T3"],"a4":true}')
 ) AS x(locale,family,product,name,body,config)
WHERE NOT EXISTS (
    SELECT 1 FROM document_template t
     WHERE t.school_id=s.id AND t.type='REPORT_CARD' AND t.locale=x.locale
       AND t.template_version=1 AND t.subsystem='SEC'
);

-- English schools may use the French baseline branding as a fallback, but an
-- explicit English version makes the locale and asset choice visible and
-- publishable in Settings.
INSERT INTO document_branding_version (
    school_id, locale, version, status, school_name, school_name_en, motto,
    ministry_text, delegation_text, address, city, country, phone, email,
    website, logo_content_type, logo_bytes, stamp_content_type, stamp_bytes,
    principal_name, principal_title, class_master_title, council_title,
    signatory_manifest, asset_manifest, content_hash, created_by, published_at
)
SELECT fr.school_id, 'en', fr.version, fr.status, fr.school_name,
       COALESCE(fr.school_name_en, fr.school_name), fr.motto, fr.ministry_text,
       fr.delegation_text, fr.address, fr.city, fr.country, fr.phone, fr.email,
       fr.website, fr.logo_content_type, fr.logo_bytes, fr.stamp_content_type,
       fr.stamp_bytes, fr.principal_name, fr.principal_title,
       fr.class_master_title, fr.council_title, fr.signatory_manifest,
       fr.asset_manifest, md5(concat_ws('|', fr.school_name, fr.school_name_en,
       fr.motto, fr.city, fr.country, fr.address, fr.phone, 'en')),
       fr.created_by, fr.published_at
  FROM document_branding_version fr
 WHERE fr.locale='fr' AND fr.status='PUBLISHED'
   AND NOT EXISTS (
       SELECT 1 FROM document_branding_version en
        WHERE en.school_id=fr.school_id AND en.locale='en' AND en.version=fr.version
   );
