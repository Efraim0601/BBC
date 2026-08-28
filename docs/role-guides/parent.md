# Parent / guardian guide

*Follow only linked children and the sections enabled by the school.*

**Scope:** Child or children explicitly linked to the account with active portal access.

The parent portal brings together academic journey, school life, fees, grades, supplies, documents, and messages. Every section depends on the guardian relationship: a parent never sees another student.

## What this role can do

- **Overview:** View child, class, fee status, attendance, and visible assessment count.
- **Official journey:** View published results and family-visible decisions.
- **School life:** View finalized attendance, parent-visible discipline, non-confidential health visits, events, and correspondence.
- **Fees and payments:** View billed amount, paid amount, balance, schedule, payment methods, and receipts.
- **Grades and documents:** View published grades, released report cards/documents, supplies, and textbooks.
- **Suggestion box:** Send a suggestion, question, complaint, or thanks about the selected child.

## Daily procedures

### Activate portal access

Route: `/login`

1. The school adds the guardian’s email from the student record and enables Portal access.
2. The guardian accepts the invitation or receives credentials according to the selected mode.
3. Sign in with the provided email/username and personal password.
4. When a guardian has no email, the contact can exist without portal access; access can be enabled later.

### Switch child

Route: `/parent`

1. Under My children, click the child to review.
2. Verify the displayed name, matricule, and class before reading a section.
3. Every following section updates for the selected child.

### Read Overview and Official journey

Route: `/parent`

1. Overview summarizes visible attendance, fees, and assessments.
2. Official journey shows only published results and decisions.
3. An empty section means no official data has been published yet.

### Review school life

Route: `/parent`

1. Open School life for finalized presence, absence, lateness, and excused counts.
2. Review discipline, parent-safe health entries, events, and correspondence.
3. Confidential medical records are never exposed in the portal.

### Review fees and pay

Route: `/parent`

1. Open Fees & payments to view class fees, paid amount, outstanding balance, and next instalment.
2. Follow the accepted-method instructions and always keep the transaction reference.
3. Give the reference to the bursary; the payment appears after it is recorded and posted.
4. Then verify the receipt row and updated balance.

### View grades, supplies, and documents

Route: `/parent`

1. Grades shows only assessments released by the school.
2. Supplies & textbooks shows lists published for the class.
3. School documents contains files explicitly shared with families.

### Send a message

Route: `/parent`

1. Open Suggestion box and choose Suggestion, Question, Complaint, or Thanks.
2. Write a sufficiently specific message, then click Send message.
3. Track it under My messages.

## Boundaries

- The account accesses only linked children and guardian-enabled options.
- No access to Staff, Students, Academic, internal Finance, Settings, or Permissions.
- Unpublished or confidential data is not visible.
- Data corrections must be requested from the school; a parent cannot edit the student record.

## Quick verification

- [ ] The child count matches active guardian relationships.
- [ ] Fees & payments shows the same balance as the bursary.
- [ ] A URL to a staff module redirects to a data-free home page.
- [ ] The suggestion box remains tied to the selected child.

## Confirmed gaps in the tested build

- The header currently says All parcours for a parent; it should say My children.
- The Apps link opens an empty page for a parent; it should return directly to the parent space.

---

Verified against the local application on 28 August 2026 (build `1c89f5b`).
