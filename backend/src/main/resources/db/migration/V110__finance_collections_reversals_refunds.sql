-- BAY-47 / allocation-aware collections, reversals, refunds, cashier control,
-- provider callback safety and legacy receipt-number preservation.
-- This migration is forward-only. V59-V62 remain unchanged.

-- Composite keys make tenant ownership explicit for references from the new
-- collection tables to legacy academic/payment-channel tables.
CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_channel_school_id_v63
    ON payment_channel(school_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_student_enrollment_school_id_v63
    ON student_enrollment(school_id, id);

ALTER TABLE payment_channel
    ADD COLUMN IF NOT EXISTS debit_account_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_payment_channel_debit_account_v63'
    ) THEN
        ALTER TABLE payment_channel
            ADD CONSTRAINT fk_payment_channel_debit_account_v63
            FOREIGN KEY (school_id, debit_account_id)
            REFERENCES chart_of_account(school_id, id) ON DELETE RESTRICT;
    END IF;
END $$;

UPDATE payment_channel pc
   SET debit_account_id = a.id
  FROM chart_of_account a
 WHERE a.school_id = pc.school_id
   AND a.code = CASE upper(pc.code)
       WHEN 'CASH' THEN '1000'
       WHEN 'OM' THEN '1020'
       WHEN 'MOMO' THEN '1030'
       WHEN 'MPGS' THEN '1040'
       WHEN 'TRANSFER' THEN '1010'
       ELSE NULL
   END
   AND pc.debit_account_id IS NULL;

