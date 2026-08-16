-- One-time sanitisation for the local production replica.
--
-- This file is intentionally explicit. It is run only against the newly
-- cloned bbc-production-replica-db container; it must never be run against
-- the current simulation database or the read-only source replica.

BEGIN;
SET LOCAL statement_timeout = 0;
SET LOCAL lock_timeout = 0;
SET LOCAL client_encoding = 'UTF8';

DO $$
BEGIN
    IF current_database() <> 'bbc_sms' THEN
        RAISE EXCEPTION 'Unexpected database: %. Expected bbc_sms.', current_database();
    END IF;

    IF NOT EXISTS (SELECT 1 FROM app_user WHERE username = 'admin' AND active) THEN
        RAISE EXCEPTION 'Safety check failed: active admin account is missing.';
    END IF;

    IF (SELECT count(*) FROM student WHERE matricule IN ('BBC-1741','BBC-1742','BBC-1743','BBC-1744','BBC-1745')) <> 5 THEN
        RAISE EXCEPTION 'Safety check failed: expected five demonstration students BBC-1741..BBC-1745.';
    END IF;
END;
$$;

CREATE TEMP TABLE _replica_admin ON COMMIT DROP AS
SELECT id
FROM app_user
WHERE username = 'admin'
LIMIT 1;

CREATE TEMP TABLE _replica_demo_students ON COMMIT DROP AS
SELECT id
FROM student
WHERE matricule IN ('BBC-1741','BBC-1742','BBC-1743','BBC-1744','BBC-1745');

-- These are configuration records that are retained, but their references to
-- test staff / test generation jobs must be cleared before staff is removed.
UPDATE academic_assessment
SET generation_batch_id = NULL
WHERE generation_batch_id IS NOT NULL;

UPDATE department
SET head_employee_id = NULL
WHERE head_employee_id IS NOT NULL;

UPDATE timetable_class_config
SET homeroom_teacher_id = NULL,
    status = 'DRAFT',
    published_at = NULL,
    published_by = NULL;

UPDATE timetable_slot
SET teacher_id = NULL,
    published_teacher_id = NULL,
    assignment_id = NULL,
    assignment_version = NULL,
    published_assignment_id = NULL,
    published_assignment_version = NULL;

UPDATE timetable_version
SET status = 'DRAFT',
    published_at = NULL,
    published_by = NULL;

UPDATE guardian
SET app_user_id = NULL
WHERE app_user_id IS NOT NULL;

UPDATE app_user
SET employee_id = NULL
WHERE employee_id IS NOT NULL;

UPDATE student_enrollment
SET promotion_decision_id = NULL
WHERE promotion_decision_id IS NOT NULL;

-- Keep the administrator's fine-grained permission changes exactly as they
-- are. User-specific rules for deleted teacher/accountant/parent accounts are
-- removed; role-level rules below remain untouched.
DELETE FROM permission_user_action
WHERE user_id NOT IN (SELECT id FROM _replica_admin);

-- Remove operational/test rows. Configuration/reference rows are deliberately
-- not in this list: sessions, terms, periods/windows, classes, subjects,
-- class-subject coefficients, assessment definitions, fee catalogue/plans,
-- accounting setup, timetable periods/shape, branding, templates, roles and
-- access-control rules all remain.
TRUNCATE TABLE
    academic_access_delegation,
    academic_copy_run,
    academic_grade,
    academic_grade_packet,
    academic_grade_packet_transition,
    academic_secondary_migration_conflict,
    assignment_discrepancy,
    attendance_mark,
    attendance_notification,
    attendance_period_adjustment,
    attendance_record,
    attendance_session,
    attendance_session_event,
    alert,
    audit_event,
    audit_log,
    bulletin_batch_artifact,
    bulletin_batch_item,
    bulletin_batch_job,
    bulletin_validation,
    bulletin_version,
    cashier_session,
    charge_adjustment,
    charge_generation_job,
    charge_generation_result,
    charge_installment,
    class_resource_item,
    class_resource_publication,
    class_teacher_assignment,
    correspondence,
    coursebook_entry,
    discipline_incident,
    employee_payroll,
    employee_payroll_line,
    employee_role,
    expense,
    family_import_job,
    family_import_row,
    finance_invoice,
    finance_invoice_batch_job,
    finance_invoice_batch_result,
    finance_invoice_line,
    finance_payment,
    finance_receipt,
    finance_receipt_line,
    generated_document,
    guardian_account_token,
    health_record,
    idempotency_key,
    infirmary_visit,
    journal_entry,
    journal_line,
    journey_entry,
    journey_entry_revision,
    journey_event,
    leave_request,
    orientation_decision,
    parent_student,
    parent_suggestion,
    payment,
    payment_allocation,
    payment_reversal_request,
    payroll_payment,
    payroll_payslip_job,
    payroll_payslip_job_result,
    payroll_period,
    payroll_run,
    payslip,
    permission_compatibility_report,
    permission_policy_audit,
    permission_policy_shadow_decision,
    promotion_decision_history,
    promotion_register,
    promotion_transition_event,
    provider_callback,
    provider_transaction,
    reconciliation_item,
    refund_request,
    refund_transaction,
    secondary_competency_mark,
    staff_application,
    student_activity,
    student_charge,
    student_credit_ledger,
    student_document,
    student_fee,
    student_fee_election,
    student_fee_override,
    student_period_conduct,
    subject_result_comment,
    teacher_class,
    teacher_subject,
    timetable_substitution,
    timetable_teacher_availability,
    timetable_teacher_qualification,
    timetable_teacher_workload_policy
RESTART IDENTITY;

-- Promotion decisions are referenced by retained enrollments, so clear the
-- reference first and then remove the now-empty operational decision table.
DELETE FROM promotion_decision;
DELETE FROM promotion_batch;

-- Remove the five deliberately-created portal demonstration students while
-- preserving the 740 source/imported students (BBC-1001..BBC-1740).
DELETE FROM student_guardian
WHERE student_id IN (SELECT id FROM _replica_demo_students);

DELETE FROM student_enrollment
WHERE student_id IN (SELECT id FROM _replica_demo_students);

DELETE FROM student
WHERE id IN (SELECT id FROM _replica_demo_students);

-- The two demonstration guardians now have no student relationship. Remove
-- them, and remove any other orphan contact row, while retaining every
-- guardian linked to a retained source student.
DELETE FROM guardian g
WHERE NOT EXISTS (
    SELECT 1
    FROM student_guardian sg
    WHERE sg.guardian_id = g.id
);

-- The generation log was cleared together with operational rows. This second
-- statement is harmless for empty databases and documents the intended state.
DELETE FROM academic_assessment_generation_batch;

-- Delete every non-administrator login and every staff identity. Role/action
-- definitions and the administrator's 89 user-specific overrides remain.
DELETE FROM app_user
WHERE id NOT IN (SELECT id FROM _replica_admin);

DELETE FROM employee;

-- Reset the sequences that are safe to reset; UUID tables do not need one.
-- The database remains structurally identical to the source clone.

COMMIT;
