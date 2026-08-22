-- ============================================================================
--  V168 — Ouvrir « promotion » et « library » au rôle administrator
--
--  V157 et V162 ont introduit deux modules et distribué leurs droits au
--  principal, à ses relais de cycle (admin_maternelle / admin_primary /
--  admin_secondary), au censeur et aux enseignants — mais pas au rôle
--  `administrator`, qui détient pourtant `write` sur les dix-huit autres
--  modules, y compris `journey`, l'autre dispositif de passage de classe.
--
--  L'omission rendait les deux fonctionnalités inaccessibles depuis le compte
--  d'administration : la matrice des rôles pilote aussi bien l'API que
--  l'affichage du menu. On rétablit donc la cohérence.
-- ============================================================================

INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT s.id, 'administrator', m.module, 'write'
  FROM school s
 CROSS JOIN (VALUES ('promotion'), ('library')) AS m(module)
ON CONFLICT (school_id, role_code, module) DO NOTHING;
