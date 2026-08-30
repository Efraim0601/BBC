-- The atomic registration service now authorizes its initial guardian link
-- with STUDENT_PROFILE_CREATE.  Remove the broader family-management role
-- action introduced while wiring the first accountant registration flow.
-- Accountants retain student creation but cannot alter or terminate family
-- links on existing student profiles.

UPDATE permission_role_action
   SET effect = 'INHERIT',
       scope_mode = 'NONE',
       scope_payload = NULL,
       is_permanent = true,
       reason = 'Accountant registration does not grant ongoing family management',
       version = version + 1,
       updated_at = now()
 WHERE role_code = 'accountant'
   AND action_code = 'GUARDIAN_LINK_MANAGE'
   AND effective_from IS NULL
   AND effective_to IS NULL;

UPDATE permission_action_grant
   SET allowed = false
 WHERE role_code = 'accountant'
   AND action_code = 'GUARDIAN_LINK_MANAGE';
