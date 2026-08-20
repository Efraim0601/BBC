-- BAY-44 / Wave 2: reusable fee-type catalogue.
-- This migration intentionally contains only the fee catalogue. Fee plans,
-- charges, collections, and other later-wave tables must link to these stable
-- identities in their own forward-only migrations.

CREATE TABLE fee_type (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    code                VARCHAR(64) NOT NULL,
    lifecycle           VARCHAR(10) NOT NULL DEFAULT 'DRAFT'
        CHECK (lifecycle IN ('DRAFT','ACTIVE','INACTIVE')),
    current_revision_no INT,
    created_by          UUID REFERENCES app_user(id),
    updated_by          UUID REFERENCES app_user(id),
    activated_by        UUID REFERENCES app_user(id),
    activated_at        TIMESTAMPTZ,
    deactivated_by      UUID REFERENCES app_user(id),
    deactivated_at      TIMESTAMPTZ,
    deactivation_reason VARCHAR(500),
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_fee_type_school_code UNIQUE (school_id, code),
    CONSTRAINT uq_fee_type_school_id UNIQUE (school_id, id),
    CONSTRAINT chk_fee_type_code CHECK (code = upper(code) AND code ~ '^[A-Z0-9_]{1,64}$'),
    CONSTRAINT chk_fee_type_current_revision CHECK (current_revision_no IS NULL OR current_revision_no > 0)
);

CREATE INDEX idx_fee_type_school_lifecycle
    ON fee_type(school_id, lifecycle, code);

CREATE TABLE fee_type_revision (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id               UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    fee_type_id             UUID NOT NULL,
    revision_no             INT NOT NULL CHECK (revision_no > 0),
    revision_status         VARCHAR(10) NOT NULL DEFAULT 'DRAFT'
        CHECK (revision_status IN ('DRAFT','ACTIVE','SUPERSEDED')),
    name_fr                 VARCHAR(160) NOT NULL,
    name_en                 VARCHAR(160) NOT NULL,
    description_fr          VARCHAR(500),
    description_en          VARCHAR(500),
    category                VARCHAR(32) NOT NULL,
    default_amount_minor    BIGINT NOT NULL DEFAULT 0 CHECK (default_amount_minor >= 0),
    default_currency        VARCHAR(3) NOT NULL DEFAULT 'XAF'
        CHECK (default_currency = upper(default_currency) AND length(default_currency) = 3),
    frequency               VARCHAR(12) NOT NULL DEFAULT 'ONCE'
        CHECK (frequency IN ('ONCE','MONTHLY','TERM','ANNUAL')),
    mandatory               BOOLEAN NOT NULL DEFAULT true,
    refundable              BOOLEAN NOT NULL DEFAULT false,
    taxable                 BOOLEAN NOT NULL DEFAULT false,
    tax_basis_points        INT NOT NULL DEFAULT 0 CHECK (tax_basis_points BETWEEN 0 AND 10000),
    receivable_account_id   UUID,
    revenue_account_id      UUID,
    effective_from          DATE,
    effective_to            DATE,
    created_by              UUID REFERENCES app_user(id),
    activated_by            UUID REFERENCES app_user(id),
    activated_at            TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_fee_type_revision_school_id UNIQUE (school_id, id),
    CONSTRAINT uq_fee_type_revision_number UNIQUE (school_id, fee_type_id, revision_no),
    CONSTRAINT fk_fee_type_revision_type
        FOREIGN KEY (school_id, fee_type_id)
        REFERENCES fee_type(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_fee_type_revision_receivable_account
        FOREIGN KEY (school_id, receivable_account_id)
        REFERENCES chart_of_account(school_id, id),
    CONSTRAINT fk_fee_type_revision_revenue_account
        FOREIGN KEY (school_id, revenue_account_id)
        REFERENCES chart_of_account(school_id, id),
    CONSTRAINT chk_fee_type_revision_dates
        CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_from <= effective_to),
    CONSTRAINT chk_fee_type_revision_distinct_accounts
        CHECK (receivable_account_id IS NULL OR revenue_account_id IS NULL
            OR receivable_account_id <> revenue_account_id)
);

CREATE INDEX idx_fee_type_revision_lookup
    ON fee_type_revision(school_id, fee_type_id, revision_no DESC);

CREATE UNIQUE INDEX uq_fee_type_revision_one_active
    ON fee_type_revision(school_id, fee_type_id)
    WHERE revision_status = 'ACTIVE';

