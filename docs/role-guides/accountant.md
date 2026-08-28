# Accountant guide

*Collect payments, reconcile treasury, monitor receivables, and run payroll across every parcours.*

**Scope:** All parcours; no permission administration.

The accountant is global: finance covers Kindergarten, Primary, and Secondary in both language sections. Every payment, expense, deposit, withdrawal, or transfer must use the money account that was actually affected.

## What this role can do

- **Collections:** Create payments, choose the credited account, issue receipts, export, and review payment history.
- **Student accounts:** Filter by class, view billed/paid/balance/credit, and generate a consolidated receipt across all instalments.
- **Treasury:** Create/archive accounts, record deposits, withdrawals, and transfers, then reconcile balances.
- **Expenses and fees:** Record expenses paid from an account and configure fee grids, types, and plans when the related actions are enabled.
- **Payroll:** Configure periods and components, calculate, review, approve, pay, and issue payslips.
- **Accounting and reporting:** Use the chart of accounts, journals, trial balance, general ledger, reconciliation, and contextual finance reports.

## Daily procedures

### Record a payment

Route: `/finance`

1. Click New payment and choose the class first, then the student.
2. Enter amount and date; choose the method used by the family.
3. Always choose the cash or bank account that actually received the money.
4. Enter the reference when required, then generate the receipt.
5. In the receipt dialog, use Download PDF or Print.

> **Remember:** A legacy payment can show Credited account — when it predates integrated treasury; every new payment must identify the account.

### Find a payment

Route: `/finance`

1. In Payments, filter by method, date, receipt, student, matricule, class, or reference.
2. Open Receipt to verify the student, instalment, amount, method, and reference.
3. Use Export for external review or reconciliation.

### Review a student account

Route: `/finance/student-accounts`

1. Choose a class and click Show students; name or matricule search is optional.
2. Read the status label: Balance due shows the remaining amount; Paid in full shows the total paid.
3. Open the student to view Billed, Paid, Balance due, Credit, and every transaction.
4. Click Prepare consolidated receipt, then download or print the complete statement.

### Record a deposit or withdrawal

Route: `/finance/treasury`

1. Choose Deposit or Withdrawal, the date, and the affected money account.
2. Choose the appropriate accounting counter-account.
3. Enter amount, required reason, and deposit-slip/statement reference.
4. Click Record and post; the operation becomes immutable and the balance recalculates immediately.

> **Remember:** Do not delete an error: post a traceable reversing movement and retain both references.

### Transfer between two accounts

Route: `/finance/treasury`

1. Choose Internal transfer, then the source and destination accounts.
2. Enter amount, reason, and bank/cash reference.
3. Verify that total treasury is unchanged: only the distribution between accounts should move.

### Record an expense

Route: `/finance`

1. Open the Expenses tab, then New expense.
2. Choose date, category, label, amount, and the account that actually paid.
3. Save, then verify the journal entry and the decrease in the account balance.

### Configure fees

Route: `/finance/fee-types`

1. First define reusable fee types and their revisions.
2. Create a plan by session and scope; configure instalments and due dates.
3. Preview charges before generation and handle adjustments and overrides separately.
4. Verify balances in Student accounts and issued documents.

### Run payroll

Route: `/finance/payroll`

1. Create a payroll period linked to an open accounting period.
2. Configure payroll components and mappings; review must remain blocked when a mapping is missing.
3. Preview eligible employees, then create and calculate the run.
4. Use separate reviewers, approvers, and payers when segregation of duties is enabled.
5. After payment, verify and regenerate payslips when necessary.

### Reconcile and close

Route: `/finance/accounting`

1. Verify chart-of-accounts, mapping, and period readiness.
2. Review journals, trial balance, and general ledger.
3. Reconcile every account to its bank statement or cash count before close.
4. Use Finance reports with the correct session and class context.

## Boundaries

- No access to Access and responsibilities.
- Do not change school structure, students, or timetables without formal administrator delegation.
- Never delete a posted finance transaction; use void, refund, or reversal.
- One person must not calculate, review, and approve payroll when segregation of duties is required.

## Quick verification

- [ ] Classes from all six parcours appear in Student accounts.
- [ ] A payment immediately increases the credited account and produces a receipt naming the actual student.
- [ ] A balanced deposit and withdrawal restore the original treasury balance while leaving two immutable records.
- [ ] The consolidated receipt includes every transaction and the exact balance.
- [ ] Access and responsibilities is blocked.

## Confirmed gaps in the tested build

- Charges and Finance Documents currently open but their first API calls are denied for the local accountant profile; role actions need alignment before use.
- The payroll Staff shortcut redirects because HR_VIEW is missing; either grant staff read access or hide the link.
- The local profile also has high-risk student creation/import and setup/timetable rights. They are not part of the normal accountant mandate and should be reviewed under least privilege.

---

Verified against the local application on 28 August 2026 (build `1c89f5b`).
