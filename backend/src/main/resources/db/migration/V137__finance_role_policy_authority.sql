-- Gate 10: materialize the reviewed Finance V2 separation-of-duties profiles.
--
-- V118 defined the finance_collector/accountant/bursar templates, but the
-- compatibility backfill ran before all finance roles were available and the
-- live tenant consequently had incomplete permission_role_action rows.  This
-- migration is deliberately limited to the finance personas used by the
-- lifecycle acceptance fixture.  It does not add finance authority to
-- teachers, parents, registrar, health, or Direction roles.

INSERT INTO role (code, label_fr, label_en, builtin)
VALUES ('finance_collector', 'Collecteur / caissier', 'Finance collector / cashier', false)
ON CONFLICT (code) DO NOTHING;

-- Keep the legacy module bridge usable for the finance V2 controllers while
-- the explicit action rows below remain the authoritative least-privilege
-- decision for each operation.
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT s.id, x.role_code, x.module, x.level
  FROM school s
 CROSS JOIN (VALUES
     ('accountant', 'finance', 'write'),
     ('econome', 'finance', 'write'),
     ('finance_collector', 'finance', 'write'),
     ('accountant', 'hr', 'read'),
     ('econome', 'hr', 'write')
 ) AS x(role_code, module, level)
ON CONFLICT (school_id, role_code, module)
DO UPDATE SET level = EXCLUDED.level;

-- Existing V106/V110 legacy rows gave accountant a broad boolean matrix and
-- gave econome a different partial matrix.  Reconcile only these finance
-- personas before adding the explicit allow-list; no ordinary-role rows are
-- changed and every unlisted finance/HR action remains explicitly denied.
UPDATE permission_action_grant pag
   SET allowed = false
  FROM permission_action pa
 WHERE pa.code = pag.action_code
   AND pa.module IN ('finance', 'hr')
   AND pag.role_code IN ('accountant', 'econome', 'finance_collector');

WITH authorities(role_code, action_code) AS (VALUES
    -- Accountant: accounting, fee/charge/document/report operations; no
    -- cashier session ownership or payment collection.
    ('accountant','FINANCE_OVERVIEW_VIEW'),
    ('accountant','FEE_CONFIGURE'),
    ('accountant','FEE_TYPE_MANAGE'),
    ('accountant','FEE_PLAN_DRAFT'),
    ('accountant','FEE_PLAN_ACTIVATE'),
    ('accountant','CHARGE_PREVIEW'),
    ('accountant','CHARGE_GENERATE'),
    ('accountant','CHARGE_ADJUST'),
    ('accountant','FEE_WAIVE_REQUEST'),
    ('accountant','FEE_WAIVE_APPROVE'),
    ('accountant','PAYMENT_VIEW'),
    ('accountant','PAYMENT_REVERSE'),
    ('accountant','REFUND_REQUEST'),
    ('accountant','REFUND_APPROVE'),
    ('accountant','FINANCE_DOCUMENT_VIEW'),
    ('accountant','FINANCE_DOCUMENT_GENERATE'),
    ('accountant','FINANCE_DOCUMENT_VOID'),
    ('accountant','FINANCE_DOCUMENT_SUPERSEDE'),
    ('accountant','FINANCE_DOCUMENT_BATCH'),
    ('accountant','ACCOUNT_MANAGE'),
    ('accountant','POSTING_RULE_MANAGE'),
    ('accountant','LEDGER_POST'),
    ('accountant','LEDGER_REVERSE'),
    ('accountant','LEDGER_CLOSE'),
    ('accountant','LEDGER_REOPEN'),
    ('accountant','FINANCE_EXPENSE_VIEW'),
    ('accountant','FINANCE_EXPENSE_CREATE'),
    ('accountant','FINANCE_EXPENSE_DELETE'),
    ('accountant','FINANCE_REPORT_VIEW'),
    ('accountant','FINANCE_EXPORT'),
    ('accountant','PAYROLL_VIEW'),

    -- Bursar/econome: configuration and approval operations, including
    -- cashier-close approval and payroll approval/payment; never collection.
    ('econome','FINANCE_OVERVIEW_VIEW'),
    ('econome','FEE_CONFIGURE'),
    ('econome','FEE_TYPE_MANAGE'),
    ('econome','FEE_PLAN_DRAFT'),
    ('econome','FEE_PLAN_ACTIVATE'),
    ('econome','CHARGE_PREVIEW'),
    ('econome','CHARGE_GENERATE'),
    ('econome','CHARGE_ADJUST'),
    ('econome','FEE_WAIVE_REQUEST'),
    ('econome','FEE_WAIVE_APPROVE'),
    ('econome','PAYMENT_VIEW'),
    ('econome','REFUND_REQUEST'),
    ('econome','REFUND_APPROVE'),
    ('econome','CASHIER_SESSION_APPROVE'),
    ('econome','FINANCE_DOCUMENT_VIEW'),
    ('econome','FINANCE_DOCUMENT_GENERATE'),
    ('econome','FINANCE_DOCUMENT_VOID'),
    ('econome','FINANCE_DOCUMENT_SUPERSEDE'),
    ('econome','FINANCE_DOCUMENT_BATCH'),
    ('econome','FINANCE_EXPENSE_VIEW'),
    ('econome','FINANCE_EXPENSE_CREATE'),
    ('econome','FINANCE_EXPENSE_DELETE'),
    ('econome','FINANCE_REPORT_VIEW'),
    ('econome','FINANCE_EXPORT'),
    ('econome','PAYROLL_VIEW'),
    ('econome','PAYROLL_REVIEW'),
    ('econome','PAYROLL_APPROVE'),
    ('econome','PAYROLL_PAY'),
    ('econome','PAYSLIP_VIEW_ALL'),
    ('econome','PAYSLIP_REGENERATE'),

    -- Collector: cashier lifecycle, payer lookup, collection and receipt
    -- visibility only.  No plan, refund, report, or ledger authority.
    ('finance_collector','FINANCE_OVERVIEW_VIEW'),
    ('finance_collector','STUDENT_DIRECTORY_VIEW'),
    ('finance_collector','PAYMENT_VIEW'),
    ('finance_collector','PAYMENT_COLLECT'),
    ('finance_collector','CASHIER_SESSION_OPEN'),
    ('finance_collector','CASHIER_SESSION_CLOSE'),
    ('finance_collector','FINANCE_DOCUMENT_VIEW')
)
INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT s.id, a.role_code, a.action_code, true
  FROM school s
 CROSS JOIN authorities a
