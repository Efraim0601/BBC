-- Align the two management personas with the behavior documented by the role
-- guides.  This migration repairs role defaults only; it does not create any
-- per-user override.

-- Principal: Staff is an operational workspace inside the active, assigned
-- parcours.  Access Control remains protected by the administrator-only
-- invariant and the explicit DENY rules created by V153.
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT s.id, 'principal', 'hr', 'write'
  FROM school s
ON CONFLICT (school_id, role_code, module)
DO UPDATE SET level = EXCLUDED.level;

-- Accountant: retain the finance workspace, read-only staff lookup for
-- payroll, student lookup, dashboard and reports.  Remove unrelated local
-- setup envelopes that exposed academic, attendance, document, settings and
-- timetable modules.
DELETE FROM permission_grant
 WHERE role_code = 'accountant'
   AND module NOT IN ('finance', 'hr', 'students', 'dashboard', 'reports');

INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT s.id, x.role_code, x.module, x.level
  FROM school s
 CROSS JOIN (VALUES
     ('accountant', 'finance', 'write'),
     ('accountant', 'hr', 'read'),
     ('accountant', 'students', 'read'),
     ('accountant', 'dashboard', 'read'),
     ('accountant', 'reports', 'read')
 ) AS x(role_code, module, level)
ON CONFLICT (school_id, role_code, module)
DO UPDATE SET level = EXCLUDED.level;

-- Revoke every out-of-mandate Accountant role rule that may have been added
-- during setup/data-entry testing.  Student lookup stays read-only; finance,
-- payroll and HR_VIEW are restored explicitly below.
WITH revoked AS (
    SELECT code
      FROM permission_action
     WHERE module IN ('academic', 'presence', 'discipline', 'documents', 'settings', 'timetable')
        OR (module = 'students' AND code NOT IN (
            'STUDENT_DIRECTORY_VIEW', 'STUDENT_PROFILE_VIEW', 'STUDENT_PHOTO_VIEW',
            'ENROLLMENT_VIEW', 'GUARDIAN_VIEW', 'GUARDIAN_DIRECTORY_SEARCH'
        ))
        OR code = 'HR_MANAGE'
)
UPDATE permission_role_action p
   SET effect = 'INHERIT', scope_mode = 'NONE', scope_payload = NULL,
       is_permanent = true,
       reason = 'Accountant least-privilege role alignment',
       version = p.version + 1, updated_at = now()
  FROM revoked r
 WHERE p.role_code = 'accountant'
   AND p.action_code = r.code
   AND p.effective_from IS NULL
   AND p.effective_to IS NULL;

WITH revoked AS (
    SELECT code
      FROM permission_action
     WHERE module IN ('academic', 'presence', 'discipline', 'documents', 'settings', 'timetable')
        OR (module = 'students' AND code NOT IN (
            'STUDENT_DIRECTORY_VIEW', 'STUDENT_PROFILE_VIEW', 'STUDENT_PHOTO_VIEW',
            'ENROLLMENT_VIEW', 'GUARDIAN_VIEW', 'GUARDIAN_DIRECTORY_SEARCH'
        ))
        OR code = 'HR_MANAGE'
)
UPDATE permission_action_grant legacy
   SET allowed = false
  FROM revoked r
 WHERE legacy.role_code = 'accountant'
   AND legacy.action_code = r.code;

