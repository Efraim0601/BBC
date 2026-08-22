-- Finance Wave 1: operational treasury accounts and journal-backed movements.
-- A treasury account is the user-facing name for an ASSET chart account. Its
-- balance is always derived from posted journal lines; the table never stores
-- a mutable balance that can drift away from the ledger.

CREATE TABLE treasury_account (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id            UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    chart_account_id     UUID NOT NULL,
    kind                 VARCHAR(20) NOT NULL
        CHECK (kind IN ('CASH','BANK','MOBILE_WALLET','OTHER')),
    display_name         VARCHAR(160) NOT NULL,
    institution_name     VARCHAR(160),
    account_number_last4 VARCHAR(32),
    currency             VARCHAR(3) NOT NULL DEFAULT 'XAF'
        CHECK (currency = upper(currency) AND length(currency) = 3),
    active               BOOLEAN NOT NULL DEFAULT true,
    is_default            BOOLEAN NOT NULL DEFAULT false,
    version               BIGINT NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    archived_at           TIMESTAMPTZ,
    archived_by           UUID REFERENCES app_user(id),
    UNIQUE (school_id, id),
    UNIQUE (school_id, chart_account_id),
    CONSTRAINT fk_treasury_chart_account
        FOREIGN KEY (school_id, chart_account_id)
        REFERENCES chart_of_account(school_id, id) ON DELETE RESTRICT
);
CREATE UNIQUE INDEX uq_treasury_default_account
    ON treasury_account(school_id) WHERE is_default AND active;
CREATE INDEX idx_treasury_account_school_active
    ON treasury_account(school_id, active, kind, display_name);

