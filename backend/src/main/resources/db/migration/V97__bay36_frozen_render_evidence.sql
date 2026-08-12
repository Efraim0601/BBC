-- BAY-36: persist the exact design/asset versions used by an official PDF.
-- All changes are additive and tenant scoped.  Snapshot JSON remains the
-- authoritative payload; these columns make the frozen evidence queryable.

ALTER TABLE bulletin_version
    ADD COLUMN IF NOT EXISTS template_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS template_config_json JSONB,
    ADD COLUMN IF NOT EXISTS branding_version INT,
    ADD COLUMN IF NOT EXISTS branding_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS resolved_asset_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS render_contract_version INT NOT NULL DEFAULT 1;

ALTER TABLE generated_document
    ADD COLUMN IF NOT EXISTS template_version INT,
    ADD COLUMN IF NOT EXISTS template_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS branding_id UUID REFERENCES document_branding_version(id),
    ADD COLUMN IF NOT EXISTS branding_version INT,
    ADD COLUMN IF NOT EXISTS branding_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS resolved_asset_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS snapshot_hash VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_generated_document_render_evidence
    ON generated_document(school_id, aggregate_type, aggregate_id,
                          template_hash, branding_hash, resolved_asset_hash);

-- Extend the BAY-35 snapshot immutability boundary to the render evidence
-- added here.  State transitions remain legal; printable inputs do not.
CREATE OR REPLACE FUNCTION enforce_bulletin_snapshot_immutable()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.snapshot_json IS DISTINCT FROM NEW.snapshot_json
       OR OLD.snapshot_hash IS DISTINCT FROM NEW.snapshot_hash
       OR OLD.template_id IS DISTINCT FROM NEW.template_id
       OR OLD.template_hash IS DISTINCT FROM NEW.template_hash
       OR OLD.template_config_json IS DISTINCT FROM NEW.template_config_json
       OR OLD.branding_id IS DISTINCT FROM NEW.branding_id
       OR OLD.branding_version IS DISTINCT FROM NEW.branding_version
       OR OLD.branding_hash IS DISTINCT FROM NEW.branding_hash
       OR OLD.resolved_asset_hash IS DISTINCT FROM NEW.resolved_asset_hash
       OR OLD.snapshot_locale IS DISTINCT FROM NEW.snapshot_locale
       OR OLD.render_contract_version IS DISTINCT FROM NEW.render_contract_version THEN
        RAISE EXCEPTION 'bulletin snapshot evidence is immutable';
    END IF;
    RETURN NEW;
END;
$$;

-- Published design rows are append-only content.  Status changes are allowed
-- so the existing retirement workflow remains compatible; content replacement
-- or deletion is rejected at the database boundary.
CREATE OR REPLACE FUNCTION reject_published_document_template_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status = 'PUBLISHED' THEN
            RAISE EXCEPTION 'published document template is immutable';
        END IF;
        RETURN OLD;
    END IF;
    IF TG_OP = 'UPDATE' AND OLD.status = 'PUBLISHED' AND (
        OLD.school_id IS DISTINCT FROM NEW.school_id OR
        OLD.type IS DISTINCT FROM NEW.type OR
        OLD.locale IS DISTINCT FROM NEW.locale OR
        OLD.name IS DISTINCT FROM NEW.name OR
        OLD.template_version IS DISTINCT FROM NEW.template_version OR
        OLD.body_template IS DISTINCT FROM NEW.body_template OR
        OLD.template_family IS DISTINCT FROM NEW.template_family OR
        OLD.product IS DISTINCT FROM NEW.product OR
        OLD.subsystem IS DISTINCT FROM NEW.subsystem OR
        OLD.reference_family IS DISTINCT FROM NEW.reference_family OR
        OLD.checksum IS DISTINCT FROM NEW.checksum OR
        OLD.config_json IS DISTINCT FROM NEW.config_json OR
        OLD.standard_key IS DISTINCT FROM NEW.standard_key OR
        OLD.effective_from IS DISTINCT FROM NEW.effective_from OR
        OLD.effective_to IS DISTINCT FROM NEW.effective_to
    ) THEN
        RAISE EXCEPTION 'published document template is immutable';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_published_document_template_immutable ON document_template;
CREATE TRIGGER trg_published_document_template_immutable
BEFORE UPDATE OR DELETE ON document_template
FOR EACH ROW EXECUTE FUNCTION reject_published_document_template_mutation();

CREATE OR REPLACE FUNCTION reject_published_document_branding_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status = 'PUBLISHED' THEN
            RAISE EXCEPTION 'published document branding is immutable';
        END IF;
        RETURN OLD;
    END IF;
    IF TG_OP = 'UPDATE' AND OLD.status = 'PUBLISHED' AND (
        OLD.school_id IS DISTINCT FROM NEW.school_id OR
        OLD.locale IS DISTINCT FROM NEW.locale OR
        OLD.version IS DISTINCT FROM NEW.version OR
        OLD.school_name IS DISTINCT FROM NEW.school_name OR
        OLD.school_name_en IS DISTINCT FROM NEW.school_name_en OR
        OLD.motto IS DISTINCT FROM NEW.motto OR
        OLD.ministry_text IS DISTINCT FROM NEW.ministry_text OR
        OLD.delegation_text IS DISTINCT FROM NEW.delegation_text OR
        OLD.address IS DISTINCT FROM NEW.address OR
        OLD.city IS DISTINCT FROM NEW.city OR
        OLD.country IS DISTINCT FROM NEW.country OR
        OLD.phone IS DISTINCT FROM NEW.phone OR
        OLD.email IS DISTINCT FROM NEW.email OR
        OLD.website IS DISTINCT FROM NEW.website OR
        OLD.logo_content_type IS DISTINCT FROM NEW.logo_content_type OR
        OLD.logo_bytes IS DISTINCT FROM NEW.logo_bytes OR
        OLD.stamp_content_type IS DISTINCT FROM NEW.stamp_content_type OR
        OLD.stamp_bytes IS DISTINCT FROM NEW.stamp_bytes OR
        OLD.principal_name IS DISTINCT FROM NEW.principal_name OR
        OLD.principal_title IS DISTINCT FROM NEW.principal_title OR
        OLD.class_master_title IS DISTINCT FROM NEW.class_master_title OR
        OLD.council_title IS DISTINCT FROM NEW.council_title OR
        OLD.signatory_manifest IS DISTINCT FROM NEW.signatory_manifest OR
        OLD.asset_manifest IS DISTINCT FROM NEW.asset_manifest OR
        OLD.content_hash IS DISTINCT FROM NEW.content_hash
    ) THEN
        RAISE EXCEPTION 'published document branding is immutable';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_published_document_branding_immutable ON document_branding_version;
CREATE TRIGGER trg_published_document_branding_immutable
BEFORE UPDATE OR DELETE ON document_branding_version
FOR EACH ROW EXECUTE FUNCTION reject_published_document_branding_mutation();
