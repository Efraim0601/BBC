-- Gate 10: the finance document register is a school-wide workspace action.
-- Individual invoice/receipt details still resolve and validate their
-- server-side student/payment context in FinancePolicyService.  The catalog
-- scope must match the reviewed V137 SCHOOL_ALL role rules so the register,
-- batch status and exports do not fail before a resource can be selected.
UPDATE permission_action
   SET scope_type = 'SCHOOL',
       updated_at = now()
 WHERE code = 'FINANCE_DOCUMENT_VIEW'
   AND scope_type = 'STUDENT';
