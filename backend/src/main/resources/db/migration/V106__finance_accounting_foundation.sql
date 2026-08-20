-- BAY-49 / Wave 1: accounting foundation.
-- This migration is deliberately independent of the legacy finance write model.
-- No fee, charge, payment, expense, payroll, or report rows are rewritten here.

CREATE TABLE chart_of_account (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    code            VARCHAR(32) NOT NULL,
    name_fr         VARCHAR(160) NOT NULL,
    name_en         VARCHAR(160) NOT NULL,
    account_type    VARCHAR(16) NOT NULL
        CHECK (account_type IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE')),
    normal_side     VARCHAR(6) NOT NULL CHECK (normal_side IN ('DEBIT','CREDIT')),
    currency        VARCHAR(3),
    parent_id       UUID,
    posting_allowed BOOLEAN NOT NULL DEFAULT true,
    active          BOOLEAN NOT NULL DEFAULT true,
    effective_from  DATE,
    effective_to    DATE,
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, code),
    UNIQUE (school_id, id),
    CONSTRAINT chk_chart_account_dates
        CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_from <= effective_to),
    CONSTRAINT fk_chart_account_parent
        FOREIGN KEY (school_id, parent_id) REFERENCES chart_of_account(school_id, id)
);
CREATE INDEX idx_chart_account_school_type
    ON chart_of_account(school_id, account_type, active, code);

-- PostgreSQL requires a matching unique key for the tenant-safe composite
-- reference below; the primary key alone is not enough for (school_id, id).
CREATE UNIQUE INDEX uq_academic_session_school_id
    ON academic_session(school_id, id);

CREATE TABLE accounting_period (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    code            VARCHAR(32) NOT NULL,
    name_fr         VARCHAR(160) NOT NULL,
    name_en         VARCHAR(160) NOT NULL,
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    academic_session_id UUID REFERENCES academic_session(id),
    status          VARCHAR(8) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN','CLOSED')),
    closed_at       TIMESTAMPTZ,
    closed_by       UUID REFERENCES app_user(id),
    close_reason    VARCHAR(500),
    reopened_at     TIMESTAMPTZ,
    reopened_by     UUID REFERENCES app_user(id),
    reopen_reason   VARCHAR(500),
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, code),
    UNIQUE (school_id, id),
    CONSTRAINT chk_accounting_period_dates CHECK (start_date <= end_date),
    CONSTRAINT fk_accounting_period_session
        FOREIGN KEY (school_id, academic_session_id)
        REFERENCES academic_session(school_id, id)
);
CREATE INDEX idx_accounting_period_school_dates
    ON accounting_period(school_id, start_date, end_date, status);

CREATE TABLE journal_entry (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    number              VARCHAR(80) NOT NULL,
    entry_date          DATE NOT NULL,
    status              VARCHAR(10) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','POSTED','REVERSED')),
    source_type         VARCHAR(80),
    source_id           VARCHAR(120),
    source_event_key    VARCHAR(180),
    description         VARCHAR(500) NOT NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'XAF',
    accounting_period_id UUID NOT NULL,
    reversal_of_id     UUID,
    reversed_by        UUID REFERENCES app_user(id),
    posted_at          TIMESTAMPTZ,
    posted_by          UUID REFERENCES app_user(id),
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, number),
    UNIQUE (school_id, source_event_key),
    UNIQUE (school_id, id),
    CONSTRAINT fk_journal_period
        FOREIGN KEY (school_id, accounting_period_id)
        REFERENCES accounting_period(school_id, id),
    CONSTRAINT fk_journal_reversal
        FOREIGN KEY (school_id, reversal_of_id)
        REFERENCES journal_entry(school_id, id),
    CONSTRAINT chk_journal_currency CHECK (currency = upper(currency) AND length(currency) = 3)
);
CREATE INDEX idx_journal_entry_school_date
    ON journal_entry(school_id, entry_date DESC, status);
CREATE INDEX idx_journal_entry_source
    ON journal_entry(school_id, source_type, source_id);

