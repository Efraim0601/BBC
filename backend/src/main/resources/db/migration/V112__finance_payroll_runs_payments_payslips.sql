-- BAY-50: configurable payroll runs, payments and payslip documents.
-- Forward-only after V64. Amounts are integer XAF minor units; tax/legal
-- formulas remain school-configured rather than being hard-coded here.

ALTER TABLE employee ADD COLUMN IF NOT EXISTS exited_on DATE;
CREATE UNIQUE INDEX IF NOT EXISTS uq_employee_school_id_v65 ON employee(school_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_channel_school_id_v65 ON payment_channel(school_id, id);

CREATE TABLE payroll_component_type (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    code                VARCHAR(64) NOT NULL,
    name_fr             VARCHAR(160) NOT NULL,
    name_en             VARCHAR(160) NOT NULL,
    component_kind      VARCHAR(28) NOT NULL CHECK (component_kind IN ('EARNING','DEDUCTION','EMPLOYER_CONTRIBUTION')),
    calculation_mode    VARCHAR(20) NOT NULL CHECK (calculation_mode IN ('FIXED','PERCENTAGE','HOURLY','MANUAL')),
    default_amount_minor BIGINT NOT NULL DEFAULT 0 CHECK (default_amount_minor >= 0),
    default_rate_bps    INT NOT NULL DEFAULT 0 CHECK (default_rate_bps >= 0 AND default_rate_bps <= 10000),
    expense_account_id  UUID,
    liability_account_id UUID,
    effective_from      DATE,
    effective_to        DATE,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payroll_component_school_id UNIQUE (school_id, id),
    CONSTRAINT uq_payroll_component_code UNIQUE (school_id, code),
    CONSTRAINT fk_payroll_component_expense FOREIGN KEY (school_id, expense_account_id)
        REFERENCES chart_of_account(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_payroll_component_liability FOREIGN KEY (school_id, liability_account_id)
        REFERENCES chart_of_account(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_payroll_component_dates CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)
);
CREATE INDEX idx_payroll_component_active ON payroll_component_type(school_id, active, effective_from, code);

CREATE TABLE payroll_period (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    code                VARCHAR(48) NOT NULL,
    start_date          DATE NOT NULL,
    end_date            DATE NOT NULL,
    payment_date        DATE NOT NULL,
    accounting_period_id UUID NOT NULL,
    status              VARCHAR(12) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CLOSED')),
    version             BIGINT NOT NULL DEFAULT 0,
    created_by          UUID REFERENCES app_user(id),
    closed_by           UUID REFERENCES app_user(id),
    closed_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payroll_period_school_id UNIQUE (school_id, id),
    CONSTRAINT uq_payroll_period_code UNIQUE (school_id, code),
    CONSTRAINT uq_payroll_period_dates UNIQUE (school_id, start_date, end_date),
    CONSTRAINT fk_payroll_period_accounting FOREIGN KEY (school_id, accounting_period_id)
        REFERENCES accounting_period(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_payroll_period_dates CHECK (end_date >= start_date AND payment_date >= start_date)
);
CREATE INDEX idx_payroll_period_status ON payroll_period(school_id, status, start_date DESC);

CREATE TABLE payroll_run (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    payroll_period_id   UUID NOT NULL,
    run_number          BIGINT NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','CALCULATED','REVIEWED','APPROVED','PAID','VOID')),
    proration_mode      VARCHAR(12) NOT NULL DEFAULT 'NONE' CHECK (proration_mode IN ('NONE','DAILY')),
    default_hours       INT NOT NULL DEFAULT 0 CHECK (default_hours >= 0),
    employee_scope_json TEXT,
    segregation_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    employee_count      INT NOT NULL DEFAULT 0 CHECK (employee_count >= 0),
    exception_count     INT NOT NULL DEFAULT 0 CHECK (exception_count >= 0),
    gross_minor         BIGINT NOT NULL DEFAULT 0 CHECK (gross_minor >= 0),
    deduction_minor     BIGINT NOT NULL DEFAULT 0 CHECK (deduction_minor >= 0),
    net_minor           BIGINT NOT NULL DEFAULT 0 CHECK (net_minor >= 0),
    employer_cost_minor BIGINT NOT NULL DEFAULT 0 CHECK (employer_cost_minor >= 0),
    currency            VARCHAR(3) NOT NULL DEFAULT 'XAF' CHECK (currency = upper(currency) AND length(currency) = 3),
    calculation_snapshot_hash VARCHAR(64),
    previous_snapshot_hash VARCHAR(64),
    snapshot_locked     BOOLEAN NOT NULL DEFAULT FALSE,
    calculation_idempotency_key VARCHAR(160),
    source_event_key    VARCHAR(240) NOT NULL,
    accrual_journal_id  UUID,
    payment_journal_id  UUID,
    calculated_by       UUID REFERENCES app_user(id),
    calculated_at       TIMESTAMPTZ,
    reviewed_by         UUID REFERENCES app_user(id),
    reviewed_at         TIMESTAMPTZ,
    approved_by         UUID REFERENCES app_user(id),
    approved_at         TIMESTAMPTZ,
    paid_by             UUID REFERENCES app_user(id),
    paid_at             TIMESTAMPTZ,
    voided_by           UUID REFERENCES app_user(id),
    voided_at           TIMESTAMPTZ,
    void_reason         VARCHAR(500),
    version             BIGINT NOT NULL DEFAULT 0,
    created_by          UUID REFERENCES app_user(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payroll_run_school_id UNIQUE (school_id, id),
    CONSTRAINT uq_payroll_run_number UNIQUE (school_id, payroll_period_id, run_number),
    CONSTRAINT uq_payroll_run_source_event UNIQUE (school_id, source_event_key),
    CONSTRAINT fk_payroll_run_period FOREIGN KEY (school_id, payroll_period_id)
        REFERENCES payroll_period(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_payroll_run_totals CHECK (deduction_minor <= gross_minor AND net_minor = gross_minor - deduction_minor)
);
CREATE UNIQUE INDEX uq_payroll_run_open_period ON payroll_run(school_id, payroll_period_id)
    WHERE status <> 'VOID';
CREATE INDEX idx_payroll_run_status ON payroll_run(school_id, status, created_at DESC);

CREATE TABLE employee_payroll (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    payroll_run_id      UUID NOT NULL,
    employee_id         UUID NOT NULL,
    employee_code       VARCHAR(32) NOT NULL,
    employee_name       VARCHAR(160) NOT NULL,
    employee_email      VARCHAR(180),
    employment_type     VARCHAR(32) NOT NULL,
    hired_on_snapshot   DATE,
    exited_on_snapshot  DATE,
    employment_mode     VARCHAR(16) NOT NULL CHECK (employment_mode IN ('MONTHLY','HOURLY')),
    monthly_salary_minor BIGINT NOT NULL DEFAULT 0 CHECK (monthly_salary_minor >= 0),
    hourly_rate_minor   BIGINT NOT NULL DEFAULT 0 CHECK (hourly_rate_minor >= 0),
    approved_hours      INT NOT NULL DEFAULT 0 CHECK (approved_hours >= 0),
    eligible            BOOLEAN NOT NULL DEFAULT TRUE,
    status              VARCHAR(16) NOT NULL DEFAULT 'READY' CHECK (status IN ('READY','EXCEPTION','PAID','VOID')),
    exception_code      VARCHAR(80),
    exception_message   VARCHAR(1000),
    formula             VARCHAR(500),
    gross_minor         BIGINT NOT NULL DEFAULT 0 CHECK (gross_minor >= 0),
    deduction_minor     BIGINT NOT NULL DEFAULT 0 CHECK (deduction_minor >= 0),
    net_minor           BIGINT NOT NULL DEFAULT 0 CHECK (net_minor >= 0),
    employer_cost_minor BIGINT NOT NULL DEFAULT 0 CHECK (employer_cost_minor >= 0),
    snapshot_hash       VARCHAR(64) NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_employee_payroll_school_id UNIQUE (school_id, id),
    CONSTRAINT uq_employee_payroll_run_employee UNIQUE (school_id, payroll_run_id, employee_id),
    CONSTRAINT fk_employee_payroll_run FOREIGN KEY (school_id, payroll_run_id)
        REFERENCES payroll_run(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_employee_payroll_employee FOREIGN KEY (school_id, employee_id)
        REFERENCES employee(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_employee_payroll_totals CHECK (deduction_minor <= gross_minor AND net_minor = gross_minor - deduction_minor)
);
CREATE INDEX idx_employee_payroll_run_status ON employee_payroll(school_id, payroll_run_id, status, employee_name);

CREATE TABLE employee_payroll_line (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    employee_payroll_id UUID NOT NULL,
    line_no             INT NOT NULL CHECK (line_no > 0),
    component_type_id   UUID,
    component_code      VARCHAR(64) NOT NULL,
    component_name_fr   VARCHAR(160) NOT NULL,
    component_name_en   VARCHAR(160) NOT NULL,
    component_kind      VARCHAR(28) NOT NULL CHECK (component_kind IN ('EARNING','DEDUCTION','EMPLOYER_CONTRIBUTION')),
    calculation_mode    VARCHAR(20) NOT NULL,
    quantity            BIGINT NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    rate_bps            INT NOT NULL DEFAULT 0 CHECK (rate_bps >= 0),
    amount_minor        BIGINT NOT NULL CHECK (amount_minor >= 0),
    source              VARCHAR(12) NOT NULL DEFAULT 'DEFAULT' CHECK (source IN ('DEFAULT','MANUAL')),
    reason              VARCHAR(500),
    expense_account_id  UUID,
    liability_account_id UUID,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_employee_payroll_line_school_id UNIQUE (school_id, id),
    CONSTRAINT uq_employee_payroll_line_no UNIQUE (school_id, employee_payroll_id, line_no),
    CONSTRAINT fk_employee_payroll_line_employee FOREIGN KEY (school_id, employee_payroll_id)
        REFERENCES employee_payroll(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_employee_payroll_line_component FOREIGN KEY (school_id, component_type_id)
        REFERENCES payroll_component_type(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_employee_payroll_line_expense FOREIGN KEY (school_id, expense_account_id)
        REFERENCES chart_of_account(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_employee_payroll_line_liability FOREIGN KEY (school_id, liability_account_id)
        REFERENCES chart_of_account(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_employee_payroll_line_reason CHECK (source <> 'MANUAL' OR length(trim(coalesce(reason,''))) >= 3)
);
CREATE INDEX idx_employee_payroll_line_parent ON employee_payroll_line(school_id, employee_payroll_id, line_no);

CREATE TABLE payroll_payment (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    employee_payroll_id UUID NOT NULL,
    payment_channel_id  UUID,
    channel_code        VARCHAR(48) NOT NULL,
    payment_account_id  UUID NOT NULL,
    payment_reference   VARCHAR(180),
    amount_minor        BIGINT NOT NULL CHECK (amount_minor > 0),
    currency            VARCHAR(3) NOT NULL DEFAULT 'XAF' CHECK (currency = upper(currency) AND length(currency) = 3),
    payment_date        DATE NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'POSTED' CHECK (status IN ('POSTED','FAILED','REVERSED')),
    journal_entry_id    UUID,
    source_event_key    VARCHAR(240) NOT NULL,
    idempotency_key     VARCHAR(160) NOT NULL,
    posted_by           UUID REFERENCES app_user(id),
    posted_at           TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payroll_payment_school_id UNIQUE (school_id, id),
    CONSTRAINT uq_payroll_payment_source_event UNIQUE (school_id, source_event_key),
    CONSTRAINT uq_payroll_payment_idempotency UNIQUE (school_id, idempotency_key),
    CONSTRAINT uq_payroll_payment_channel UNIQUE (school_id, payment_channel_id, id),
    CONSTRAINT fk_payroll_payment_employee FOREIGN KEY (school_id, employee_payroll_id)
        REFERENCES employee_payroll(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_payroll_payment_channel FOREIGN KEY (school_id, payment_channel_id)
        REFERENCES payment_channel(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_payroll_payment_account FOREIGN KEY (school_id, payment_account_id)
        REFERENCES chart_of_account(school_id, id) ON DELETE RESTRICT
);
CREATE UNIQUE INDEX uq_payroll_payment_employee_posted ON payroll_payment(school_id, employee_payroll_id)
    WHERE status = 'POSTED';
CREATE INDEX idx_payroll_payment_status ON payroll_payment(school_id, status, payment_date DESC);

CREATE TABLE payroll_payslip_job (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    payroll_run_id      UUID NOT NULL,
    status              VARCHAR(28) NOT NULL DEFAULT 'RUNNING'
        CHECK (status IN ('RUNNING','COMPLETED','COMPLETED_WITH_FAILURES','FAILED')),
    total_count         INT NOT NULL DEFAULT 0,
    issued_count        INT NOT NULL DEFAULT 0,
    failed_count        INT NOT NULL DEFAULT 0,
    idempotency_key     VARCHAR(160) NOT NULL,
    last_error          VARCHAR(1000),
    version             BIGINT NOT NULL DEFAULT 0,
    requested_by        UUID REFERENCES app_user(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ,
    CONSTRAINT uq_payroll_payslip_job_school_id UNIQUE (school_id, id),
    CONSTRAINT uq_payroll_payslip_job_idempotency UNIQUE (school_id, idempotency_key),
    CONSTRAINT fk_payroll_payslip_job_run FOREIGN KEY (school_id, payroll_run_id)
        REFERENCES payroll_run(school_id, id) ON DELETE RESTRICT
);

CREATE TABLE payslip (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    employee_payroll_id UUID NOT NULL,
    version_no          INT NOT NULL DEFAULT 1 CHECK (version_no > 0),
    payslip_number      VARCHAR(80) NOT NULL,
    locale              VARCHAR(4) NOT NULL DEFAULT 'fr',
    status              VARCHAR(24) NOT NULL DEFAULT 'GENERATION_FAILED'
        CHECK (status IN ('ISSUED','GENERATION_FAILED','VOIDED','SUPERSEDED')),
    generated_document_id UUID,
    snapshot_hash       VARCHAR(64) NOT NULL,
    source_event_key    VARCHAR(240) NOT NULL,
    idempotency_key     VARCHAR(160) NOT NULL,
    generation_error    VARCHAR(1000),
    superseded_by_id    UUID,
    issued_by           UUID REFERENCES app_user(id),
    issued_at           TIMESTAMPTZ,
    voided_by           UUID REFERENCES app_user(id),
    voided_at           TIMESTAMPTZ,
    void_reason         VARCHAR(500),
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payslip_school_id UNIQUE (school_id, id),
    CONSTRAINT uq_payslip_number UNIQUE (school_id, payslip_number),
    CONSTRAINT uq_payslip_employee_version UNIQUE (school_id, employee_payroll_id, version_no),
    CONSTRAINT uq_payslip_source_event UNIQUE (school_id, source_event_key),
    CONSTRAINT uq_payslip_idempotency UNIQUE (school_id, idempotency_key),
    CONSTRAINT fk_payslip_employee FOREIGN KEY (school_id, employee_payroll_id)
        REFERENCES employee_payroll(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_payslip_document FOREIGN KEY (school_id, generated_document_id)
        REFERENCES generated_document(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_payslip_superseded FOREIGN KEY (school_id, superseded_by_id)
        REFERENCES payslip(school_id, id) ON DELETE RESTRICT
);
CREATE INDEX idx_payslip_employee_status ON payslip(school_id, employee_payroll_id, status, version_no DESC);

CREATE TABLE payroll_payslip_job_result (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    job_id              UUID NOT NULL,
    employee_payroll_id UUID NOT NULL,
    payslip_id          UUID,
    result_status       VARCHAR(20) NOT NULL CHECK (result_status IN ('ISSUED','FAILED','ALREADY_EXISTS')),
    error_detail        VARCHAR(1000),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payroll_payslip_job_result_school_id UNIQUE (school_id, id),
    CONSTRAINT uq_payroll_payslip_job_employee UNIQUE (school_id, job_id, employee_payroll_id),
    CONSTRAINT fk_payroll_payslip_job_result_job FOREIGN KEY (school_id, job_id)
        REFERENCES payroll_payslip_job(school_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_payroll_payslip_job_result_employee FOREIGN KEY (school_id, employee_payroll_id)
        REFERENCES employee_payroll(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_payroll_payslip_job_result_payslip FOREIGN KEY (school_id, payslip_id)
        REFERENCES payslip(school_id, id) ON DELETE RESTRICT
);
CREATE INDEX idx_payroll_payslip_job_result_job ON payroll_payslip_job_result(school_id, job_id, result_status);

CREATE OR REPLACE FUNCTION reject_approved_payroll_snapshot_mutation() RETURNS trigger AS $$
DECLARE v_status VARCHAR(16);
BEGIN
    SELECT r.status INTO v_status FROM payroll_run r
      WHERE r.school_id = COALESCE(NEW.school_id, OLD.school_id)
        AND r.id = COALESCE(NEW.payroll_run_id, OLD.payroll_run_id);
    IF COALESCE(v_status, 'APPROVED') IN ('APPROVED','PAID','VOID') AND
       (TG_OP = 'DELETE' OR NEW.employee_id IS DISTINCT FROM OLD.employee_id
        OR NEW.employee_code IS DISTINCT FROM OLD.employee_code OR NEW.employee_name IS DISTINCT FROM OLD.employee_name
        OR NEW.employment_type IS DISTINCT FROM OLD.employment_type OR NEW.employment_mode IS DISTINCT FROM OLD.employment_mode
        OR NEW.monthly_salary_minor IS DISTINCT FROM OLD.monthly_salary_minor OR NEW.hourly_rate_minor IS DISTINCT FROM OLD.hourly_rate_minor
        OR NEW.approved_hours IS DISTINCT FROM OLD.approved_hours OR NEW.gross_minor IS DISTINCT FROM OLD.gross_minor
        OR NEW.deduction_minor IS DISTINCT FROM OLD.deduction_minor OR NEW.net_minor IS DISTINCT FROM OLD.net_minor
        OR NEW.employer_cost_minor IS DISTINCT FROM OLD.employer_cost_minor OR NEW.snapshot_hash IS DISTINCT FROM OLD.snapshot_hash) THEN
        RAISE EXCEPTION 'approved payroll snapshots are immutable; use a correction run';
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_employee_payroll_immutable
    BEFORE UPDATE OR DELETE ON employee_payroll
    FOR EACH ROW EXECUTE FUNCTION reject_approved_payroll_snapshot_mutation();

CREATE OR REPLACE FUNCTION reject_approved_payroll_line_mutation() RETURNS trigger AS $$
DECLARE v_run UUID; v_status VARCHAR(16);
BEGIN
    SELECT payroll_run_id INTO v_run FROM employee_payroll
      WHERE school_id = COALESCE(NEW.school_id, OLD.school_id)
        AND id = COALESCE(NEW.employee_payroll_id, OLD.employee_payroll_id);
    SELECT status INTO v_status FROM payroll_run WHERE school_id = COALESCE(NEW.school_id, OLD.school_id) AND id = v_run;
    IF COALESCE(v_status, 'APPROVED') IN ('APPROVED','PAID','VOID') THEN
        RAISE EXCEPTION 'approved payroll lines are immutable; use a correction run';
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_employee_payroll_line_immutable
    BEFORE UPDATE OR DELETE ON employee_payroll_line
    FOR EACH ROW EXECUTE FUNCTION reject_approved_payroll_line_mutation();

CREATE OR REPLACE FUNCTION reject_posted_payroll_payment_mutation() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' OR (TG_OP = 'UPDATE' AND OLD.status = 'POSTED' AND
       (NEW.employee_payroll_id <> OLD.employee_payroll_id OR NEW.amount_minor <> OLD.amount_minor
        OR NEW.payment_date <> OLD.payment_date OR NEW.payment_account_id <> OLD.payment_account_id
        OR COALESCE(NEW.payment_reference,'') <> COALESCE(OLD.payment_reference,'')
        OR NEW.source_event_key <> OLD.source_event_key OR NEW.idempotency_key <> OLD.idempotency_key)) THEN
        RAISE EXCEPTION 'posted payroll payments are immutable; use a reversal';
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_payroll_payment_immutable
    BEFORE UPDATE OR DELETE ON payroll_payment
    FOR EACH ROW EXECUTE FUNCTION reject_posted_payroll_payment_mutation();

-- Configurable starting catalogue. Missing accounts intentionally remain NULL so
-- the payroll preview exposes an ACCOUNT_MAPPING_MISSING exception.
INSERT INTO payroll_component_type
    (school_id, code, name_fr, name_en, component_kind, calculation_mode,
     default_amount_minor, default_rate_bps, expense_account_id, liability_account_id, active)
SELECT s.id, x.code, x.name_fr, x.name_en, x.kind, x.mode, x.amount, x.rate,
       expense.id, liability.id, true
FROM school s
CROSS JOIN (VALUES
    ('BASE_SALARY','Salaire de base','Base salary','EARNING','FIXED',0,0,'6000','2200'),
    ('HOURLY_WORK','Travail horaire','Hourly work','EARNING','HOURLY',0,0,'6000','2200'),
    ('BONUS','Prime','Bonus','EARNING','FIXED',0,0,'6000','2200'),
    ('ALLOWANCE','Indemnité','Allowance','EARNING','FIXED',0,0,'6000','2200'),
    ('ADVANCE_RECOVERY','Récupération avance','Advance recovery','DEDUCTION','FIXED',0,0,'2200','2200'),
    ('OTHER_DEDUCTION','Autre retenue','Other deduction','DEDUCTION','MANUAL',0,0,'2200','2200'),
    ('EMPLOYER_CONTRIBUTION','Cotisation employeur','Employer contribution','EMPLOYER_CONTRIBUTION','PERCENTAGE',0,0,'6000','2200')
) AS x(code,name_fr,name_en,kind,mode,amount,rate,expense_code,liability_code)
LEFT JOIN chart_of_account expense ON expense.school_id=s.id AND expense.code=x.expense_code
LEFT JOIN chart_of_account liability ON liability.school_id=s.id AND liability.code=x.liability_code
ON CONFLICT (school_id, code) DO NOTHING;

INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT s.id, r.code, a.action_code,
       CASE
           WHEN r.code IN ('principal','accountant') THEN true
           WHEN r.code IN ('hr','payroll') AND a.action_code IN ('PAYROLL_VIEW','PAYROLL_CALCULATE','PAYROLL_REVIEW','PAYSLIP_VIEW_ALL','PAYROLL_PERIOD_MANAGE','PAYROLL_COMPONENT_MANAGE','PAYROLL_ADJUST') THEN true
           ELSE false
       END
FROM school s
JOIN role r ON r.code IN ('principal','accountant','hr','payroll')
CROSS JOIN (VALUES
    ('PAYROLL_VIEW'), ('PAYROLL_PERIOD_MANAGE'), ('PAYROLL_COMPONENT_MANAGE'), ('PAYROLL_CALCULATE'),
    ('PAYROLL_ADJUST'), ('PAYROLL_REVIEW'), ('PAYROLL_APPROVE'), ('PAYROLL_PAY'),
    ('PAYSLIP_VIEW_ALL'), ('PAYSLIP_REGENERATE')
) AS a(action_code)
ON CONFLICT (school_id, role_code, action_code)
DO UPDATE SET allowed = EXCLUDED.allowed;
