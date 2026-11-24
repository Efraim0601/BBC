-- Grade sheets no longer wait for an approval queue. A teacher save is the
-- authoritative, report-card-ready status; management may still reopen one
-- class/subject packet with a reason when a correction is necessary.

-- Entries which were already waiting in the previous approval queue become
-- report-card ready on deployment. Preserve an explicit migration audit row
-- instead of pretending that an administrator approved them individually.
INSERT INTO academic_grade_packet_transition
    (school_id, packet_id, from_status, to_status, reason, actor_user_id)
SELECT school_id, id, 'SUBMITTED', 'ACCEPTED',
       'Automatic transition: direct-save grade workflow enabled', NULL
  FROM academic_grade_packet
 WHERE status = 'SUBMITTED';

UPDATE academic_grade_packet
   SET status = 'ACCEPTED',
       version = version + 1,
       updated_at = now()
 WHERE status = 'SUBMITTED';

-- Keep line-level provenance aligned with the packet status so trace/audit
-- data and legacy readers agree with the report-card readiness decision.
UPDATE academic_grade
   SET workflow_status = 'ACCEPTED',
       version = version + 1,
       updated_at = now()
 WHERE workflow_status = 'SUBMITTED';

UPDATE subject_result_comment
   SET workflow_status = 'ACCEPTED',
       version = version + 1,
       updated_at = now()
 WHERE workflow_status = 'SUBMITTED';

-- Accountants already have school-wide report-card read access. They now also
-- receive the narrowly scoped packet-review action used to reopen a sheet for
-- correction; it does not grant grade entry, validation, publishing, or setup.
DELETE FROM permission_role_action
 WHERE role_code = 'accountant'
   AND action_code = 'ACADEMIC_GRADE_PACKET_REVIEW'
   AND effective_from IS NULL
   AND effective_to IS NULL;

INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode, is_permanent, reason)
SELECT s.id, 'accountant', 'ACADEMIC_GRADE_PACKET_REVIEW',
       'ALLOW', 'SCHOOL_ALL', true,
       'Accountant reopens saved grade sheets for correction'
  FROM school s;

INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT s.id, 'accountant', 'ACADEMIC_GRADE_PACKET_REVIEW', true
  FROM school s
ON CONFLICT (school_id, role_code, action_code)
DO UPDATE SET allowed = EXCLUDED.allowed;

DELETE FROM permission_role_template_rule
 WHERE template_code = 'accountant'
   AND action_code = 'ACADEMIC_GRADE_PACKET_REVIEW';

INSERT INTO permission_role_template_rule
    (template_code, action_code, effect, scope_mode, is_permanent, reason, display_order)
VALUES
    ('accountant', 'ACADEMIC_GRADE_PACKET_REVIEW', 'ALLOW', 'SCHOOL_ALL', true,
     'Accountant reopens saved grade sheets for correction', 89);