CREATE TABLE journal_line (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    journal_entry_id    UUID NOT NULL,
    line_number         INT NOT NULL CHECK (line_number > 0),
    account_id          UUID NOT NULL,
    debit_minor         BIGINT NOT NULL DEFAULT 0 CHECK (debit_minor >= 0),
    credit_minor        BIGINT NOT NULL DEFAULT 0 CHECK (credit_minor >= 0),
    student_id          UUID,
    enrollment_id       UUID,
    employee_id         UUID,
    class_id            UUID,
    fee_type_code       VARCHAR(64),
    description         VARCHAR(500),
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, journal_entry_id, line_number),
    CONSTRAINT fk_journal_line_entry
        FOREIGN KEY (school_id, journal_entry_id)
        REFERENCES journal_entry(school_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_journal_line_account
        FOREIGN KEY (school_id, account_id)
        REFERENCES chart_of_account(school_id, id),
    CONSTRAINT chk_journal_line_one_side
        CHECK ((debit_minor > 0 AND credit_minor = 0)
            OR (credit_minor > 0 AND debit_minor = 0))
);
CREATE INDEX idx_journal_line_account
    ON journal_line(school_id, account_id, journal_entry_id);

CREATE TABLE posting_rule (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    event_type          VARCHAR(80) NOT NULL,
    side                VARCHAR(6) NOT NULL CHECK (side IN ('DEBIT','CREDIT')),
    scope_code          VARCHAR(80),
    fee_type_code       VARCHAR(64),
    payment_channel_code VARCHAR(32),
    component_code      VARCHAR(64),
    target_account_id   UUID NOT NULL,
    priority            INT NOT NULL DEFAULT 0,
    effective_from      DATE,
    effective_to        DATE,
    enabled             BOOLEAN NOT NULL DEFAULT true,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, id),
    CONSTRAINT fk_posting_rule_account
        FOREIGN KEY (school_id, target_account_id)
        REFERENCES chart_of_account(school_id, id),
    CONSTRAINT chk_posting_rule_dates
        CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_from <= effective_to)
);
CREATE INDEX idx_posting_rule_resolution
    ON posting_rule(school_id, event_type, enabled, priority DESC, effective_from, effective_to);

CREATE TABLE reconciliation_item (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    source_type     VARCHAR(80) NOT NULL,
    source_id       VARCHAR(120),
    expected_amount BIGINT NOT NULL DEFAULT 0 CHECK (expected_amount >= 0),
    posted_amount   BIGINT NOT NULL DEFAULT 0 CHECK (posted_amount >= 0),
    currency        VARCHAR(3) NOT NULL DEFAULT 'XAF',
    state           VARCHAR(12) NOT NULL DEFAULT 'MISSING'
        CHECK (state IN ('MATCHED','MISSING','MISMATCH','IGNORED')),
    reason          VARCHAR(500) NOT NULL,
    resolved_at     TIMESTAMPTZ,
    resolved_by     UUID REFERENCES app_user(id),
    resolution_note VARCHAR(500),
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_reconciliation_currency CHECK (currency = upper(currency) AND length(currency) = 3)
);
CREATE INDEX idx_reconciliation_queue
    ON reconciliation_item(school_id, state, created_at DESC);
CREATE INDEX idx_reconciliation_source
    ON reconciliation_item(school_id, source_type, source_id);

CREATE TABLE document_sequence (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    document_type   VARCHAR(48) NOT NULL,
    period_key      VARCHAR(32) NOT NULL,
    prefix          VARCHAR(80) NOT NULL,
    next_number     BIGINT NOT NULL DEFAULT 1 CHECK (next_number > 0),
    padding         INT NOT NULL DEFAULT 6 CHECK (padding BETWEEN 1 AND 12),
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, document_type, period_key)
);
CREATE INDEX idx_document_sequence_school_type
    ON document_sequence(school_id, document_type, period_key);

-- Journal shape and posted immutability are protected in the database as well as
-- in the service layer. Constraint triggers are deferred so a balanced entry can
-- be assembled in one transaction before its status changes to POSTED.
CREATE OR REPLACE FUNCTION assert_posted_journal_balanced() RETURNS trigger AS $$
DECLARE
    v_journal_id UUID;
    v_status VARCHAR(10);
    v_count BIGINT;
    v_debit NUMERIC;
    v_credit NUMERIC;