CREATE TABLE treasury_movement (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id            UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    movement_no          VARCHAR(80) NOT NULL,
    movement_type        VARCHAR(16) NOT NULL
        CHECK (movement_type IN ('OPENING','DEPOSIT','WITHDRAWAL','TRANSFER','ADJUSTMENT')),
    entry_date           DATE NOT NULL,
    from_account_id      UUID,
    to_account_id        UUID,
    offset_account_id    UUID,
    amount_minor         BIGINT NOT NULL CHECK (amount_minor > 0),
    currency             VARCHAR(3) NOT NULL DEFAULT 'XAF'
        CHECK (currency = upper(currency) AND length(currency) = 3),
    reason               VARCHAR(500) NOT NULL,
    reference            VARCHAR(180),
    status                VARCHAR(12) NOT NULL DEFAULT 'POSTED'
        CHECK (status IN ('POSTED','REVERSED')),
    journal_entry_id     UUID,
    created_by           UUID REFERENCES app_user(id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              BIGINT NOT NULL DEFAULT 0,
    UNIQUE (school_id, id),
    UNIQUE (school_id, movement_no),
    CONSTRAINT fk_treasury_movement_from
        FOREIGN KEY (school_id, from_account_id)
        REFERENCES treasury_account(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_treasury_movement_to
        FOREIGN KEY (school_id, to_account_id)
        REFERENCES treasury_account(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_treasury_movement_offset
        FOREIGN KEY (school_id, offset_account_id)
        REFERENCES chart_of_account(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_treasury_movement_journal
        FOREIGN KEY (school_id, journal_entry_id)
        REFERENCES journal_entry(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_treasury_movement_accounts
        CHECK (from_account_id IS NOT NULL OR to_account_id IS NOT NULL),
    CONSTRAINT chk_treasury_movement_not_same
        CHECK (from_account_id IS NULL OR to_account_id IS NULL OR from_account_id <> to_account_id)
);
CREATE INDEX idx_treasury_movement_school_date
    ON treasury_movement(school_id, entry_date DESC, movement_no DESC);
CREATE INDEX idx_treasury_movement_account
    ON treasury_movement(school_id, from_account_id, to_account_id, entry_date DESC);

-- Operational bank accounts requested by the school. The original generic
-- 1010 bank account remains available for legacy mappings; these four accounts
-- make the actual institutions independently visible and reconcilable.
INSERT INTO chart_of_account
    (school_id, code, name_fr, name_en, account_type, normal_side, currency, posting_allowed)
SELECT s.id, a.code, a.name_fr, a.name_en, 'ASSET', 'DEBIT', 'XAF', true
FROM school s
CROSS JOIN (VALUES
    ('1011','BGFI Bank','BGFI Bank'),
    ('1012','Afriland First Bank','Afriland First Bank'),
    ('1013','CCA Bank','CCA Bank'),
    ('1014','Banque Régionale','Regional Bank')
) AS a(code, name_fr, name_en)
ON CONFLICT (school_id, code) DO NOTHING;

INSERT INTO treasury_account
    (school_id, chart_account_id, kind, display_name, institution_name, currency, active, is_default)
SELECT s.id, a.id, seed.kind, seed.display_name, seed.institution_name, 'XAF', true, seed.is_default
FROM school s
JOIN (VALUES
    ('1000','CASH','Cash','Cash',true),
    ('1011','BANK','BGFI Bank','BGFI Bank',false),
    ('1012','BANK','Afriland','Afriland First Bank',false),
    ('1013','BANK','CCA','CCA Bank',false),
    ('1014','BANK','Regional','Banque Régionale',false)
) AS seed(code, kind, display_name, institution_name, is_default) ON true
JOIN chart_of_account a ON a.school_id=s.id AND a.code=seed.code
ON CONFLICT (school_id, chart_account_id) DO NOTHING;

-- New, explicit permissions for operational treasury work. The older
-- ACCOUNT_MANAGE/LEDGER_POST actions remain available for advanced accounting.
INSERT INTO permission_action
    (code,module,group_code,label_fr,label_en,description_fr,description_en,
     risk_level,scope_type,required_level,default_read_action,display_order)
VALUES
    ('TREASURY_ACCOUNT_VIEW','finance','Finance',
     'Comptes de trésorerie — consulter','Treasury accounts — view',
     'Voir les caisses, banques et soldes calculés depuis le journal.','View cash, bank accounts and balances derived from the ledger.',
     'LOW','SCHOOL','read',true,543),
    ('TREASURY_ACCOUNT_MANAGE','finance','Finance',
     'Comptes de trésorerie — gérer','Treasury accounts — manage',
     'Ajouter, configurer ou archiver un compte de trésorerie.','Add, configure or archive a treasury account.',
     'HIGH','SCHOOL','write',false,544),
    ('TREASURY_MOVEMENT_VIEW','finance','Finance',
     'Mouvements de trésorerie — consulter','Treasury movements — view',
     'Consulter les dépôts, retraits, transferts et soldes traçables.','View traceable deposits, withdrawals, transfers and balances.',
     'LOW','SCHOOL','read',true,545),
    ('TREASURY_MOVEMENT_CREATE','finance','Finance',
     'Mouvement de trésorerie — enregistrer','Treasury movement — create',
     'Enregistrer un dépôt, retrait, transfert ou solde initial avec motif.','Record a deposit, withdrawal, transfer or opening balance with a reason.',
     'HIGH','SCHOOL','write',false,546),
    ('TREASURY_RECONCILE','finance','Finance',
     'Trésorerie — rapprocher','Treasury — reconcile',
     'Rapprocher les relevés bancaires et traiter les écarts.','Reconcile bank statements and resolve differences.',
     'HIGH','SCHOOL','write',false,547)
ON CONFLICT (code) DO UPDATE SET
    label_fr=EXCLUDED.label_fr, label_en=EXCLUDED.label_en,
    description_fr=EXCLUDED.description_fr, description_en=EXCLUDED.description_en,
    risk_level=EXCLUDED.risk_level, scope_type=EXCLUDED.scope_type,
    required_level=EXCLUDED.required_level, default_read_action=EXCLUDED.default_read_action,
    display_order=EXCLUDED.display_order, active=true, updated_at=now();

INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT s.id, r.code, a.code,
       CASE
           WHEN r.code IN ('principal','administrator','admin','school_admin','accountant') THEN true
           WHEN r.code IN ('econome','finance_officer','finance_collector') AND a.code IN ('TREASURY_ACCOUNT_VIEW','TREASURY_MOVEMENT_VIEW') THEN true
           ELSE false
       END
FROM school s
JOIN role r ON r.code IN ('principal','administrator','admin','school_admin','accountant','econome','finance_officer','finance_collector')
CROSS JOIN (VALUES
    ('TREASURY_ACCOUNT_VIEW'),('TREASURY_ACCOUNT_MANAGE'),('TREASURY_MOVEMENT_VIEW'),
    ('TREASURY_MOVEMENT_CREATE'),('TREASURY_RECONCILE')
) a(code)
ON CONFLICT (school_id, role_code, action_code)
DO UPDATE SET allowed=EXCLUDED.allowed;

WITH authorities(role_code, action_code) AS (VALUES
    ('principal','TREASURY_ACCOUNT_VIEW'),('principal','TREASURY_MOVEMENT_VIEW'),
    ('administrator','TREASURY_ACCOUNT_VIEW'),('administrator','TREASURY_ACCOUNT_MANAGE'),
    ('administrator','TREASURY_MOVEMENT_VIEW'),('administrator','TREASURY_MOVEMENT_CREATE'),
    ('administrator','TREASURY_RECONCILE'),
    ('admin','TREASURY_ACCOUNT_VIEW'),('admin','TREASURY_ACCOUNT_MANAGE'),
    ('admin','TREASURY_MOVEMENT_VIEW'),('admin','TREASURY_MOVEMENT_CREATE'),('admin','TREASURY_RECONCILE'),
    ('school_admin','TREASURY_ACCOUNT_VIEW'),('school_admin','TREASURY_ACCOUNT_MANAGE'),
    ('school_admin','TREASURY_MOVEMENT_VIEW'),('school_admin','TREASURY_MOVEMENT_CREATE'),('school_admin','TREASURY_RECONCILE'),
    ('accountant','TREASURY_ACCOUNT_VIEW'),('accountant','TREASURY_ACCOUNT_MANAGE'),
    ('accountant','TREASURY_MOVEMENT_VIEW'),('accountant','TREASURY_MOVEMENT_CREATE'),('accountant','TREASURY_RECONCILE'),
    ('econome','TREASURY_ACCOUNT_VIEW'),('econome','TREASURY_MOVEMENT_VIEW'),
    ('finance_officer','TREASURY_ACCOUNT_VIEW'),('finance_officer','TREASURY_MOVEMENT_VIEW'),
    ('finance_collector','TREASURY_ACCOUNT_VIEW')
)
INSERT INTO permission_role_action
    (school_id,role_code,action_code,effect,scope_mode,is_permanent,reason)
SELECT s.id,a.role_code,a.action_code,'ALLOW','SCHOOL_ALL',true,
       'V167 operational treasury authority'
FROM school s CROSS JOIN authorities a
JOIN role r ON r.code=a.role_code
ON CONFLICT DO NOTHING;

INSERT INTO permission_role_template_rule
    (template_code,action_code,effect,scope_mode,is_permanent,reason,display_order)
VALUES
    ('accountant','TREASURY_ACCOUNT_VIEW','ALLOW','SCHOOL_ALL',true,'Comptabilité — consulter les comptes de trésorerie',543),
    ('accountant','TREASURY_ACCOUNT_MANAGE','ALLOW','SCHOOL_ALL',true,'Comptabilité — gérer les comptes de trésorerie',544),
    ('accountant','TREASURY_MOVEMENT_VIEW','ALLOW','SCHOOL_ALL',true,'Comptabilité — consulter les mouvements',545),
    ('accountant','TREASURY_MOVEMENT_CREATE','ALLOW','SCHOOL_ALL',true,'Comptabilité — enregistrer les mouvements',546),
    ('accountant','TREASURY_RECONCILE','ALLOW','SCHOOL_ALL',true,'Comptabilité — rapprocher la trésorerie',547),
    ('principal_oversight','TREASURY_ACCOUNT_VIEW','ALLOW','SCHOOL_ALL',true,'Direction — visibilité des comptes de trésorerie',543),
    ('principal_oversight','TREASURY_MOVEMENT_VIEW','ALLOW','SCHOOL_ALL',true,'Direction — visibilité des mouvements',545)
ON CONFLICT DO NOTHING;

-- Collections may select the operational account explicitly. Existing rows
-- are backfilled from the channel's chart-account mapping when possible.
ALTER TABLE finance_payment
    ADD COLUMN IF NOT EXISTS treasury_account_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_finance_payment_treasury_account_v167'
    ) THEN
        ALTER TABLE finance_payment
            ADD CONSTRAINT fk_finance_payment_treasury_account_v167
            FOREIGN KEY (school_id, treasury_account_id)
            REFERENCES treasury_account(school_id, id) ON DELETE RESTRICT;
    END IF;
END $$;

UPDATE finance_payment p
   SET treasury_account_id=t.id
  FROM payment_channel pc
  JOIN treasury_account t
    ON t.school_id=pc.school_id AND t.chart_account_id=pc.debit_account_id
 WHERE p.school_id=pc.school_id
   AND p.payment_channel_id=pc.id
   AND p.treasury_account_id IS NULL;

CREATE INDEX idx_finance_payment_treasury_account
    ON finance_payment(school_id, treasury_account_id, payment_date DESC);

-- Keep the legacy Finance screen safe during the gradual V2 cutover. A legacy
-- payment/expense now carries the operational account and its journal link;
-- older rows remain readable with NULL links and are not silently rewritten.
ALTER TABLE payment
    ADD COLUMN IF NOT EXISTS treasury_account_id UUID,
    ADD COLUMN IF NOT EXISTS journal_entry_id UUID;
ALTER TABLE expense
    ADD COLUMN IF NOT EXISTS treasury_account_id UUID,
    ADD COLUMN IF NOT EXISTS journal_entry_id UUID,
    ADD COLUMN IF NOT EXISTS status VARCHAR(12) NOT NULL DEFAULT 'POSTED',
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES app_user(id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_payment_treasury_account_v167') THEN
        ALTER TABLE payment ADD CONSTRAINT fk_payment_treasury_account_v167
            FOREIGN KEY (school_id, treasury_account_id)
            REFERENCES treasury_account(school_id, id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_payment_journal_v167') THEN
        ALTER TABLE payment ADD CONSTRAINT fk_payment_journal_v167
            FOREIGN KEY (school_id, journal_entry_id)
            REFERENCES journal_entry(school_id, id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_expense_treasury_account_v167') THEN
        ALTER TABLE expense ADD CONSTRAINT fk_expense_treasury_account_v167
            FOREIGN KEY (school_id, treasury_account_id)
            REFERENCES treasury_account(school_id, id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_expense_journal_v167') THEN
        ALTER TABLE expense ADD CONSTRAINT fk_expense_journal_v167
            FOREIGN KEY (school_id, journal_entry_id)
            REFERENCES journal_entry(school_id, id) ON DELETE RESTRICT;
    END IF;
END $$;
CREATE INDEX idx_payment_treasury_account_v167 ON payment(school_id, treasury_account_id, paid_on DESC);
CREATE INDEX idx_expense_treasury_account_v167 ON expense(school_id, treasury_account_id, spent_on DESC, status);
