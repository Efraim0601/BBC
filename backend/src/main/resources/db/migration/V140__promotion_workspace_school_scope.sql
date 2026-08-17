-- The promotion workspace operates on an explicitly submitted, tenant-scoped
-- roster and resolves each enrollment server-side.  Its controller envelope
-- therefore has no single student/class resource to attach to.  Keep the
-- action permissions narrow, but describe the workspace actions as school
-- scoped so V2 can authorize the envelope without a context-free legacy gate.
-- Ordinary role/user grants are not changed here.
UPDATE permission_action
   SET scope_type='SCHOOL', updated_at=now()
 WHERE code IN ('PROGRESSION_VIEW', 'PROMOTION_RECOMMEND',
                'PROMOTION_REVIEW', 'PROMOTION_OVERRIDE');