BEGIN
    IF TG_TABLE_NAME = 'journal_entry' THEN
        v_journal_id := COALESCE(NEW.id, OLD.id);
    ELSE
        v_journal_id := COALESCE(NEW.journal_entry_id, OLD.journal_entry_id);
    END IF;
    SELECT status INTO v_status FROM journal_entry WHERE id = v_journal_id;
    IF v_status = 'POSTED' THEN
        SELECT count(*), COALESCE(sum(debit_minor), 0), COALESCE(sum(credit_minor), 0)
          INTO v_count, v_debit, v_credit
          FROM journal_line WHERE journal_entry_id = v_journal_id;
        IF v_count < 2 OR v_debit <> v_credit THEN
            RAISE EXCEPTION 'posted journal must have at least two balanced lines';
        END IF;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_journal_line_balanced
    AFTER INSERT OR UPDATE OR DELETE ON journal_line
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION assert_posted_journal_balanced();

CREATE CONSTRAINT TRIGGER trg_journal_entry_balanced
    AFTER INSERT OR UPDATE ON journal_entry
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION assert_posted_journal_balanced();

CREATE OR REPLACE FUNCTION reject_posted_journal_mutation() RETURNS trigger AS $$
DECLARE
    v_status VARCHAR(10);
BEGIN
    IF TG_TABLE_NAME = 'journal_entry' THEN
        IF TG_OP = 'DELETE' AND OLD.status IN ('POSTED','REVERSED') THEN
            RAISE EXCEPTION 'posted or reversed journals are immutable';
        END IF;
        IF TG_OP = 'UPDATE' AND OLD.status = 'REVERSED' THEN
            RAISE EXCEPTION 'reversed journals are immutable';
        END IF;
        IF TG_OP = 'UPDATE' AND OLD.status = 'POSTED' THEN
            IF NEW.status <> 'REVERSED'
               OR NEW.id <> OLD.id
               OR NEW.school_id <> OLD.school_id
               OR NEW.number <> OLD.number
               OR NEW.entry_date <> OLD.entry_date
               OR NEW.source_type IS DISTINCT FROM OLD.source_type
               OR NEW.source_id IS DISTINCT FROM OLD.source_id
               OR NEW.source_event_key IS DISTINCT FROM OLD.source_event_key
               OR NEW.description <> OLD.description
               OR NEW.currency <> OLD.currency
               OR NEW.accounting_period_id <> OLD.accounting_period_id
               OR NEW.reversal_of_id IS DISTINCT FROM OLD.reversal_of_id
               OR NEW.posted_at IS DISTINCT FROM OLD.posted_at
               OR NEW.posted_by IS DISTINCT FROM OLD.posted_by THEN
                RAISE EXCEPTION 'posted journals are immutable; reverse the journal instead';
            END IF;
        END IF;
    ELSE
        SELECT status INTO v_status FROM journal_entry WHERE id = COALESCE(NEW.journal_entry_id, OLD.journal_entry_id);
        IF v_status IN ('POSTED','REVERSED') THEN
            RAISE EXCEPTION 'lines of posted or reversed journals are immutable';
        END IF;
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_journal_entry_immutable
    BEFORE UPDATE OR DELETE ON journal_entry
    FOR EACH ROW EXECUTE FUNCTION reject_posted_journal_mutation();
CREATE TRIGGER trg_journal_line_immutable
    BEFORE INSERT OR UPDATE OR DELETE ON journal_line
    FOR EACH ROW EXECUTE FUNCTION reject_posted_journal_mutation();

