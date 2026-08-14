-- Employee self-service must be an explicit, self-scoped capability.  The
-- service still resolves the authenticated employee link and checks ownership
-- of every payslip; this action is the policy boundary for the endpoint.
INSERT INTO permission_action
    (code,module,group_code,label_fr,label_en,description_fr,description_en,
     risk_level,scope_type,required_level,default_read_action,display_order)
VALUES
    ('PAYSLIP_VIEW_SELF','hr','Finance',
     'Mes bulletins de paie','My payslips',
     'Consulter uniquement les bulletins de paie de son propre compte salarié.',
     'View payslips belonging only to the authenticated employee account.',
     'LOW','SELF','read',true,543)
ON CONFLICT (code) DO UPDATE SET
    module=EXCLUDED.module, group_code=EXCLUDED.group_code,
    label_fr=EXCLUDED.label_fr, label_en=EXCLUDED.label_en,
    description_fr=EXCLUDED.description_fr, description_en=EXCLUDED.description_en,
    risk_level=EXCLUDED.risk_level, scope_type=EXCLUDED.scope_type,
    required_level=EXCLUDED.required_level,
    default_read_action=EXCLUDED.default_read_action,
    display_order=EXCLUDED.display_order, active=true, updated_at=now();

INSERT INTO permission_role_template_rule
    (template_code,action_code,effect,scope_mode,is_permanent,reason,display_order)
VALUES
    ('primary_teacher','PAYSLIP_VIEW_SELF','ALLOW','SELF',true,
     'Bulletins de paie du salarié connecté',50),
    ('secondary_teacher','PAYSLIP_VIEW_SELF','ALLOW','SELF',true,
     'Bulletins de paie du salarié connecté',50),
    ('form_teacher','PAYSLIP_VIEW_SELF','ALLOW','SELF',true,
     'Bulletins de paie du salarié connecté',50),
    ('finance_collector','PAYSLIP_VIEW_SELF','ALLOW','SELF',true,
     'Bulletins de paie du salarié connecté',50),
    ('accountant','PAYSLIP_VIEW_SELF','ALLOW','SELF',true,
     'Bulletins de paie du salarié connecté',50),
    ('principal_oversight','PAYSLIP_VIEW_SELF','ALLOW','SELF',true,
     'Bulletins de paie du salarié connecté',50)
ON CONFLICT DO NOTHING;

-- Upgrade existing schools and keep the legacy action facade in sync.  The
-- self scope remains enforced by AuthorizationPolicyService, so this does
-- not expose another employee's payslip.
INSERT INTO permission_role_action
    (school_id,role_code,action_code,effect,scope_mode,is_permanent,reason)
SELECT s.id,r.code,'PAYSLIP_VIEW_SELF','ALLOW','SELF',true,
       'Permission Policy V2 employee self-service payslip'
  FROM school s
  JOIN role r ON r.code IN ('teacher','form_teacher','principal','accountant','econome','hr','payroll')
ON CONFLICT DO NOTHING;

INSERT INTO permission_action_grant(school_id,role_code,action_code,allowed)
SELECT s.id,r.code,'PAYSLIP_VIEW_SELF',true
  FROM school s
  JOIN role r ON r.code IN ('teacher','form_teacher','principal','accountant','econome','hr','payroll')
ON CONFLICT (school_id,role_code,action_code)
DO UPDATE SET allowed=EXCLUDED.allowed;