-- Keep the stable fee identity while protecting the catalogue from accidental
-- hard deletion. Lifecycle commands are the only supported deactivation path.
CREATE OR REPLACE FUNCTION reject_fee_type_delete() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'fee types are stable identities; deactivate instead';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_fee_type_no_delete
    BEFORE DELETE ON fee_type
    FOR EACH ROW EXECUTE FUNCTION reject_fee_type_delete();

-- Active and superseded revisions are immutable snapshots. The only permitted
-- update is a lifecycle transition (DRAFT -> ACTIVE or ACTIVE -> SUPERSEDED)
-- plus the activation metadata written by the service.
CREATE OR REPLACE FUNCTION reject_fee_type_revision_mutation() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' AND OLD.revision_status IN ('ACTIVE','SUPERSEDED') THEN
        RAISE EXCEPTION 'active or superseded fee type revisions are immutable';
    END IF;
    IF TG_OP = 'UPDATE' AND OLD.revision_status IN ('ACTIVE','SUPERSEDED') THEN
        IF NEW.school_id <> OLD.school_id
           OR NEW.fee_type_id <> OLD.fee_type_id
           OR NEW.revision_no <> OLD.revision_no
           OR NEW.name_fr <> OLD.name_fr
           OR NEW.name_en <> OLD.name_en
           OR NEW.description_fr IS DISTINCT FROM OLD.description_fr
           OR NEW.description_en IS DISTINCT FROM OLD.description_en
           OR NEW.category <> OLD.category
           OR NEW.default_amount_minor <> OLD.default_amount_minor
           OR NEW.default_currency <> OLD.default_currency
           OR NEW.frequency <> OLD.frequency
           OR NEW.mandatory <> OLD.mandatory
           OR NEW.refundable <> OLD.refundable
           OR NEW.taxable <> OLD.taxable
           OR NEW.tax_basis_points <> OLD.tax_basis_points
           OR NEW.receivable_account_id IS DISTINCT FROM OLD.receivable_account_id
           OR NEW.revenue_account_id IS DISTINCT FROM OLD.revenue_account_id
           OR NEW.effective_from IS DISTINCT FROM OLD.effective_from
           OR NEW.effective_to IS DISTINCT FROM OLD.effective_to THEN
            RAISE EXCEPTION 'active or superseded fee type revisions are immutable';
        END IF;
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_fee_type_revision_immutable
    BEFORE UPDATE OR DELETE ON fee_type_revision
    FOR EACH ROW EXECUTE FUNCTION reject_fee_type_revision_mutation();

-- Account compatibility is enforced in the service for friendly field errors
-- and again here for non-HTTP writers. Drafts may omit mappings, but whenever a
-- mapping is supplied it must point to the correct tenant and account nature.
CREATE OR REPLACE FUNCTION validate_fee_type_revision_accounts() RETURNS trigger AS $$
DECLARE
    v_type VARCHAR(16);
    v_active BOOLEAN;
    v_posting BOOLEAN;
    v_currency VARCHAR(3);
BEGIN
    IF NEW.receivable_account_id IS NOT NULL THEN
        SELECT account_type, active, posting_allowed, currency
          INTO v_type, v_active, v_posting, v_currency
          FROM chart_of_account
         WHERE school_id = NEW.school_id AND id = NEW.receivable_account_id;
        IF NOT FOUND OR v_type <> 'ASSET' OR NOT v_active OR NOT v_posting THEN
            RAISE EXCEPTION 'fee receivable account must be an active posting ASSET account';
        END IF;
        IF v_currency IS NOT NULL AND v_currency <> NEW.default_currency THEN
            RAISE EXCEPTION 'fee receivable account currency is incompatible';
        END IF;
    END IF;
    IF NEW.revenue_account_id IS NOT NULL THEN
        SELECT account_type, active, posting_allowed, currency
          INTO v_type, v_active, v_posting, v_currency
          FROM chart_of_account
         WHERE school_id = NEW.school_id AND id = NEW.revenue_account_id;
        IF NOT FOUND OR v_type <> 'REVENUE' OR NOT v_active OR NOT v_posting THEN
            RAISE EXCEPTION 'fee revenue account must be an active posting REVENUE account';
        END IF;
        IF v_currency IS NOT NULL AND v_currency <> NEW.default_currency THEN
            RAISE EXCEPTION 'fee revenue account currency is incompatible';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_fee_type_revision_accounts
    BEFORE INSERT OR UPDATE ON fee_type_revision
    FOR EACH ROW EXECUTE FUNCTION validate_fee_type_revision_accounts();

-- Existing fee_config rows remain untouched. BAY-44 exposes a reviewable preview
-- and only creates catalogue rows after an explicit mapping command; unresolved
-- source items are written to reconciliation_item rather than guessed as Other.
