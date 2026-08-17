-- Gate 11: bind the linked-child acknowledgement action to the live parent
-- principal role used by authenticated parent users in this deployment.
INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode, is_permanent, reason)
SELECT s.id, 'parent', 'PARENT_MESSAGES_ACK', 'ALLOW', 'LINKED_CHILDREN', true,
       'Gate 11 linked-child correspondence acknowledgement for parent role'
  FROM school s
 WHERE EXISTS (SELECT 1 FROM role WHERE code='parent')
   AND EXISTS (SELECT 1 FROM permission_action WHERE code='PARENT_MESSAGES_ACK')
ON CONFLICT DO NOTHING;