-- The complete operational Accountant envelope used by the current Finance
-- UX.  HR_VIEW is intentionally read-only; no student/setup/timetable write
-- action is present in this list.
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
    ('accountant','PAYMENT_COLLECT','SCHOOL_ALL'),
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
    ('accountant','TREASURY_ACCOUNT_VIEW','SCHOOL_ALL'),
    ('accountant','TREASURY_ACCOUNT_MANAGE','SCHOOL_ALL'),
    ('accountant','TREASURY_MOVEMENT_VIEW','SCHOOL_ALL'),
    ('accountant','TREASURY_MOVEMENT_CREATE','SCHOOL_ALL'),
    ('accountant','TREASURY_RECONCILE','SCHOOL_ALL'),
    ('accountant','FINANCE_STUDENT_ACCOUNT_VIEW','SCHOOL_ALL'),
    ('accountant','FINANCE_CONSOLIDATED_RECEIPT_CREATE','SCHOOL_ALL'),
    ('accountant','PAYROLL_VIEW','SCHOOL_ALL'),
    ('accountant','PAYROLL_PERIOD_MANAGE','SCHOOL_ALL'),
    ('accountant','PAYROLL_COMPONENT_MANAGE','SCHOOL_ALL'),
    ('accountant','PAYROLL_CALCULATE','SCHOOL_ALL'),
    ('accountant','PAYROLL_ADJUST','SCHOOL_ALL'),
    ('accountant','PAYROLL_REVIEW','SCHOOL_ALL'),
    ('accountant','PAYROLL_APPROVE','SCHOOL_ALL'),
    ('accountant','PAYROLL_PAY','SCHOOL_ALL'),
    ('accountant','PAYROLL_VOID','SCHOOL_ALL'),
    ('accountant','PAYSLIP_VIEW_ALL','SCHOOL_ALL'),
    ('accountant','PAYSLIP_REGENERATE','SCHOOL_ALL'),
    ('accountant','HR_VIEW','SCHOOL_ALL'),
    ('accountant','STUDENT_DIRECTORY_VIEW','SCHOOL_ALL'),
    ('accountant','STUDENT_PROFILE_VIEW','SCHOOL_ALL'),
    ('accountant','STUDENT_PHOTO_VIEW','SCHOOL_ALL'),
    ('accountant','ENROLLMENT_VIEW','SCHOOL_ALL'),
    ('accountant','GUARDIAN_VIEW','SCHOOL_ALL'),
    ('accountant','GUARDIAN_DIRECTORY_SEARCH','SCHOOL_ALL'),
    ('principal','HR_VIEW','PARCOURS_ALLOWED'),
    ('principal','HR_MANAGE','PARCOURS_ALLOWED')
), updated AS (
    UPDATE permission_role_action p
       SET effect = 'ALLOW', scope_mode = a.scope_mode, scope_payload = NULL,
           is_permanent = true,
           reason = 'V177 Principal and Accountant role alignment',
           version = p.version + 1, updated_at = now()
      FROM authorities a
     WHERE p.role_code = a.role_code
       AND p.action_code = a.action_code
       AND p.effective_from IS NULL
       AND p.effective_to IS NULL
    RETURNING p.school_id, p.role_code, p.action_code
)
INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode, is_permanent, reason)
SELECT s.id, a.role_code, a.action_code, 'ALLOW', a.scope_mode, true,
       'V177 Principal and Accountant role alignment'
  FROM school s
 CROSS JOIN authorities a
 WHERE NOT EXISTS (
       SELECT 1
         FROM permission_role_action p
        WHERE p.school_id = s.id
          AND p.role_code = a.role_code
          AND p.action_code = a.action_code
          AND p.effective_from IS NULL
          AND p.effective_to IS NULL
 )
ON CONFLICT DO NOTHING;

WITH authorities(role_code, action_code) AS (VALUES
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
    ('accountant','PAYMENT_COLLECT'),
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
    ('accountant','TREASURY_ACCOUNT_VIEW'),
    ('accountant','TREASURY_ACCOUNT_MANAGE'),
    ('accountant','TREASURY_MOVEMENT_VIEW'),
    ('accountant','TREASURY_MOVEMENT_CREATE'),
    ('accountant','TREASURY_RECONCILE'),
    ('accountant','FINANCE_STUDENT_ACCOUNT_VIEW'),
    ('accountant','FINANCE_CONSOLIDATED_RECEIPT_CREATE'),
    ('accountant','PAYROLL_VIEW'),
    ('accountant','PAYROLL_PERIOD_MANAGE'),
    ('accountant','PAYROLL_COMPONENT_MANAGE'),
    ('accountant','PAYROLL_CALCULATE'),
    ('accountant','PAYROLL_ADJUST'),
    ('accountant','PAYROLL_REVIEW'),
    ('accountant','PAYROLL_APPROVE'),
    ('accountant','PAYROLL_PAY'),
    ('accountant','PAYROLL_VOID'),
    ('accountant','PAYSLIP_VIEW_ALL'),
    ('accountant','PAYSLIP_REGENERATE'),
    ('accountant','HR_VIEW'),
    ('accountant','STUDENT_DIRECTORY_VIEW'),
    ('accountant','STUDENT_PROFILE_VIEW'),
    ('accountant','STUDENT_PHOTO_VIEW'),
    ('accountant','ENROLLMENT_VIEW'),
    ('accountant','GUARDIAN_VIEW'),
    ('accountant','GUARDIAN_DIRECTORY_SEARCH'),
    ('principal','HR_VIEW'),
    ('principal','HR_MANAGE')
)
INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT s.id, a.role_code, a.action_code, true
  FROM school s
 CROSS JOIN authorities a
ON CONFLICT (school_id, role_code, action_code)
DO UPDATE SET allowed = EXCLUDED.allowed;
