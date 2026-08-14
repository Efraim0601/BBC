-- BAY-46 / Wave 2: student charges, immutable charge snapshots,
-- installment schedules, generation jobs, waivers and ageing read data.
-- This migration is intentionally forward-only. V59, V60 and V61 are
-- applied foundations and must not be rewritten by charge generation.

ALTER TABLE fee_plan_line
    ADD COLUMN IF NOT EXISTS proration_policy VARCHAR(8) NOT NULL DEFAULT 'NONE';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_fee_plan_line_proration_policy'
    ) THEN
        ALTER TABLE fee_plan_line
            ADD CONSTRAINT chk_fee_plan_line_proration_policy
            CHECK (proration_policy IN ('NONE', 'DAILY', 'MONTHLY'));
    END IF;
END $$;

-- Existing legacy tables use a single-column primary key. These composite
-- indexes make every new tenant-scoped foreign key prove school ownership.
CREATE UNIQUE INDEX IF NOT EXISTS uq_student_school_id_v62
    ON student(school_id, id);

CREATE TABLE student_charge (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id                   UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    student_enrollment_id       UUID NOT NULL,
    student_id                  UUID NOT NULL,
    academic_session_id         UUID NOT NULL,
    fee_plan_id                 UUID NOT NULL,
    fee_plan_line_id            UUID NOT NULL,
    fee_type_id                 UUID NOT NULL,
    fee_type_revision_id        UUID NOT NULL,
    fee_plan_version_no         INT NOT NULL CHECK (fee_plan_version_no > 0),
    fee_type_code               VARCHAR(64) NOT NULL,
    fee_type_name_fr            VARCHAR(160) NOT NULL,
    fee_type_name_en            VARCHAR(160) NOT NULL,
    fee_type_category           VARCHAR(32) NOT NULL,
    scope_type                  VARCHAR(8) NOT NULL CHECK (scope_type IN ('LEVEL', 'CLASS')),
    level_snapshot              VARCHAR(32) NOT NULL,
    subsystem_snapshot          VARCHAR(16) NOT NULL,
    school_class_id_snapshot    UUID,
    class_name_snapshot         VARCHAR(160),
    receivable_account_id       UUID NOT NULL,
    revenue_account_id          UUID NOT NULL,
    original_amount_minor       BIGINT NOT NULL CHECK (original_amount_minor >= 0),
    adjusted_amount_minor       BIGINT NOT NULL CHECK (adjusted_amount_minor >= 0),
    currency                    VARCHAR(3) NOT NULL DEFAULT 'XAF'
        CHECK (currency = upper(currency) AND length(currency) = 3),
    charge_date                 DATE NOT NULL,
    proration_policy            VARCHAR(8) NOT NULL DEFAULT 'NONE'
        CHECK (proration_policy IN ('NONE', 'DAILY', 'MONTHLY')),
    proration_formula           VARCHAR(500),
    generation_key              VARCHAR(220) NOT NULL,
    transfer_from_enrollment_id UUID,
    transfer_policy             VARCHAR(24) NOT NULL DEFAULT 'INCREMENTAL_ONLY'
        CHECK (transfer_policy IN ('INCREMENTAL_ONLY', 'FULL_REASSESSMENT')),
    status                      VARCHAR(10) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'POSTED', 'PARTIAL', 'PAID', 'WAIVED', 'REVERSED')),
    paid_minor                  BIGINT NOT NULL DEFAULT 0 CHECK (paid_minor >= 0),
    waived_minor                BIGINT NOT NULL DEFAULT 0 CHECK (waived_minor >= 0),
    outstanding_minor           BIGINT NOT NULL DEFAULT 0 CHECK (outstanding_minor >= 0),
    journal_entry_id            UUID,
    version                     BIGINT NOT NULL DEFAULT 0,
    created_by                  UUID REFERENCES app_user(id),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_student_charge_school_id UNIQUE (school_id, id),
    CONSTRAINT uq_student_charge_generation_key UNIQUE (school_id, generation_key),
    CONSTRAINT fk_student_charge_enrollment
        FOREIGN KEY (school_id, student_enrollment_id)
        REFERENCES student_enrollment(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_charge_student
        FOREIGN KEY (school_id, student_id)
        REFERENCES student(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_charge_session
        FOREIGN KEY (school_id, academic_session_id)
        REFERENCES academic_session(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_charge_plan
        FOREIGN KEY (school_id, fee_plan_id)
        REFERENCES fee_plan(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_charge_plan_line
        FOREIGN KEY (school_id, fee_plan_line_id)
        REFERENCES fee_plan_line(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_charge_fee_type
        FOREIGN KEY (school_id, fee_type_id)
        REFERENCES fee_type(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_charge_fee_revision
        FOREIGN KEY (school_id, fee_type_revision_id)
        REFERENCES fee_type_revision(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_charge_class_snapshot
        FOREIGN KEY (school_id, school_class_id_snapshot)
        REFERENCES school_class(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_charge_receivable
        FOREIGN KEY (school_id, receivable_account_id)
        REFERENCES chart_of_account(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_charge_revenue
        FOREIGN KEY (school_id, revenue_account_id)
        REFERENCES chart_of_account(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_charge_journal
        FOREIGN KEY (school_id, journal_entry_id)
        REFERENCES journal_entry(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_charge_transfer_source
        FOREIGN KEY (school_id, transfer_from_enrollment_id)
        REFERENCES student_enrollment(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_student_charge_amounts
        CHECK (outstanding_minor + waived_minor + paid_minor = adjusted_amount_minor),
    CONSTRAINT chk_student_charge_snapshot_class
        CHECK (scope_type = 'LEVEL' OR school_class_id_snapshot IS NOT NULL)
);

CREATE INDEX idx_student_charge_session_scope
    ON student_charge(school_id, academic_session_id, school_class_id_snapshot, status);
CREATE INDEX idx_student_charge_student
    ON student_charge(school_id, student_id, charge_date, status);
CREATE INDEX idx_student_charge_enrollment
    ON student_charge(school_id, student_enrollment_id, charge_date);
CREATE INDEX idx_student_charge_ageing
    ON student_charge(school_id, status, charge_date, outstanding_minor);

CREATE TABLE charge_installment (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id               UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    charge_id               UUID NOT NULL,
    installment_no          INT NOT NULL CHECK (installment_no > 0),
    label_fr                VARCHAR(160) NOT NULL,
    label_en                VARCHAR(160) NOT NULL,
    due_date                DATE NOT NULL,
    amount_minor            BIGINT NOT NULL CHECK (amount_minor >= 0),
    paid_minor              BIGINT NOT NULL DEFAULT 0 CHECK (paid_minor >= 0),
    waived_minor            BIGINT NOT NULL DEFAULT 0 CHECK (waived_minor >= 0),
    outstanding_minor       BIGINT NOT NULL DEFAULT 0 CHECK (outstanding_minor >= 0),
    status                  VARCHAR(10) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'PARTIAL', 'PAID', 'WAIVED')),
    generation_key          VARCHAR(240) NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_charge_installment_school_id UNIQUE (school_id, id),
    CONSTRAINT uq_charge_installment_no UNIQUE (school_id, charge_id, installment_no),
    CONSTRAINT uq_charge_installment_generation_key UNIQUE (school_id, generation_key),
    CONSTRAINT fk_charge_installment_charge
        FOREIGN KEY (school_id, charge_id)
        REFERENCES student_charge(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_charge_installment_amounts
        CHECK (outstanding_minor + waived_minor + paid_minor = amount_minor)
);

CREATE INDEX idx_charge_installment_due
    ON charge_installment(school_id, due_date, status, outstanding_minor);
CREATE INDEX idx_charge_installment_charge
    ON charge_installment(school_id, charge_id, installment_no);

CREATE TABLE charge_adjustment (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id               UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    charge_id               UUID NOT NULL,
    installment_id          UUID,
    adjustment_type         VARCHAR(12) NOT NULL
        CHECK (adjustment_type IN ('WAIVER', 'ADJUSTMENT')),
    amount_minor            BIGINT NOT NULL CHECK (amount_minor > 0),
    currency                VARCHAR(3) NOT NULL DEFAULT 'XAF'
        CHECK (currency = upper(currency) AND length(currency) = 3),
    reason                  VARCHAR(1000) NOT NULL,
    evidence_reference      VARCHAR(240),
    contra_account_id       UUID NOT NULL,
    effective_date          DATE NOT NULL,
    status                  VARCHAR(10) NOT NULL DEFAULT 'REQUESTED'
        CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'POSTED')),
    requested_by            UUID REFERENCES app_user(id),
    requested_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_by             UUID REFERENCES app_user(id),
    approved_at             TIMESTAMPTZ,
    decision_reason         VARCHAR(1000),
    journal_entry_id        UUID,
    version                 BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_charge_adjustment_school_id UNIQUE (school_id, id),
    CONSTRAINT fk_charge_adjustment_charge
        FOREIGN KEY (school_id, charge_id)
        REFERENCES student_charge(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_charge_adjustment_installment
        FOREIGN KEY (school_id, installment_id)
        REFERENCES charge_installment(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_charge_adjustment_account
        FOREIGN KEY (school_id, contra_account_id)
        REFERENCES chart_of_account(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_charge_adjustment_journal
        FOREIGN KEY (school_id, journal_entry_id)
        REFERENCES journal_entry(school_id, id) ON DELETE RESTRICT
);

CREATE INDEX idx_charge_adjustment_charge
    ON charge_adjustment(school_id, charge_id, status, effective_date);

CREATE TABLE charge_generation_job (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id               UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id     UUID NOT NULL,
    school_class_id         UUID,
    level                   VARCHAR(32),
    subsystem               VARCHAR(16),
    charge_date             DATE NOT NULL,
    proration_policy        VARCHAR(8) NOT NULL DEFAULT 'NONE'
        CHECK (proration_policy IN ('NONE', 'DAILY', 'MONTHLY')),
    transfer_policy         VARCHAR(24) NOT NULL DEFAULT 'INCREMENTAL_ONLY'
        CHECK (transfer_policy IN ('INCREMENTAL_ONLY', 'FULL_REASSESSMENT')),
    status                  VARCHAR(24) NOT NULL DEFAULT 'PREVIEW'
        CHECK (status IN ('PREVIEW', 'RUNNING', 'COMPLETED', 'COMPLETED_WITH_BLOCKERS', 'FAILED')),
    idempotency_key         VARCHAR(240),
    enrollment_count        INT NOT NULL DEFAULT 0,
    generated_count         INT NOT NULL DEFAULT 0,
    already_exists_count    INT NOT NULL DEFAULT 0,
    blocked_count           INT NOT NULL DEFAULT 0,
    failed_count            INT NOT NULL DEFAULT 0,
    total_amount_minor      BIGINT NOT NULL DEFAULT 0,
    currency                VARCHAR(3) NOT NULL DEFAULT 'XAF',
    requested_by            UUID REFERENCES app_user(id),
    started_at              TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    last_error              VARCHAR(1000),
    version                 BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_charge_generation_job_school_id UNIQUE (school_id, id),
    CONSTRAINT uq_charge_generation_job_idempotency UNIQUE (school_id, idempotency_key),
    CONSTRAINT fk_charge_generation_job_session
        FOREIGN KEY (school_id, academic_session_id)
        REFERENCES academic_session(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_charge_generation_job_class
        FOREIGN KEY (school_id, school_class_id)
        REFERENCES school_class(school_id, id) ON DELETE RESTRICT
);

CREATE INDEX idx_charge_generation_job_school_status
    ON charge_generation_job(school_id, status, created_at DESC);

CREATE TABLE charge_generation_result (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id               UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    job_id                  UUID NOT NULL,
    student_enrollment_id   UUID,
    student_id              UUID,
    fee_plan_id             UUID,
    fee_plan_line_id        UUID,
    student_charge_id       UUID,
    school_class_id         UUID,
    class_name_snapshot     VARCHAR(160),
    result_status           VARCHAR(18) NOT NULL
        CHECK (result_status IN ('GENERATED', 'ALREADY_EXISTS', 'BLOCKED', 'FAILED')),
    amount_minor            BIGINT NOT NULL DEFAULT 0 CHECK (amount_minor >= 0),
    currency                VARCHAR(3) NOT NULL DEFAULT 'XAF',
    blocker_code            VARCHAR(80),
    blocker_message         VARCHAR(1000),
    action_link             VARCHAR(240),
    error_detail            VARCHAR(1000),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_charge_generation_result_school_id UNIQUE (school_id, id),
    CONSTRAINT fk_charge_generation_result_job
        FOREIGN KEY (school_id, job_id)
        REFERENCES charge_generation_job(school_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_charge_generation_result_enrollment
        FOREIGN KEY (school_id, student_enrollment_id)
        REFERENCES student_enrollment(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_charge_generation_result_student
        FOREIGN KEY (school_id, student_id)
        REFERENCES student(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_charge_generation_result_plan
        FOREIGN KEY (school_id, fee_plan_id)
        REFERENCES fee_plan(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_charge_generation_result_line
        FOREIGN KEY (school_id, fee_plan_line_id)
        REFERENCES fee_plan_line(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_charge_generation_result_charge
        FOREIGN KEY (school_id, student_charge_id)
        REFERENCES student_charge(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_charge_generation_result_class
        FOREIGN KEY (school_id, school_class_id)
        REFERENCES school_class(school_id, id) ON DELETE RESTRICT
);

CREATE INDEX idx_charge_generation_result_job
    ON charge_generation_result(school_id, job_id, result_status);

-- A posted charge is an historical plan/fee snapshot. Aggregates may change
-- through future allocation/waiver work, but identity, source, scope and
-- accounting mapping must never be relabelled.
CREATE OR REPLACE FUNCTION reject_student_charge_snapshot_mutation() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' AND OLD.status <> 'DRAFT' THEN
        RAISE EXCEPTION 'posted student charges are immutable; use an adjustment';
    END IF;
    IF TG_OP = 'UPDATE' AND OLD.status <> 'DRAFT' THEN
        IF NEW.school_id <> OLD.school_id
           OR NEW.student_enrollment_id <> OLD.student_enrollment_id
           OR NEW.student_id <> OLD.student_id
           OR NEW.academic_session_id <> OLD.academic_session_id
           OR NEW.fee_plan_id <> OLD.fee_plan_id
           OR NEW.fee_plan_line_id <> OLD.fee_plan_line_id
           OR NEW.fee_type_id <> OLD.fee_type_id
           OR NEW.fee_type_revision_id <> OLD.fee_type_revision_id
           OR NEW.fee_plan_version_no <> OLD.fee_plan_version_no
           OR NEW.fee_type_code <> OLD.fee_type_code
           OR NEW.level_snapshot <> OLD.level_snapshot
           OR NEW.subsystem_snapshot <> OLD.subsystem_snapshot
           OR NEW.school_class_id_snapshot IS DISTINCT FROM OLD.school_class_id_snapshot
           OR NEW.class_name_snapshot IS DISTINCT FROM OLD.class_name_snapshot
           OR NEW.receivable_account_id <> OLD.receivable_account_id
           OR NEW.revenue_account_id <> OLD.revenue_account_id
           OR NEW.original_amount_minor <> OLD.original_amount_minor
           OR NEW.adjusted_amount_minor <> OLD.adjusted_amount_minor
           OR NEW.currency <> OLD.currency
           OR NEW.charge_date <> OLD.charge_date
           OR NEW.generation_key <> OLD.generation_key THEN
            RAISE EXCEPTION 'posted student charge snapshots are immutable; use an adjustment';
        END IF;
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_student_charge_snapshot_immutable
    BEFORE UPDATE OR DELETE ON student_charge
    FOR EACH ROW EXECUTE FUNCTION reject_student_charge_snapshot_mutation();

CREATE OR REPLACE FUNCTION reject_charge_installment_mutation() RETURNS trigger AS $$
DECLARE
    v_charge_status VARCHAR(10);
BEGIN
    SELECT status INTO v_charge_status
      FROM student_charge
     WHERE school_id = COALESCE(NEW.school_id, OLD.school_id)
       AND id = COALESCE(NEW.charge_id, OLD.charge_id);
    IF TG_OP = 'DELETE' AND COALESCE(v_charge_status, 'POSTED') <> 'DRAFT' THEN
        RAISE EXCEPTION 'posted charge installments are immutable; use an adjustment';
    END IF;
    IF TG_OP = 'UPDATE' AND COALESCE(v_charge_status, 'POSTED') <> 'DRAFT'
       AND (NEW.charge_id <> OLD.charge_id
            OR NEW.installment_no <> OLD.installment_no
            OR NEW.label_fr <> OLD.label_fr
            OR NEW.label_en <> OLD.label_en
            OR NEW.due_date <> OLD.due_date
            OR NEW.amount_minor <> OLD.amount_minor
            OR NEW.generation_key <> OLD.generation_key) THEN
        RAISE EXCEPTION 'posted charge installment schedules are immutable';
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_charge_installment_immutable
    BEFORE UPDATE OR DELETE ON charge_installment
    FOR EACH ROW EXECUTE FUNCTION reject_charge_installment_mutation();

CREATE OR REPLACE FUNCTION reject_charge_adjustment_mutation() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' AND OLD.status <> 'REQUESTED' THEN
        RAISE EXCEPTION 'decided charge adjustments are immutable';
    END IF;
    IF TG_OP = 'UPDATE' AND OLD.status IN ('APPROVED', 'REJECTED', 'POSTED') THEN
        RAISE EXCEPTION 'decided charge adjustments are immutable';
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_charge_adjustment_immutable
    BEFORE UPDATE OR DELETE ON charge_adjustment
    FOR EACH ROW EXECUTE FUNCTION reject_charge_adjustment_mutation();