ON CONFLICT (school_id, role_code, action_code)
DO UPDATE SET allowed = EXCLUDED.allowed;

-- V2 resource scope is school-wide for the finance workspace.  Student-scoped
-- finance actions still resolve the server-side enrollment context in
-- FinancePolicyService; SCHOOL_ALL here is the reviewed role boundary, not a
-- client-supplied wildcard.
WITH authorities(role_code, action_code, scope_mode) AS (VALUES
    ('accountant','FINANCE_OVERVIEW_VIEW','SCHOOL_ALL'),
    ('accountant','FEE_CONFIGURE','SCHOOL_ALL'),
    ('accountant','FEE_TYPE_MANAGE','SCHOOL_ALL'),
    ('accountant','FEE_PLAN_DRAFT','SCHOOL_ALL'),
    ('accountant','FEE_PLAN_ACTIVATE','SCHOOL_ALL'),
    ('accountant','CHARGE_PREVIEW','SCHOOL_ALL'),
    ('accountant','CHARGE_GENERATE','SCHOOL_ALL'),
    ('accountant','CHARGE_ADJUST','SCHOOL_ALL'),
    ('accountant','FEE_WAIVE_REQUEST','SCHOOL_ALL'),
    ('accountant','FEE_WAIVE_APPROVE','SCHOOL_ALL'),
    ('accountant','PAYMENT_VIEW','SCHOOL_ALL'),
    ('accountant','PAYMENT_REVERSE','SCHOOL_ALL'),
    ('accountant','REFUND_REQUEST','SCHOOL_ALL'),
    ('accountant','REFUND_APPROVE','SCHOOL_ALL'),
    ('accountant','FINANCE_DOCUMENT_VIEW','SCHOOL_ALL'),
    ('accountant','FINANCE_DOCUMENT_GENERATE','SCHOOL_ALL'),
    ('accountant','FINANCE_DOCUMENT_VOID','SCHOOL_ALL'),
    ('accountant','FINANCE_DOCUMENT_SUPERSEDE','SCHOOL_ALL'),
    ('accountant','FINANCE_DOCUMENT_BATCH','SCHOOL_ALL'),
    ('accountant','ACCOUNT_MANAGE','SCHOOL_ALL'),
    ('accountant','POSTING_RULE_MANAGE','SCHOOL_ALL'),
    ('accountant','LEDGER_POST','SCHOOL_ALL'),
    ('accountant','LEDGER_REVERSE','SCHOOL_ALL'),
    ('accountant','LEDGER_CLOSE','SCHOOL_ALL'),
    ('accountant','LEDGER_REOPEN','SCHOOL_ALL'),
    ('accountant','FINANCE_EXPENSE_VIEW','SCHOOL_ALL'),
    ('accountant','FINANCE_EXPENSE_CREATE','SCHOOL_ALL'),
    ('accountant','FINANCE_EXPENSE_DELETE','SCHOOL_ALL'),
    ('accountant','FINANCE_REPORT_VIEW','SCHOOL_ALL'),
    ('accountant','FINANCE_EXPORT','SCHOOL_ALL'),
    ('accountant','PAYROLL_VIEW','SCHOOL_ALL'),
    ('econome','FINANCE_OVERVIEW_VIEW','SCHOOL_ALL'),
    ('econome','FEE_CONFIGURE','SCHOOL_ALL'),
    ('econome','FEE_TYPE_MANAGE','SCHOOL_ALL'),
    ('econome','FEE_PLAN_DRAFT','SCHOOL_ALL'),
    ('econome','FEE_PLAN_ACTIVATE','SCHOOL_ALL'),
    ('econome','CHARGE_PREVIEW','SCHOOL_ALL'),
    ('econome','CHARGE_GENERATE','SCHOOL_ALL'),
    ('econome','CHARGE_ADJUST','SCHOOL_ALL'),
    ('econome','FEE_WAIVE_REQUEST','SCHOOL_ALL'),
    ('econome','FEE_WAIVE_APPROVE','SCHOOL_ALL'),
    ('econome','PAYMENT_VIEW','SCHOOL_ALL'),
    ('econome','REFUND_REQUEST','SCHOOL_ALL'),
    ('econome','REFUND_APPROVE','SCHOOL_ALL'),
    ('econome','CASHIER_SESSION_APPROVE','SCHOOL_ALL'),
    ('econome','FINANCE_DOCUMENT_VIEW','SCHOOL_ALL'),
    ('econome','FINANCE_DOCUMENT_GENERATE','SCHOOL_ALL'),
    ('econome','FINANCE_DOCUMENT_VOID','SCHOOL_ALL'),
    ('econome','FINANCE_DOCUMENT_SUPERSEDE','SCHOOL_ALL'),
    ('econome','FINANCE_DOCUMENT_BATCH','SCHOOL_ALL'),
    ('econome','FINANCE_EXPENSE_VIEW','SCHOOL_ALL'),
    ('econome','FINANCE_EXPENSE_CREATE','SCHOOL_ALL'),
    ('econome','FINANCE_EXPENSE_DELETE','SCHOOL_ALL'),
    ('econome','FINANCE_REPORT_VIEW','SCHOOL_ALL'),
    ('econome','FINANCE_EXPORT','SCHOOL_ALL'),
    ('econome','PAYROLL_VIEW','SCHOOL_ALL'),
    ('econome','PAYROLL_REVIEW','SCHOOL_ALL'),
    ('econome','PAYROLL_APPROVE','SCHOOL_ALL'),
    ('econome','PAYROLL_PAY','SCHOOL_ALL'),
    ('econome','PAYSLIP_VIEW_ALL','SCHOOL_ALL'),
    ('econome','PAYSLIP_REGENERATE','SCHOOL_ALL'),
    ('finance_collector','FINANCE_OVERVIEW_VIEW','SCHOOL_ALL'),
    ('finance_collector','STUDENT_DIRECTORY_VIEW','SCHOOL_ALL'),
    ('finance_collector','PAYMENT_VIEW','SCHOOL_ALL'),
    ('finance_collector','PAYMENT_COLLECT','SCHOOL_ALL'),
    ('finance_collector','CASHIER_SESSION_OPEN','SCHOOL_ALL'),
    ('finance_collector','CASHIER_SESSION_CLOSE','SCHOOL_ALL'),
    ('finance_collector','FINANCE_DOCUMENT_VIEW','SCHOOL_ALL')
), updated AS (
    UPDATE permission_role_action p
       SET effect='ALLOW', scope_mode=a.scope_mode, scope_payload=NULL,
           is_permanent=true,
           reason='V137 reviewed Finance V2 separation-of-duties authority',
           updated_at=now()
      FROM school s
      JOIN authorities a ON true
     WHERE p.school_id=s.id
       AND p.role_code=a.role_code
       AND p.action_code=a.action_code
       AND p.effective_from IS NULL
       AND p.effective_to IS NULL
    RETURNING p.school_id, p.role_code, p.action_code, a.scope_mode
)
INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode, is_permanent, reason)
SELECT s.id, a.role_code, a.action_code, 'ALLOW', a.scope_mode, true,
       'V137 reviewed Finance V2 separation-of-duties authority'
  FROM school s
 CROSS JOIN authorities a
 WHERE NOT EXISTS (
       SELECT 1
         FROM permission_role_action p
        WHERE p.school_id=s.id
          AND p.role_code=a.role_code
          AND p.action_code=a.action_code
          AND p.scope_mode=a.scope_mode
          AND p.effective_from IS NULL
          AND p.effective_to IS NULL
 )
ON CONFLICT DO NOTHING;