CREATE TABLE finance_payment (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id                UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    student_id               UUID NOT NULL,
    student_enrollment_id    UUID NOT NULL,
    academic_session_id      UUID NOT NULL,
    payment_channel_id       UUID NOT NULL,
    channel_code_snapshot    VARCHAR(20) NOT NULL,
    amount_minor             BIGINT NOT NULL CHECK (amount_minor > 0),
    currency                 VARCHAR(3) NOT NULL DEFAULT 'XAF'
        CHECK (currency = upper(currency) AND length(currency) = 3),
    payment_date             DATE NOT NULL,
    reference                VARCHAR(180),
    payer_name               VARCHAR(180),
    note                     VARCHAR(1000),
    status                   VARCHAR(20) NOT NULL DEFAULT 'POSTED'
        CHECK (status IN ('DRAFT','POSTED','REVERSED','PARTIALLY_REFUNDED','REFUNDED')),
    receipt_no               VARCHAR(80) NOT NULL,
    legacy_receipt_no        VARCHAR(80),
    cashier_session_id       UUID,
    journal_entry_id         UUID,
    source_event_key         VARCHAR(240) NOT NULL,
    idempotency_key          VARCHAR(160) NOT NULL,
    posted_at                TIMESTAMPTZ,
    posted_by                UUID REFERENCES app_user(id),
    created_by               UUID REFERENCES app_user(id),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                  BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_finance_payment_school_id UNIQUE (school_id, id),
    CONSTRAINT uq_finance_payment_receipt UNIQUE (school_id, receipt_no),
    CONSTRAINT uq_finance_payment_source_event UNIQUE (school_id, source_event_key),
    CONSTRAINT uq_finance_payment_idempotency UNIQUE (school_id, idempotency_key),
    CONSTRAINT fk_finance_payment_student
        FOREIGN KEY (school_id, student_id) REFERENCES student(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_payment_enrollment
        FOREIGN KEY (school_id, student_enrollment_id)
        REFERENCES student_enrollment(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_payment_session
        FOREIGN KEY (school_id, academic_session_id)
        REFERENCES academic_session(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_payment_channel
        FOREIGN KEY (school_id, payment_channel_id)
        REFERENCES payment_channel(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_payment_journal
        FOREIGN KEY (school_id, journal_entry_id)
        REFERENCES journal_entry(school_id, id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_finance_payment_reference_v63
    ON finance_payment(school_id, channel_code_snapshot, reference)
    WHERE reference IS NOT NULL AND btrim(reference) <> '';
CREATE INDEX idx_finance_payment_account
    ON finance_payment(school_id, student_id, payment_date DESC, status);
CREATE INDEX idx_finance_payment_filter
    ON finance_payment(school_id, academic_session_id, payment_date, channel_code_snapshot, status);

CREATE TABLE payment_allocation (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id             UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    payment_id            UUID NOT NULL,
    charge_installment_id UUID NOT NULL,
    student_id            UUID NOT NULL,
    allocated_minor       BIGINT NOT NULL CHECK (allocated_minor > 0),
    currency              VARCHAR(3) NOT NULL DEFAULT 'XAF'
        CHECK (currency = upper(currency) AND length(currency) = 3),
    status                VARCHAR(12) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE','REVERSED','REFUNDED')),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_payment_allocation_school_id UNIQUE (school_id, id),
    CONSTRAINT uq_payment_allocation_installment UNIQUE (school_id, payment_id, charge_installment_id),
    CONSTRAINT fk_payment_allocation_payment
        FOREIGN KEY (school_id, payment_id) REFERENCES finance_payment(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_allocation_installment
        FOREIGN KEY (school_id, charge_installment_id)
        REFERENCES charge_installment(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_allocation_student
        FOREIGN KEY (school_id, student_id) REFERENCES student(school_id, id) ON DELETE RESTRICT
);
CREATE INDEX idx_payment_allocation_installment
    ON payment_allocation(school_id, charge_installment_id, status);
CREATE INDEX idx_payment_allocation_payment
    ON payment_allocation(school_id, payment_id, status);

CREATE TABLE student_credit_ledger (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id             UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    student_id            UUID NOT NULL,
    student_enrollment_id UUID,
    payment_id            UUID,
    payment_allocation_id UUID,
    source_credit_id      UUID,
    entry_type            VARCHAR(16) NOT NULL
        CHECK (entry_type IN ('CREATED','CONSUMED','REFUNDED','REVERSED')),
    amount_minor          BIGINT NOT NULL CHECK (amount_minor > 0),
    currency              VARCHAR(3) NOT NULL DEFAULT 'XAF'
        CHECK (currency = upper(currency) AND length(currency) = 3),
    source_event_key      VARCHAR(240) NOT NULL,
    entry_date            DATE NOT NULL,
    reason                VARCHAR(500),
    created_by             UUID REFERENCES app_user(id),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_student_credit_school_id UNIQUE (school_id, id),
    CONSTRAINT uq_student_credit_source_event UNIQUE (school_id, source_event_key),
    CONSTRAINT fk_student_credit_student
        FOREIGN KEY (school_id, student_id) REFERENCES student(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_credit_enrollment
        FOREIGN KEY (school_id, student_enrollment_id)
        REFERENCES student_enrollment(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_credit_payment
        FOREIGN KEY (school_id, payment_id) REFERENCES finance_payment(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_credit_allocation
        FOREIGN KEY (school_id, payment_allocation_id)
        REFERENCES payment_allocation(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_credit_source
        FOREIGN KEY (school_id, source_credit_id)
        REFERENCES student_credit_ledger(school_id, id) ON DELETE RESTRICT
);
CREATE INDEX idx_student_credit_balance
    ON student_credit_ledger(school_id, student_id, entry_date, entry_type);

CREATE TABLE payment_reversal_request (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id             UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    payment_id            UUID NOT NULL,
    reversal_no           VARCHAR(80),
    status                VARCHAR(12) NOT NULL DEFAULT 'REQUESTED'
        CHECK (status IN ('REQUESTED','APPROVED','REJECTED','POSTED')),
    reason                VARCHAR(1000) NOT NULL,
    requested_by          UUID REFERENCES app_user(id),
    requested_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_by           UUID REFERENCES app_user(id),
    approved_at           TIMESTAMPTZ,
    decision_reason       VARCHAR(1000),
    journal_entry_id      UUID,
    posted_at             TIMESTAMPTZ,
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_payment_reversal_school_id UNIQUE (school_id, id),
    CONSTRAINT fk_payment_reversal_payment
        FOREIGN KEY (school_id, payment_id) REFERENCES finance_payment(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_reversal_journal
        FOREIGN KEY (school_id, journal_entry_id) REFERENCES journal_entry(school_id, id) ON DELETE RESTRICT
);
CREATE UNIQUE INDEX uq_payment_reversal_open_v63
    ON payment_reversal_request(school_id, payment_id)
    WHERE status IN ('REQUESTED','APPROVED','POSTED');

CREATE TABLE refund_request (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id             UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    payment_id            UUID NOT NULL,
    refund_no             VARCHAR(80),
    amount_minor          BIGINT NOT NULL CHECK (amount_minor > 0),
    currency              VARCHAR(3) NOT NULL DEFAULT 'XAF'
        CHECK (currency = upper(currency) AND length(currency) = 3),
    channel_code          VARCHAR(20) NOT NULL,
    reference             VARCHAR(180),
    reason                VARCHAR(1000) NOT NULL,
    status                VARCHAR(12) NOT NULL DEFAULT 'REQUESTED'
        CHECK (status IN ('REQUESTED','APPROVED','REJECTED','POSTED')),
    requested_by          UUID REFERENCES app_user(id),
    requested_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_by           UUID REFERENCES app_user(id),
    approved_at           TIMESTAMPTZ,
    decision_reason       VARCHAR(1000),
    journal_entry_id      UUID,
    posted_at             TIMESTAMPTZ,
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_refund_request_school_id UNIQUE (school_id, id),
    CONSTRAINT fk_refund_request_payment
        FOREIGN KEY (school_id, payment_id) REFERENCES finance_payment(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_refund_request_journal
        FOREIGN KEY (school_id, journal_entry_id) REFERENCES journal_entry(school_id, id) ON DELETE RESTRICT
);
CREATE INDEX idx_refund_request_payment
    ON refund_request(school_id, payment_id, status);

CREATE TABLE refund_transaction (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id             UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    refund_request_id     UUID NOT NULL,
    payment_id            UUID NOT NULL,
    refund_no             VARCHAR(80) NOT NULL,
    amount_minor          BIGINT NOT NULL CHECK (amount_minor > 0),
    currency              VARCHAR(3) NOT NULL DEFAULT 'XAF',
    channel_code          VARCHAR(20) NOT NULL,
    reference             VARCHAR(180),
    journal_entry_id      UUID,
    posted_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    posted_by             UUID REFERENCES app_user(id),
    CONSTRAINT uq_refund_transaction_school_id UNIQUE (school_id, id),
    CONSTRAINT uq_refund_transaction_no UNIQUE (school_id, refund_no),
    CONSTRAINT fk_refund_transaction_request
        FOREIGN KEY (school_id, refund_request_id) REFERENCES refund_request(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_refund_transaction_payment
        FOREIGN KEY (school_id, payment_id) REFERENCES finance_payment(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_refund_transaction_journal
        FOREIGN KEY (school_id, journal_entry_id) REFERENCES journal_entry(school_id, id) ON DELETE RESTRICT
);

CREATE TABLE cashier_session (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id             UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    cashier_user_id       UUID NOT NULL REFERENCES app_user(id),
    status                VARCHAR(8) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN','CLOSED')),
    opened_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at             TIMESTAMPTZ,
    opening_cash_minor    BIGINT NOT NULL DEFAULT 0 CHECK (opening_cash_minor >= 0),
    expected_cash_minor   BIGINT NOT NULL DEFAULT 0 CHECK (expected_cash_minor >= 0),
    declared_cash_minor   BIGINT CHECK (declared_cash_minor >= 0),
    variance_minor        BIGINT,
    close_note            VARCHAR(1000),
    manager_approved_by   UUID REFERENCES app_user(id),
    manager_approved_at   TIMESTAMPTZ,
    version               BIGINT NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_cashier_session_school_id UNIQUE (school_id, id)
);
CREATE UNIQUE INDEX uq_cashier_session_open
    ON cashier_session(school_id, cashier_user_id)
    WHERE status = 'OPEN';
CREATE INDEX idx_cashier_session_status
    ON cashier_session(school_id, status, opened_at DESC);

-- The payment table references this table; it is created before the table
-- definition above in a fresh migration through the deferred FK addition.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_finance_payment_cashier'
    ) THEN
        ALTER TABLE finance_payment
            ADD CONSTRAINT fk_finance_payment_cashier
            FOREIGN KEY (school_id, cashier_session_id)
            REFERENCES cashier_session(school_id, id) ON DELETE RESTRICT;
    END IF;
END $$;

CREATE TABLE provider_transaction (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id             UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    payment_channel_id    UUID NOT NULL,
    finance_payment_id    UUID,
    provider_code         VARCHAR(32) NOT NULL,
    external_reference    VARCHAR(180) NOT NULL,
    amount_minor          BIGINT CHECK (amount_minor IS NULL OR amount_minor > 0),
    currency              VARCHAR(3) NOT NULL DEFAULT 'XAF',
    status                VARCHAR(16) NOT NULL DEFAULT 'RECEIVED'
        CHECK (status IN ('RECEIVED','MATCHED','REJECTED','MANUAL_CONFIRMED')),
    payload_hash          VARCHAR(128),
    received_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    matched_at            TIMESTAMPTZ,
    matched_by            UUID REFERENCES app_user(id),
    rejection_reason      VARCHAR(1000),
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_provider_transaction_school_id UNIQUE (school_id, id),
    CONSTRAINT uq_provider_transaction_reference UNIQUE (school_id, provider_code, external_reference),
    CONSTRAINT fk_provider_transaction_channel
        FOREIGN KEY (school_id, payment_channel_id)
        REFERENCES payment_channel(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_provider_transaction_payment
        FOREIGN KEY (school_id, finance_payment_id)
        REFERENCES finance_payment(school_id, id) ON DELETE RESTRICT
);

CREATE TABLE provider_callback (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id             UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    provider_code         VARCHAR(32) NOT NULL,
    event_id              VARCHAR(180) NOT NULL,
    external_reference    VARCHAR(180),
    payload_hash          VARCHAR(128) NOT NULL,
    payload               JSONB NOT NULL,
    status                VARCHAR(16) NOT NULL DEFAULT 'RECEIVED'
        CHECK (status IN ('RECEIVED','MATCHED','REJECTED','MANUAL_REVIEW')),
    provider_transaction_id UUID,
    received_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at          TIMESTAMPTZ,
    processed_by          UUID REFERENCES app_user(id),
    message               VARCHAR(1000),
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_provider_callback_school_id UNIQUE (school_id, id),
    CONSTRAINT uq_provider_callback_event UNIQUE (school_id, provider_code, event_id),
    CONSTRAINT fk_provider_callback_transaction
        FOREIGN KEY (school_id, provider_transaction_id)
        REFERENCES provider_transaction(school_id, id) ON DELETE RESTRICT
);
CREATE INDEX idx_provider_callback_review
    ON provider_callback(school_id, status, received_at DESC);

CREATE OR REPLACE FUNCTION reject_posted_finance_payment_mutation() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' AND OLD.status <> 'DRAFT' THEN
        RAISE EXCEPTION 'posted payments are immutable; use reversal or refund';
    END IF;
    IF TG_OP = 'UPDATE' AND OLD.status <> 'DRAFT' THEN
        IF NEW.school_id <> OLD.school_id
           OR NEW.student_id <> OLD.student_id
           OR NEW.student_enrollment_id <> OLD.student_enrollment_id
           OR NEW.academic_session_id <> OLD.academic_session_id
           OR NEW.payment_channel_id <> OLD.payment_channel_id
           OR NEW.channel_code_snapshot <> OLD.channel_code_snapshot
           OR NEW.amount_minor <> OLD.amount_minor
           OR NEW.currency <> OLD.currency
           OR NEW.payment_date <> OLD.payment_date
           OR NEW.reference IS DISTINCT FROM OLD.reference
           OR NEW.receipt_no <> OLD.receipt_no
           OR NEW.source_event_key <> OLD.source_event_key
           OR NEW.idempotency_key <> OLD.idempotency_key THEN
            RAISE EXCEPTION 'posted payment snapshots are immutable; use reversal or refund';
        END IF;
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_finance_payment_immutable
    BEFORE UPDATE OR DELETE ON finance_payment
    FOR EACH ROW EXECUTE FUNCTION reject_posted_finance_payment_mutation();

CREATE OR REPLACE FUNCTION reject_decided_collection_mutation() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'collection corrections are immutable; use a new reversal or refund';
    END IF;
    IF TG_OP = 'UPDATE' AND OLD.status IN ('APPROVED','REJECTED','POSTED') THEN
        RAISE EXCEPTION 'decided collection corrections are immutable';
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_payment_reversal_immutable
    BEFORE UPDATE OR DELETE ON payment_reversal_request
    FOR EACH ROW EXECUTE FUNCTION reject_decided_collection_mutation();
CREATE TRIGGER trg_refund_request_immutable
    BEFORE UPDATE OR DELETE ON refund_request
    FOR EACH ROW EXECUTE FUNCTION reject_decided_collection_mutation();

CREATE OR REPLACE FUNCTION reject_posted_allocation_mutation() RETURNS trigger AS $$
DECLARE
    v_status VARCHAR(20);
BEGIN
    SELECT status INTO v_status FROM finance_payment
     WHERE school_id = COALESCE(NEW.school_id, OLD.school_id)
       AND id = COALESCE(NEW.payment_id, OLD.payment_id);
    IF TG_OP = 'DELETE' AND COALESCE(v_status, 'POSTED') <> 'DRAFT' THEN
        RAISE EXCEPTION 'posted payment allocations are immutable';
    END IF;
    IF TG_OP = 'UPDATE' AND COALESCE(v_status, 'POSTED') <> 'DRAFT'
       AND (NEW.payment_id <> OLD.payment_id
            OR NEW.charge_installment_id <> OLD.charge_installment_id
            OR NEW.student_id <> OLD.student_id
            OR NEW.allocated_minor <> OLD.allocated_minor
            OR NEW.currency <> OLD.currency) THEN
        RAISE EXCEPTION 'posted payment allocations are immutable';
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_payment_allocation_immutable
    BEFORE UPDATE OR DELETE ON payment_allocation
    FOR EACH ROW EXECUTE FUNCTION reject_posted_allocation_mutation();

-- BAY-47 action grants are explicit and retain the existing finance-module
-- fallback for older tenants that have not yet refreshed their matrix.
INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT s.id, r.code, a.action_code,
       CASE
           WHEN r.code IN ('principal', 'accountant') THEN true
           WHEN r.code = 'econome' AND a.action_code IN
                ('PAYMENT_VIEW','PAYMENT_COLLECT','PAYMENT_REVERSE','REFUND_REQUEST',
                 'CASHIER_SESSION_OPEN','CASHIER_SESSION_CLOSE') THEN true
           ELSE false
       END
FROM school s
JOIN role r ON r.code IN ('principal','econome','accountant')
CROSS JOIN (VALUES
    ('PAYMENT_VIEW'), ('PAYMENT_COLLECT'), ('PAYMENT_REVERSE'),
    ('REFUND_REQUEST'), ('REFUND_APPROVE'), ('CASHIER_SESSION_OPEN'),
    ('CASHIER_SESSION_CLOSE'), ('CASHIER_SESSION_APPROVE'),
    ('PROVIDER_CALLBACK_REVIEW')
) AS a(action_code)
ON CONFLICT (school_id, role_code, action_code)
DO UPDATE SET allowed = EXCLUDED.allowed;
