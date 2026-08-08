-- ============================================================================
--  V43 — Administrateurs de section
--
--  L'admin principal (« principal ») pilote l'établissement entier. Il lui faut
--  des relais : un administrateur par section, qui administre son cycle comme
--  l'admin principal administre l'école, mais SANS jamais déborder.
--
--  Trois rôles, un par section, plutôt qu'un rôle unique doublé d'une colonne
--  `app_user.section` : le code du rôle voyage déjà dans le JWT, si bien que la
--  section se déduit à chaque requête sans une seule lecture en base
--  (cf. platform.security.SectionRoles).
--
--  Ce que la matrice NE dit PAS, et que le code seul peut dire :
--    · le cloisonnement au cycle — la matrice ignore la notion de section ;
--    · « Paramètres : Complet » sans les réglages école-entière (matrice des
--      rôles, profil, SMTP, calendrier, catalogues, clôture d'année), réservés
--      à l'admin principal par @perm.schoolWide().
--  L'octroi ci-dessous est donc généreux : c'est le verrou de section, et non
--  la matrice, qui borne ces comptes.
-- ============================================================================

-- ---- 1. Les trois rôles ----------------------------------------------------
INSERT INTO role (code, label_fr, label_en, builtin) VALUES
    ('admin_maternelle', 'Admin Maternelle', 'Nursery Admin',   true),
    ('admin_primary',    'Admin Primaire',   'Primary Admin',   true),
    ('admin_secondary',  'Admin Secondaire', 'Secondary Admin', true)
ON CONFLICT (code) DO NOTHING;

-- ---- 2. Droits par défaut --------------------------------------------------
-- Écriture sur tout ce qui vit dans une section : élèves, pédagogie, vie
-- scolaire, finances et pilotage de son cycle. La seule exception est le
-- portail parent, qui n'appartient qu'aux familles.
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT s.id, r.code, m.module, 'write'
  FROM school s
 CROSS JOIN (VALUES ('admin_maternelle'), ('admin_primary'), ('admin_secondary')) AS r(code)
 CROSS JOIN (VALUES
        ('dashboard'), ('presence'), ('students'), ('hr'), ('academic'),
        ('finance'), ('timetable'), ('events'), ('discipline'), ('reports'),
        ('settings'), ('journey'), ('alerts'), ('messages'), ('coursebook'),
        ('health'), ('documents'), ('classkit'), ('promotion')
      ) AS m(module)
ON CONFLICT (school_id, role_code, module) DO NOTHING;
