-- BAY-48 / immutable invoices, receipts and finance document jobs.
-- Forward-only after V63. Financial documents keep their own immutable
-- snapshots and link to the shared generated_document storage ledger.

ALTER TABLE generated_document
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS superseded_by_id UUID,
    ADD COLUMN IF NOT EXISTS superseded_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS superseded_by UUID REFERENCES app_user(id),
    ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS source_event_key VARCHAR(240);

CREATE UNIQUE INDEX IF NOT EXISTS uq_generated_document_school_id_v64
    ON generated_document(school_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_guardian_school_id_v64
    ON guardian(school_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_student_guardian_school_id_v64
    ON student_guardian(school_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_allocation_school_id_v64
    ON payment_allocation(school_id, id);

ALTER TABLE generated_document
    DROP CONSTRAINT IF EXISTS fk_generated_document_superseded_by_v64;
ALTER TABLE generated_document
    ADD CONSTRAINT fk_generated_document_superseded_by_v64
    FOREIGN KEY (school_id, superseded_by_id)
    REFERENCES generated_document(school_id, id) ON DELETE RESTRICT;

CREATE TABLE finance_invoice (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id                   UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    student_id                  UUID NOT NULL,
    student_enrollment_id       UUID NOT NULL,
    academic_session_id         UUID NOT NULL,
    school_class_id_snapshot    UUID,
    class_name_snapshot         VARCHAR(160),
    invoice_number              VARCHAR(80) NOT NULL,
    status                      VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','ISSUED','PARTIALLY_PAID','PAID','VOIDED','SUPERSEDED')),
    issue_date                  DATE NOT NULL,
    due_date                    DATE NOT NULL,
    currency                    VARCHAR(3) NOT NULL DEFAULT 'XAF'
        CHECK (currency = upper(currency) AND length(currency) = 3),
    total_minor                 BIGINT NOT NULL DEFAULT 0 CHECK (total_minor >= 0),
    paid_minor                  BIGINT NOT NULL DEFAULT 0 CHECK (paid_minor >= 0),
    outstanding_minor           BIGINT NOT NULL DEFAULT 0 CHECK (outstanding_minor >= 0),
    recipient_guardian_id       UUID,
    recipient_name              VARCHAR(180) NOT NULL,
    recipient_email             VARCHAR(180),
    recipient_phone             VARCHAR(80),
    recipient_source            VARCHAR(24) NOT NULL DEFAULT 'FINANCE_RESPONSIBLE',
    recipient_warning           VARCHAR(500),
    snapshot_hash               VARCHAR(64) NOT NULL,
    source_event_key            VARCHAR(240) NOT NULL,
    idempotency_key             VARCHAR(160) NOT NULL,
    generated_document_id       UUID,
    superseded_by_invoice_id    UUID,
    superseded_at               TIMESTAMPTZ,
    superseded_by               UUID REFERENCES app_user(id),
    voided_at                   TIMESTAMPTZ,
    voided_by                   UUID REFERENCES app_user(id),
    void_reason                 VARCHAR(500),
    issued_by                   UUID REFERENCES app_user(id),
    issued_at                   TIMESTAMPTZ,
    version                     BIGINT NOT NULL DEFAULT 0,
    created_by                  UUID REFERENCES app_user(id),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_finance_invoice_school_id UNIQUE (school_id, id),
    CONSTRAINT uq_finance_invoice_number UNIQUE (school_id, invoice_number),
    CONSTRAINT uq_finance_invoice_source_event UNIQUE (school_id, source_event_key),
    CONSTRAINT uq_finance_invoice_idempotency UNIQUE (school_id, idempotency_key),
    CONSTRAINT fk_finance_invoice_student
        FOREIGN KEY (school_id, student_id) REFERENCES student(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_invoice_enrollment
        FOREIGN KEY (school_id, student_enrollment_id) REFERENCES student_enrollment(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_invoice_session
        FOREIGN KEY (school_id, academic_session_id) REFERENCES academic_session(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_invoice_class
        FOREIGN KEY (school_id, school_class_id_snapshot) REFERENCES school_class(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_invoice_guardian
        FOREIGN KEY (school_id, recipient_guardian_id) REFERENCES guardian(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_invoice_document
        FOREIGN KEY (school_id, generated_document_id) REFERENCES generated_document(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_invoice_superseded
        FOREIGN KEY (school_id, superseded_by_invoice_id) REFERENCES finance_invoice(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_finance_invoice_dates CHECK (due_date >= issue_date),
    CONSTRAINT chk_finance_invoice_balance CHECK (paid_minor + outstanding_minor <= total_minor)
);

CREATE INDEX idx_finance_invoice_student
    ON finance_invoice(school_id, student_id, issue_date DESC, status);
CREATE INDEX idx_finance_invoice_scope
    ON finance_invoice(school_id, academic_session_id, school_class_id_snapshot, issue_date DESC);
CREATE INDEX idx_finance_invoice_recipient
    ON finance_invoice(school_id, lower(recipient_name), status);

CREATE TABLE finance_invoice_line (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id                   UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    invoice_id                 UUID NOT NULL,
    line_no                    INT NOT NULL CHECK (line_no > 0),
    source_charge_id            UUID NOT NULL,
    source_installment_id      UUID NOT NULL,
    fee_type_code               VARCHAR(64) NOT NULL,
    fee_type_name_fr           VARCHAR(160) NOT NULL,
    fee_type_name_en           VARCHAR(160) NOT NULL,
    description_fr              VARCHAR(240) NOT NULL,
    description_en              VARCHAR(240) NOT NULL,
    due_date                    DATE NOT NULL,
    amount_minor                BIGINT NOT NULL CHECK (amount_minor >= 0),
    paid_minor                  BIGINT NOT NULL DEFAULT 0 CHECK (paid_minor >= 0),
    outstanding_minor           BIGINT NOT NULL DEFAULT 0 CHECK (outstanding_minor >= 0),
    currency                    VARCHAR(3) NOT NULL DEFAULT 'XAF'
        CHECK (currency = upper(currency) AND length(currency) = 3),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_finance_invoice_line_school_id UNIQUE (school_id, id),
    CONSTRAINT uq_finance_invoice_line_no UNIQUE (school_id, invoice_id, line_no),
    CONSTRAINT fk_finance_invoice_line_invoice
        FOREIGN KEY (school_id, invoice_id) REFERENCES finance_invoice(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_invoice_line_charge
        FOREIGN KEY (school_id, source_charge_id) REFERENCES student_charge(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_invoice_line_installment
        FOREIGN KEY (school_id, source_installment_id) REFERENCES charge_installment(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_finance_invoice_line_balance CHECK (paid_minor + outstanding_minor <= amount_minor)
);

CREATE INDEX idx_finance_invoice_line_source
    ON finance_invoice_line(school_id, source_charge_id, source_installment_id);

CREATE TABLE finance_invoice_batch_job (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id                   UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id         UUID NOT NULL,
    school_class_id             UUID,
    issue_date                  DATE NOT NULL,
    due_date                    DATE NOT NULL,
    status                      VARCHAR(28) NOT NULL DEFAULT 'PREVIEW'
        CHECK (status IN ('PREVIEW','RUNNING','COMPLETED','COMPLETED_WITH_BLOCKERS','FAILED')),
    idempotency_key             VARCHAR(160),
    enrollment_count            INT NOT NULL DEFAULT 0,
    issued_count                INT NOT NULL DEFAULT 0,
    already_issued_count        INT NOT NULL DEFAULT 0,
    blocked_count               INT NOT NULL DEFAULT 0,
    failed_count                INT NOT NULL DEFAULT 0,
    total_amount_minor          BIGINT NOT NULL DEFAULT 0,
    currency                    VARCHAR(3) NOT NULL DEFAULT 'XAF',
    requested_by                UUID REFERENCES app_user(id),
    started_at                  TIMESTAMPTZ,
    completed_at                TIMESTAMPTZ,
    last_error                  VARCHAR(1000),
    version                     BIGINT NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_finance_invoice_batch_school_id UNIQUE (school_id, id),
    CONSTRAINT fk_finance_invoice_batch_session
        FOREIGN KEY (school_id, academic_session_id) REFERENCES academic_session(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_invoice_batch_class
        FOREIGN KEY (school_id, school_class_id) REFERENCES school_class(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_finance_invoice_batch_dates CHECK (due_date >= issue_date)
);
CREATE UNIQUE INDEX uq_finance_invoice_batch_idempotency
    ON finance_invoice_batch_job(school_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_finance_invoice_batch_status
    ON finance_invoice_batch_job(school_id, status, created_at DESC);

CREATE TABLE finance_invoice_batch_result (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id                   UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    job_id                     UUID NOT NULL,
    student_enrollment_id      UUID,
    student_id                 UUID,
    finance_invoice_id         UUID,
    result_status              VARCHAR(18) NOT NULL
        CHECK (result_status IN ('ISSUED','ALREADY_ISSUED','BLOCKED','FAILED')),
    amount_minor                BIGINT NOT NULL DEFAULT 0 CHECK (amount_minor >= 0),
    currency                    VARCHAR(3) NOT NULL DEFAULT 'XAF',
    blocker_code                VARCHAR(80),
    blocker_message             VARCHAR(1000),
    action_link                 VARCHAR(240),
    error_detail                VARCHAR(1000),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_finance_invoice_batch_result_school_id UNIQUE (school_id, id),
    CONSTRAINT fk_finance_invoice_batch_result_job
        FOREIGN KEY (school_id, job_id) REFERENCES finance_invoice_batch_job(school_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_finance_invoice_batch_result_enrollment
        FOREIGN KEY (school_id, student_enrollment_id) REFERENCES student_enrollment(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_invoice_batch_result_student
        FOREIGN KEY (school_id, student_id) REFERENCES student(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_invoice_batch_result_invoice
        FOREIGN KEY (school_id, finance_invoice_id) REFERENCES finance_invoice(school_id, id) ON DELETE RESTRICT
);
CREATE INDEX idx_finance_invoice_batch_result_job
    ON finance_invoice_batch_result(school_id, job_id, result_status);

CREATE TABLE finance_receipt (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id                   UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    finance_payment_id          UUID NOT NULL,
    student_id                  UUID NOT NULL,
    student_enrollment_id       UUID NOT NULL,
    academic_session_id         UUID NOT NULL,
    school_class_id_snapshot    UUID,
    class_name_snapshot         VARCHAR(160),
    receipt_number              VARCHAR(80) NOT NULL,
    status                      VARCHAR(24) NOT NULL DEFAULT 'ISSUED'
        CHECK (status IN ('ISSUED','GENERATION_FAILED','VOIDED','SUPERSEDED')),
    issue_date                  DATE NOT NULL,
    currency                    VARCHAR(3) NOT NULL DEFAULT 'XAF'
        CHECK (currency = upper(currency) AND length(currency) = 3),
    amount_minor                BIGINT NOT NULL CHECK (amount_minor > 0),
    allocated_minor             BIGINT NOT NULL DEFAULT 0 CHECK (allocated_minor >= 0),
    credit_minor                BIGINT NOT NULL DEFAULT 0 CHECK (credit_minor >= 0),
    outstanding_minor           BIGINT NOT NULL DEFAULT 0 CHECK (outstanding_minor >= 0),
    channel_code_snapshot       VARCHAR(48) NOT NULL,
    payment_reference           VARCHAR(180),
    cashier_session_id          UUID,
    journal_entry_id            UUID,
    recipient_guardian_id       UUID,
    recipient_name              VARCHAR(180) NOT NULL,
    recipient_email             VARCHAR(180),
    recipient_phone             VARCHAR(80),
    recipient_source            VARCHAR(24) NOT NULL DEFAULT 'FINANCE_RESPONSIBLE',
    recipient_warning           VARCHAR(500),
    snapshot_hash               VARCHAR(64) NOT NULL,
    source_event_key            VARCHAR(240) NOT NULL,
    idempotency_key             VARCHAR(160) NOT NULL,
    generated_document_id       UUID,
    generation_error            VARCHAR(1000),
    issued_by                   UUID REFERENCES app_user(id),
    issued_at                   TIMESTAMPTZ,
    version                     BIGINT NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_finance_receipt_school_id UNIQUE (school_id, id),
    CONSTRAINT uq_finance_receipt_number UNIQUE (school_id, receipt_number),
    CONSTRAINT uq_finance_receipt_payment UNIQUE (school_id, finance_payment_id),
    CONSTRAINT uq_finance_receipt_source_event UNIQUE (school_id, source_event_key),
    CONSTRAINT uq_finance_receipt_idempotency UNIQUE (school_id, idempotency_key),
    CONSTRAINT fk_finance_receipt_payment
        FOREIGN KEY (school_id, finance_payment_id) REFERENCES finance_payment(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_receipt_student
        FOREIGN KEY (school_id, student_id) REFERENCES student(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_receipt_enrollment
        FOREIGN KEY (school_id, student_enrollment_id) REFERENCES student_enrollment(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_receipt_session
        FOREIGN KEY (school_id, academic_session_id) REFERENCES academic_session(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_receipt_class
        FOREIGN KEY (school_id, school_class_id_snapshot) REFERENCES school_class(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_receipt_cashier
        FOREIGN KEY (school_id, cashier_session_id) REFERENCES cashier_session(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_receipt_guardian
        FOREIGN KEY (school_id, recipient_guardian_id) REFERENCES guardian(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_receipt_document
        FOREIGN KEY (school_id, generated_document_id) REFERENCES generated_document(school_id, id) ON DELETE RESTRICT
);

CREATE INDEX idx_finance_receipt_student
    ON finance_receipt(school_id, student_id, issue_date DESC, status);
CREATE INDEX idx_finance_receipt_payment
    ON finance_receipt(school_id, finance_payment_id);

CREATE TABLE finance_receipt_line (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id                   UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    receipt_id                 UUID NOT NULL,
    allocation_id              UUID NOT NULL,
    source_charge_id           UUID NOT NULL,
    source_installment_id      UUID NOT NULL,
    fee_type_code              VARCHAR(64) NOT NULL,
    fee_type_name_fr           VARCHAR(160) NOT NULL,
    fee_type_name_en           VARCHAR(160) NOT NULL,
    due_date                   DATE NOT NULL,
    allocated_minor            BIGINT NOT NULL CHECK (allocated_minor > 0),
    installment_remaining_minor BIGINT NOT NULL CHECK (installment_remaining_minor >= 0),
    currency                   VARCHAR(3) NOT NULL DEFAULT 'XAF'
        CHECK (currency = upper(currency) AND length(currency) = 3),
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_finance_receipt_line_school_id UNIQUE (school_id, id),
    CONSTRAINT uq_finance_receipt_line_allocation UNIQUE (school_id, allocation_id),
    CONSTRAINT fk_finance_receipt_line_receipt
        FOREIGN KEY (school_id, receipt_id) REFERENCES finance_receipt(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_receipt_line_allocation
        FOREIGN KEY (school_id, allocation_id) REFERENCES payment_allocation(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_receipt_line_charge
        FOREIGN KEY (school_id, source_charge_id) REFERENCES student_charge(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_receipt_line_installment
        FOREIGN KEY (school_id, source_installment_id) REFERENCES charge_installment(school_id, id) ON DELETE RESTRICT
);

CREATE INDEX idx_finance_receipt_line_receipt
    ON finance_receipt_line(school_id, receipt_id);

CREATE OR REPLACE FUNCTION reject_issued_finance_invoice_line_mutation() RETURNS trigger AS $$
DECLARE v_status VARCHAR(20);
BEGIN
    SELECT status INTO v_status FROM finance_invoice
     WHERE school_id = COALESCE(NEW.school_id, OLD.school_id)
       AND id = COALESCE(NEW.invoice_id, OLD.invoice_id);
    IF COALESCE(v_status, 'ISSUED') IN ('ISSUED','PARTIALLY_PAID','PAID','VOIDED','SUPERSEDED') THEN
        RAISE EXCEPTION 'issued invoice snapshots are immutable; use void or supersede';
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_finance_invoice_line_immutable
    BEFORE UPDATE OR DELETE ON finance_invoice_line
    FOR EACH ROW EXECUTE FUNCTION reject_issued_finance_invoice_line_mutation();

CREATE OR REPLACE FUNCTION reject_issued_finance_receipt_mutation() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' OR (TG_OP = 'UPDATE' AND OLD.status IN ('ISSUED','GENERATION_FAILED','VOIDED','SUPERSEDED')
       AND (NEW.finance_payment_id <> OLD.finance_payment_id
            OR NEW.receipt_number <> OLD.receipt_number
            OR NEW.amount_minor <> OLD.amount_minor
            OR NEW.allocated_minor <> OLD.allocated_minor
            OR NEW.credit_minor <> OLD.credit_minor
            OR NEW.snapshot_hash <> OLD.snapshot_hash
            OR NEW.source_event_key <> OLD.source_event_key
            OR NEW.idempotency_key <> OLD.idempotency_key)) THEN
        RAISE EXCEPTION 'receipt snapshots are immutable; use void or supersede';
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_finance_receipt_immutable
    BEFORE UPDATE OR DELETE ON finance_receipt
    FOR EACH ROW EXECUTE FUNCTION reject_issued_finance_receipt_mutation();

INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT s.id, r.code, a.action_code,
       CASE
           WHEN r.code IN ('principal','accountant') THEN true
           WHEN r.code = 'econome' AND a.action_code IN ('FINANCE_DOCUMENT_VIEW','FINANCE_DOCUMENT_GENERATE') THEN true
           ELSE false
       END
FROM school s
JOIN role r ON r.code IN ('principal','econome','accountant')
CROSS JOIN (VALUES
    ('FINANCE_DOCUMENT_VIEW'), ('FINANCE_DOCUMENT_GENERATE'),
    ('FINANCE_DOCUMENT_VOID'), ('FINANCE_DOCUMENT_SUPERSEDE'),
    ('FINANCE_DOCUMENT_BATCH')
) AS a(action_code)
ON CONFLICT (school_id, role_code, action_code)
DO UPDATE SET allowed = EXCLUDED.allowed;
