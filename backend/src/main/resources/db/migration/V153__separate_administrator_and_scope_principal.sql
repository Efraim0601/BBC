-- Separate the technical school administrator from the operational principal.
-- Administrators remain school-wide and own Access Control. Principals are
-- always restricted to explicitly assigned Nursery/Primary/Secondary levels.

INSERT INTO role(code, label_fr, label_en, builtin)
VALUES ('administrator', 'Administrateur', 'Administrator', true)
ON CONFLICT (code) DO UPDATE SET builtin=true;

CREATE TABLE IF NOT EXISTS employee_management_level (
    employee_id UUID NOT NULL REFERENCES employee(id) ON DELETE CASCADE,
    level       VARCHAR(12) NOT NULL
        CHECK (level IN ('maternelle', 'primary', 'secondary')),
    PRIMARY KEY (employee_id, level)
);

-- Give the administrator the complete legacy module envelope for every school.
INSERT INTO permission_grant(school_id, role_code, module, level)
SELECT s.id, 'administrator', m.module, 'write'
  FROM school s
 CROSS JOIN (VALUES
    ('dashboard'), ('presence'), ('students'), ('hr'), ('academic'),
    ('finance'), ('timetable'), ('events'), ('discipline'), ('reports'),
    ('settings'), ('journey'), ('alerts'), ('messages'), ('coursebook'),
    ('health'), ('documents'), ('classkit')
 ) AS m(module)
ON CONFLICT (school_id, role_code, module)
DO UPDATE SET level=EXCLUDED.level;

-- The administrator role owns every active V2 action. NONE actions retain the
-- real NONE scope; all resource-aware actions receive school-wide authority.
INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode,
     is_permanent, reason)
SELECT s.id, 'administrator', a.code, 'ALLOW',
       CASE WHEN upper(a.scope_type)='NONE' THEN 'NONE' ELSE 'SCHOOL_ALL' END,
       true, 'Built-in administrator authority'
  FROM school s
 CROSS JOIN permission_action a
 WHERE a.active=true
   AND NOT EXISTS (
       SELECT 1
         FROM permission_role_action existing
        WHERE existing.school_id=s.id
          AND existing.role_code='administrator'
          AND existing.action_code=a.code
   );

-- ProductionBootstrap historically created the one technical administrator
-- with the principal role. Identify that explicit bootstrap exception without
-- promoting ordinary principals.
CREATE TEMP TABLE v153_administrator_candidate ON COMMIT DROP AS
SELECT DISTINCT u.id, u.school_id
  FROM app_user u
 WHERE lower(u.role_code) IN ('principal', 'administrator', 'admin', 'school_admin')
   AND (
       lower(u.username) IN ('admin', 'administrator', 'administrateur')
       OR EXISTS (
           SELECT 1
             FROM permission_user_action ua
            WHERE ua.school_id=u.school_id
              AND ua.user_id=u.id
              AND ua.action_code='PERMISSION_MANAGE'
              AND ua.effect='ALLOW'
              AND lower(ua.reason) LIKE '%administrator%'
       )
   );

UPDATE app_user u
   SET role_code='administrator', parcours_scope_mode='GLOBAL'
  FROM v153_administrator_candidate candidate
 WHERE u.id=candidate.id;

DELETE FROM app_user_role ur
 USING v153_administrator_candidate candidate
 WHERE ur.user_id=candidate.id
   AND (ur.is_primary OR ur.role_code='principal_legacy_compat');

INSERT INTO app_user_role
    (school_id, user_id, role_code, is_primary, reason)
SELECT school_id, id, 'administrator', true,
       'Promoted from the historical bootstrap principal account'
  FROM v153_administrator_candidate
ON CONFLICT DO NOTHING;

-- Existing ordinary principals must not become locked out during migration.
-- Preserve an existing explicit assignment; otherwise begin with all three
-- levels and let the administrator narrow it from Staff.
INSERT INTO app_user_parcours(user_id, level, subsystem)
SELECT u.id, level.code, subsystem.code
  FROM app_user u
 CROSS JOIN (VALUES ('maternelle'), ('primary'), ('secondary')) AS level(code)
 CROSS JOIN (VALUES ('FR'), ('EN')) AS subsystem(code)
 WHERE lower(u.role_code)='principal'
   AND NOT EXISTS (SELECT 1 FROM app_user_parcours current_scope WHERE current_scope.user_id=u.id)
ON CONFLICT DO NOTHING;

UPDATE app_user
   SET parcours_scope_mode='EXPLICIT'
 WHERE lower(role_code)='principal';

INSERT INTO employee_management_level(employee_id, level)
SELECT DISTINCT u.employee_id, p.level
  FROM app_user u
  JOIN app_user_parcours p ON p.user_id=u.id
 WHERE lower(u.role_code)='principal'
   AND u.employee_id IS NOT NULL
ON CONFLICT DO NOTHING;

-- Access Control is an administrator-only boundary. Keep explicit DENY rows
-- for auditable policy output even though the Java policy invariant also
-- enforces this rule against user overrides and future matrix changes.
DELETE FROM permission_role_action
 WHERE role_code='principal'
   AND action_code IN ('PERMISSION_VIEW', 'PERMISSION_MANAGE', 'ROLE_MANAGE');

INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode,
     is_permanent, reason)
SELECT s.id, 'principal', a.code, 'DENY', 'SCHOOL_ALL', true,
       'Access Control is reserved for administrators'
  FROM school s
  JOIN permission_action a
    ON a.code IN ('PERMISSION_VIEW', 'PERMISSION_MANAGE', 'ROLE_MANAGE')
ON CONFLICT DO NOTHING;
