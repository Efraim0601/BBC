-- Gate 11: expose a narrow, linked-child parent acknowledgement action.
-- The read endpoints use the existing child-scoped parent actions; this new
-- write action is intentionally separate so a read grant cannot mutate a
-- correspondence acknowledgement.
WITH actions(code, module, group_code, risk_level, scope_type, required_level,
             default_read_action, display_order) AS (VALUES
    ('PARENT_MESSAGES_ACK','parent','Parent','LOW','CHILD','write',false,608)
)
INSERT INTO permission_action
    (code,module,group_code,label_fr,label_en,description_fr,description_en,
     risk_level,scope_type,required_level,default_read_action,display_order)
SELECT code, module, group_code, 'Accuser une correspondance enfant',
       'Acknowledge child correspondence',
       'Accusé de réception limité à un enfant lié.',
       'Acknowledgement limited to a linked child.',
       risk_level,scope_type,required_level,default_read_action,display_order
  FROM actions
ON CONFLICT (code) DO UPDATE SET
    module=EXCLUDED.module, group_code=EXCLUDED.group_code,
    risk_level=EXCLUDED.risk_level, scope_type=EXCLUDED.scope_type,
    required_level=EXCLUDED.required_level, display_order=EXCLUDED.display_order,
    updated_at=now();

INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode, is_permanent, reason)
SELECT s.id, 'parent_portal', 'PARENT_MESSAGES_ACK', 'ALLOW', 'LINKED_CHILDREN', true,
       'Gate 11 linked-child correspondence acknowledgement'
  FROM school s
 WHERE EXISTS (SELECT 1 FROM role WHERE code='parent_portal')
ON CONFLICT DO NOTHING;

INSERT INTO permission_action_grant(school_id, role_code, action_code, allowed)
SELECT s.id, 'parent', 'PARENT_MESSAGES_ACK', true
  FROM school s
 WHERE EXISTS (SELECT 1 FROM role WHERE code='parent')
ON CONFLICT (school_id,role_code,action_code)
DO UPDATE SET allowed=EXCLUDED.allowed;
