-- V152: make the accountant payer-lookup grant explicit and non-expiring.
-- V151 added this authority for fresh databases.  Existing databases may
-- already contain an equivalent admin-panel grant, so normalize it here.

UPDATE permission_role_action
   SET effect = 'ALLOW',
       scope_mode = 'SCHOOL_ALL',
       scope_payload = NULL,
       is_permanent = true,
       reason = 'Accountant payment workflow requires minimal payer lookup',
       updated_at = now()
 WHERE role_code = 'accountant'
   AND action_code = 'STUDENT_DIRECTORY_VIEW'
   AND effective_from IS NULL
   AND effective_to IS NULL;
