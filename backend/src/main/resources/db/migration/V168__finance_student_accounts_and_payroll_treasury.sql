-- Student account history and consolidated receipts are staff-facing finance
-- workflows. Payroll payments also need the operational treasury account that
-- was actually debited, while retaining payment_account_id for the journal.

ALTER TABLE payroll_payment
    ADD COLUMN IF NOT EXISTS treasury_account_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'fk_payroll_payment_treasury_account_v168'
    ) THEN
        ALTER TABLE payroll_payment
            ADD CONSTRAINT fk_payroll_payment_treasury_account_v168
            FOREIGN KEY (school_id, treasury_account_id)
            REFERENCES treasury_account(school_id, id) ON DELETE RESTRICT;
    END IF;
END $$;

UPDATE payroll_payment p
   SET treasury_account_id=t.id
  FROM treasury_account t
 WHERE t.school_id=p.school_id
   AND t.chart_account_id=p.payment_account_id
   AND p.treasury_account_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_payroll_payment_treasury_account_v168
    ON payroll_payment(school_id, treasury_account_id, payment_date DESC);

INSERT INTO permission_action
    (code,module,group_code,label_fr,label_en,description_fr,description_en,
     risk_level,scope_type,required_level,default_read_action,display_order)
VALUES
    ('FINANCE_STUDENT_ACCOUNT_VIEW','finance','Finance',
     'Compte élève — consulter','Student account — view',
     'Voir le solde, les versements et l’historique financier complet d’un élève.',
     'View a student balance, payments and complete financial history.',
     'LOW','SCHOOL','read',true,548),
    ('FINANCE_CONSOLIDATED_RECEIPT_CREATE','finance','Finance',
     'Reçu consolidé — générer','Consolidated receipt — generate',
     'Générer un reçu unique reprenant tous les versements d’un élève.',
     'Generate one reprintable receipt covering all payments for a student.',
     'MEDIUM','SCHOOL','write',false,549)
ON CONFLICT (code) DO UPDATE SET
    label_fr=EXCLUDED.label_fr, label_en=EXCLUDED.label_en,
    description_fr=EXCLUDED.description_fr, description_en=EXCLUDED.description_en,
    risk_level=EXCLUDED.risk_level, scope_type=EXCLUDED.scope_type,
    required_level=EXCLUDED.required_level, default_read_action=EXCLUDED.default_read_action,
    display_order=EXCLUDED.display_order, active=true, updated_at=now();

INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT s.id, r.code, a.code,
       CASE
           WHEN r.code IN ('principal','administrator','admin','school_admin','accountant',
                           'econome','finance_collector','finance_officer') THEN true
           ELSE false
       END
  FROM school s
  JOIN role r ON r.code IN ('principal','administrator','admin','school_admin','accountant',
                            'econome','finance_collector','finance_officer')
 CROSS JOIN (VALUES ('FINANCE_STUDENT_ACCOUNT_VIEW'),
                    ('FINANCE_CONSOLIDATED_RECEIPT_CREATE')) a(code)
ON CONFLICT (school_id, role_code, action_code)
DO UPDATE SET allowed=EXCLUDED.allowed;

WITH authorities(role_code, action_code) AS (VALUES
    ('principal','FINANCE_STUDENT_ACCOUNT_VIEW'),
    ('principal','FINANCE_CONSOLIDATED_RECEIPT_CREATE'),
    ('administrator','FINANCE_STUDENT_ACCOUNT_VIEW'),
    ('administrator','FINANCE_CONSOLIDATED_RECEIPT_CREATE'),
    ('admin','FINANCE_STUDENT_ACCOUNT_VIEW'),
    ('admin','FINANCE_CONSOLIDATED_RECEIPT_CREATE'),
    ('school_admin','FINANCE_STUDENT_ACCOUNT_VIEW'),
    ('school_admin','FINANCE_CONSOLIDATED_RECEIPT_CREATE'),
    ('accountant','FINANCE_STUDENT_ACCOUNT_VIEW'),
    ('accountant','FINANCE_CONSOLIDATED_RECEIPT_CREATE'),
    ('econome','FINANCE_STUDENT_ACCOUNT_VIEW'),
    ('econome','FINANCE_CONSOLIDATED_RECEIPT_CREATE'),
    ('finance_collector','FINANCE_STUDENT_ACCOUNT_VIEW'),
    ('finance_collector','FINANCE_CONSOLIDATED_RECEIPT_CREATE'),
    ('finance_officer','FINANCE_STUDENT_ACCOUNT_VIEW'),
    ('finance_officer','FINANCE_CONSOLIDATED_RECEIPT_CREATE')
)
INSERT INTO permission_role_action
    (school_id,role_code,action_code,effect,scope_mode,is_permanent,reason)
SELECT s.id,a.role_code,a.action_code,'ALLOW','SCHOOL_ALL',true,
       'V168 student finance account and consolidated receipt authority'
  FROM school s
 CROSS JOIN authorities a
 JOIN role r ON r.code=a.role_code
ON CONFLICT DO NOTHING;

INSERT INTO permission_role_template_rule
    (template_code,action_code,effect,scope_mode,is_permanent,reason,display_order)
VALUES
    ('accountant','FINANCE_STUDENT_ACCOUNT_VIEW','ALLOW','SCHOOL_ALL',true,
     'Comptabilité — consulter le compte financier élève',548),
    ('accountant','FINANCE_CONSOLIDATED_RECEIPT_CREATE','ALLOW','SCHOOL_ALL',true,
     'Comptabilité — générer un reçu consolidé',549),
    ('principal_oversight','FINANCE_STUDENT_ACCOUNT_VIEW','ALLOW','SCHOOL_ALL',true,
     'Direction — consulter le compte financier élève',548),
    ('principal_oversight','FINANCE_CONSOLIDATED_RECEIPT_CREATE','ALLOW','SCHOOL_ALL',true,
     'Direction — générer un reçu consolidé',549)
ON CONFLICT DO NOTHING;