CREATE OR REPLACE FUNCTION reject_used_account_identity_change() RETURNS trigger AS $$
BEGIN
    IF (NEW.code IS DISTINCT FROM OLD.code OR NEW.account_type IS DISTINCT FROM OLD.account_type)
       AND EXISTS (
           SELECT 1
             FROM journal_line l
             JOIN journal_entry j ON j.id = l.journal_entry_id AND j.school_id = l.school_id
            WHERE l.school_id = OLD.school_id
              AND l.account_id = OLD.id
              AND j.status IN ('POSTED','REVERSED')
       ) THEN
        RAISE EXCEPTION 'account identity cannot change after a posted journal references it';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_chart_account_identity
    BEFORE UPDATE ON chart_of_account
    FOR EACH ROW EXECUTE FUNCTION reject_used_account_identity_change();

-- Existing schools get a draft catalogue. A new school created by the runtime
-- bootstrap receives the same catalogue in ProductionBootstrap; these rows are
-- intentionally not posted opening balances.
INSERT INTO chart_of_account
    (school_id, code, name_fr, name_en, account_type, normal_side, currency, posting_allowed)
SELECT s.id, a.code, a.name_fr, a.name_en, a.account_type, a.normal_side, 'XAF', true
FROM school s
CROSS JOIN (VALUES
    ('1000','Caisse','Cash on hand','ASSET','DEBIT'),
    ('1010','Banque','Bank','ASSET','DEBIT'),
    ('1020','Compensation Orange Money','Orange Money clearing','ASSET','DEBIT'),
    ('1030','Compensation MoMo','MoMo clearing','ASSET','DEBIT'),
    ('1040','Compensation carte','Card clearing','ASSET','DEBIT'),
    ('1100','Créances élèves','Accounts receivable - students','ASSET','DEBIT'),
    ('2100','Crédits élèves','Student credits','LIABILITY','CREDIT'),
    ('4000','Produits de scolarité','Tuition revenue','REVENUE','CREDIT'),
    ('4010','Produits d''inscription','Registration revenue','REVENUE','CREDIT'),
    ('4090','Autres produits scolaires','Other fee revenue','REVENUE','CREDIT'),
    ('2200','Dettes de paie','Payroll payable','LIABILITY','CREDIT'),
    ('6000','Charges de personnel','Salary expense','EXPENSE','DEBIT'),
    ('6900','Compte de contrôle des dépenses','Expense control','EXPENSE','DEBIT'),
    ('3000','Fonds propres d''ouverture','Opening balance equity','EQUITY','CREDIT'),
    ('3990','Compte d''attente','Suspense','EQUITY','CREDIT')
) AS a(code, name_fr, name_en, account_type, normal_side)
ON CONFLICT (school_id, code) DO NOTHING;

-- One open monthly period per month of each known academic session. Historical
-- sessions remain closed; the current/draft session remains open for setup.
INSERT INTO accounting_period
    (school_id, code, name_fr, name_en, start_date, end_date, academic_session_id, status)
SELECT s.school_id, to_char(month_start, 'YYYY-MM'),
       'Période ' || to_char(month_start, 'YYYY-MM'),
       'Period ' || to_char(month_start, 'YYYY-MM'),
       month_start::date,
       LEAST((month_start + interval '1 month - 1 day')::date, s.end_date),
       s.id,
       CASE WHEN s.status IN ('OPEN','DRAFT') THEN 'OPEN' ELSE 'CLOSED' END
FROM academic_session s
CROSS JOIN LATERAL generate_series(
    date_trunc('month', s.start_date)::date,
    date_trunc('month', s.end_date)::date,
    interval '1 month') month_start
WHERE month_start::date <= s.end_date
ON CONFLICT (school_id, code) DO NOTHING;

INSERT INTO document_sequence (school_id, document_type, period_key, prefix, next_number, padding)
SELECT p.school_id, 'JOURNAL', p.code, 'JRN/' || p.code || '/', 1, 6
FROM accounting_period p
ON CONFLICT (school_id, document_type, period_key) DO NOTHING;

-- School defaults are explicit rules, not hidden controller constants. Future
-- fee/payment/payroll waves may add more specific scoped rules with higher priority.
INSERT INTO posting_rule
    (school_id, event_type, side, target_account_id, priority, enabled)
