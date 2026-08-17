-- Keep chart-of-accounts administration separate from the expense ledger.
-- Existing finance grants are translated additively so the compatibility
-- rollout preserves authority until a school reviews the new profile.
INSERT INTO permission_action
    (code,module,group_code,label_fr,label_en,description_fr,description_en,
     risk_level,scope_type,required_level,default_read_action,display_order)
VALUES
    ('FINANCE_EXPENSE_VIEW','finance','Finance',
     'Dépenses — consultation','Expenses — view',
     'Consulter les dépenses de l’établissement et leurs écritures minimales.',
     'View school expenses and their minimal ledger details.',
     'MEDIUM','SCHOOL','read',false,526),
    ('FINANCE_EXPENSE_CREATE','finance','Finance',
     'Dépense — enregistrer','Expense — create',
     'Enregistrer une dépense validée dans le périmètre financier de l’établissement.',
     'Record an expense within the school finance scope.',
     'HIGH','SCHOOL','write',false,527),
    ('FINANCE_EXPENSE_DELETE','finance','Finance',
     'Dépense — supprimer','Expense — delete',
     'Supprimer une dépense selon la procédure financière et la séparation des tâches.',
     'Delete an expense under the finance procedure and separation of duties.',
     'CRITICAL','SCHOOL','write',false,528)
ON CONFLICT (code) DO UPDATE SET
    module=EXCLUDED.module, group_code=EXCLUDED.group_code,
    label_fr=EXCLUDED.label_fr, label_en=EXCLUDED.label_en,
    description_fr=EXCLUDED.description_fr, description_en=EXCLUDED.description_en,
    risk_level=EXCLUDED.risk_level, scope_type=EXCLUDED.scope_type,
    required_level=EXCLUDED.required_level,
    default_read_action=EXCLUDED.default_read_action,
    display_order=EXCLUDED.display_order, active=true, updated_at=now();

-- Deterministic least-privilege templates for new schools.  The compatibility
-- inserts below are intentionally broader only where the old finance module
-- grant was already broad; adoption can narrow them through Access Control.
INSERT INTO permission_role_template_rule
    (template_code,action_code,effect,scope_mode,is_permanent,reason,display_order)
VALUES
    ('accountant','FINANCE_EXPENSE_VIEW','ALLOW','SCHOOL_ALL',true,
     'Comptabilité — consultation des dépenses',20),
    ('accountant','FINANCE_EXPENSE_CREATE','ALLOW','SCHOOL_ALL',true,
     'Comptabilité — saisie des dépenses',21),
    ('bursar','FINANCE_EXPENSE_VIEW','ALLOW','SCHOOL_ALL',true,
     'Économat — consultation des dépenses',20),
    ('principal_oversight','FINANCE_EXPENSE_VIEW','ALLOW','SCHOOL_ALL',true,
     'Direction — visibilité financière',20)
ON CONFLICT DO NOTHING;

INSERT INTO permission_role_action
    (school_id,role_code,action_code,effect,scope_mode,is_permanent,reason)
SELECT s.id,r.code,'FINANCE_EXPENSE_VIEW','ALLOW','SCHOOL_ALL',true,
       'Permission Policy V2 expense-view compatibility backfill'
  FROM school s JOIN role r ON r.code IN ('principal','administrator','admin','school_admin',
                                          'accountant','bursar','econome','finance_officer')
  LEFT JOIN permission_grant pg
    ON pg.school_id=s.id AND pg.role_code=r.code AND pg.module='finance'
 WHERE lower(coalesce(pg.level,'none')) IN ('read','write')
    OR r.code IN ('principal','administrator','admin','school_admin','accountant','bursar','econome')
ON CONFLICT DO NOTHING;

INSERT INTO permission_role_action
    (school_id,role_code,action_code,effect,scope_mode,is_permanent,reason)
SELECT s.id,r.code,'FINANCE_EXPENSE_CREATE','ALLOW','SCHOOL_ALL',true,
       'Permission Policy V2 expense-create compatibility backfill'
  FROM school s JOIN role r ON r.code IN ('principal','administrator','admin','school_admin',
                                          'accountant','bursar','econome','finance_officer')
  JOIN permission_grant pg
    ON pg.school_id=s.id AND pg.role_code=r.code AND pg.module='finance'
 WHERE lower(pg.level)='write'
ON CONFLICT DO NOTHING;

INSERT INTO permission_role_action
    (school_id,role_code,action_code,effect,scope_mode,is_permanent,reason)
SELECT s.id,r.code,'FINANCE_EXPENSE_DELETE','ALLOW','SCHOOL_ALL',true,
       'Permission Policy V2 expense-delete compatibility backfill'
  FROM school s JOIN role r ON r.code IN ('principal','administrator','admin','school_admin',
                                          'accountant','bursar','econome','finance_officer')
  JOIN permission_grant pg
    ON pg.school_id=s.id AND pg.role_code=r.code AND pg.module='finance'
 WHERE lower(pg.level)='write'
ON CONFLICT DO NOTHING;
