-- Demo-only Wave 1 accounting fixture. It is repeatable and never belongs in
-- production migrations; the fixed demo school is created by V4__seed_core.sql.
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
WHERE s.id = '11111111-1111-1111-1111-111111111111'
ON CONFLICT (school_id, code) DO NOTHING;

INSERT INTO accounting_period
    (school_id, code, name_fr, name_en, start_date, end_date, academic_session_id, status)
SELECT s.school_id, to_char(month_start, 'YYYY-MM'),
       'Période ' || to_char(month_start, 'YYYY-MM'), 'Period ' || to_char(month_start, 'YYYY-MM'),
       month_start::date, LEAST((month_start + interval '1 month - 1 day')::date, s.end_date),
       s.id, CASE WHEN s.status IN ('OPEN','DRAFT') THEN 'OPEN' ELSE 'CLOSED' END
FROM academic_session s
CROSS JOIN LATERAL generate_series(date_trunc('month', s.start_date)::date,
                                    date_trunc('month', s.end_date)::date,
                                    interval '1 month') month_start
WHERE s.school_id = '11111111-1111-1111-1111-111111111111'
  AND month_start::date <= s.end_date
ON CONFLICT (school_id, code) DO NOTHING;

INSERT INTO document_sequence (school_id, document_type, period_key, prefix, next_number, padding)
SELECT p.school_id, 'JOURNAL', p.code, 'JRN/' || p.code || '/', 1, 6
FROM accounting_period p
WHERE p.school_id = '11111111-1111-1111-1111-111111111111'
ON CONFLICT (school_id, document_type, period_key) DO NOTHING;

INSERT INTO posting_rule (school_id, event_type, side, target_account_id, priority, enabled)
SELECT '11111111-1111-1111-1111-111111111111', r.event_type, r.side, a.id, 0, true
FROM (VALUES
    ('FEE_CHARGE','DEBIT','1100'), ('FEE_CHARGE','CREDIT','4000'),
    ('PAYMENT_CASH','DEBIT','1000'), ('PAYMENT_CASH','CREDIT','1100'),
    ('EXPENSE_POST','DEBIT','6900'), ('EXPENSE_POST','CREDIT','1000')
) AS r(event_type, side, code)
JOIN chart_of_account a ON a.school_id = '11111111-1111-1111-1111-111111111111' AND a.code = r.code
WHERE NOT EXISTS (
    SELECT 1 FROM posting_rule p
     WHERE p.school_id = a.school_id AND p.event_type=r.event_type AND p.side=r.side
       AND p.priority=0 AND p.scope_code IS NULL AND p.fee_type_code IS NULL
       AND p.payment_channel_code IS NULL AND p.component_code IS NULL
);

INSERT INTO permission_grant (school_id, role_code, module, level)
VALUES ('11111111-1111-1111-1111-111111111111', 'accountant', 'finance', 'write')
ON CONFLICT DO NOTHING;

INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT '11111111-1111-1111-1111-111111111111', r.code, a.action_code,
       CASE WHEN r.code IN ('principal','accountant') THEN true
            WHEN r.code='econome' AND a.action_code IN ('FINANCE_OVERVIEW_VIEW','FINANCE_REPORT_VIEW','FINANCE_EXPORT') THEN true
            ELSE false END
FROM role r
JOIN (VALUES ('principal'),('econome'),('accountant')) allowed_roles(code) ON allowed_roles.code=r.code
CROSS JOIN (VALUES
    ('FINANCE_OVERVIEW_VIEW'),('FEE_TYPE_MANAGE'),('FEE_PLAN_DRAFT'),('FEE_PLAN_ACTIVATE'),
    ('CHARGE_PREVIEW'),('CHARGE_GENERATE'),('CHARGE_ADJUST'),('FEE_WAIVE_REQUEST'),('FEE_WAIVE_APPROVE'),
    ('PAYMENT_COLLECT'),('PAYMENT_REVERSE'),('REFUND_REQUEST'),('REFUND_APPROVE'),('CASHIER_SESSION_CLOSE'),
    ('FINANCE_DOCUMENT_GENERATE'),('FINANCE_DOCUMENT_VOID'),('ACCOUNT_MANAGE'),('POSTING_RULE_MANAGE'),
    ('LEDGER_POST'),('LEDGER_REVERSE'),('LEDGER_CLOSE'),('LEDGER_REOPEN'),('PAYROLL_CALCULATE'),
    ('PAYROLL_REVIEW'),('PAYROLL_APPROVE'),('PAYROLL_PAY'),('PAYSLIP_VIEW_ALL'),('FINANCE_REPORT_VIEW'),('FINANCE_EXPORT')
) actions(action_code)
ON CONFLICT (school_id, role_code, action_code) DO UPDATE SET allowed=EXCLUDED.allowed;