SELECT s.id, r.event_type, r.side, a.id, 0, true
FROM school s
CROSS JOIN (VALUES
    ('FEE_CHARGE','DEBIT','1100'), ('FEE_CHARGE','CREDIT','4000'),
    ('PAYMENT_CASH','DEBIT','1000'), ('PAYMENT_CASH','CREDIT','1100'),
    ('PAYMENT_OM','DEBIT','1020'), ('PAYMENT_OM','CREDIT','1100'),
    ('PAYMENT_MOMO','DEBIT','1030'), ('PAYMENT_MOMO','CREDIT','1100'),
    ('PAYMENT_CARD','DEBIT','1040'), ('PAYMENT_CARD','CREDIT','1100'),
    ('PAYMENT_TRANSFER','DEBIT','1010'), ('PAYMENT_TRANSFER','CREDIT','1100'),
    ('EXPENSE_POST','DEBIT','6900'), ('EXPENSE_POST','CREDIT','1000'),
    ('PAYROLL_ACCRUAL','DEBIT','6000'), ('PAYROLL_ACCRUAL','CREDIT','2200'),
    ('PAYROLL_PAYMENT','DEBIT','2200'), ('PAYROLL_PAYMENT','CREDIT','1010')
) AS r(event_type, side, account_code)
JOIN chart_of_account a ON a.school_id = s.id AND a.code = r.account_code
WHERE NOT EXISTS (
    SELECT 1 FROM posting_rule pr
    WHERE pr.school_id = s.id AND pr.event_type = r.event_type AND pr.side = r.side
      AND pr.priority = 0 AND pr.scope_code IS NULL AND pr.fee_type_code IS NULL
      AND pr.payment_channel_code IS NULL AND pr.component_code IS NULL
);

-- Action grants are seeded for roles that exist today. Explicit false rows for
-- the bursar prevent a broad finance=write module grant from becoming ledger
-- administration. The accountant role is available for schools that use it.
INSERT INTO role (code, label_fr, label_en, builtin)
VALUES ('accountant', 'Comptable', 'Accountant', true)
ON CONFLICT (code) DO NOTHING;
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT s.id, 'accountant', 'finance', 'write'
FROM school s
ON CONFLICT (school_id, role_code, module) DO NOTHING;

INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT s.id, r.code, a.action_code,
       CASE
           WHEN r.code IN ('principal','accountant') THEN true
           WHEN r.code = 'econome' AND a.action_code IN
                ('FINANCE_OVERVIEW_VIEW','FINANCE_REPORT_VIEW','FINANCE_EXPORT') THEN true
           ELSE false
       END
FROM school s
JOIN role r ON r.code IN ('principal','econome','accountant')
CROSS JOIN (VALUES
    ('FINANCE_OVERVIEW_VIEW'), ('FEE_TYPE_MANAGE'), ('FEE_PLAN_DRAFT'),
    ('FEE_PLAN_ACTIVATE'), ('CHARGE_PREVIEW'), ('CHARGE_GENERATE'),
    ('CHARGE_ADJUST'), ('FEE_WAIVE_REQUEST'), ('FEE_WAIVE_APPROVE'),
    ('PAYMENT_COLLECT'), ('PAYMENT_REVERSE'), ('REFUND_REQUEST'),
    ('REFUND_APPROVE'), ('CASHIER_SESSION_CLOSE'), ('FINANCE_DOCUMENT_GENERATE'),
    ('FINANCE_DOCUMENT_VOID'), ('ACCOUNT_MANAGE'), ('POSTING_RULE_MANAGE'),
    ('LEDGER_POST'), ('LEDGER_REVERSE'), ('LEDGER_CLOSE'), ('LEDGER_REOPEN'),
    ('PAYROLL_CALCULATE'), ('PAYROLL_REVIEW'), ('PAYROLL_APPROVE'), ('PAYROLL_PAY'),
    ('PAYSLIP_VIEW_ALL'), ('FINANCE_REPORT_VIEW'), ('FINANCE_EXPORT')
) AS a(action_code)
ON CONFLICT (school_id, role_code, action_code)
DO UPDATE SET allowed = EXCLUDED.allowed;
