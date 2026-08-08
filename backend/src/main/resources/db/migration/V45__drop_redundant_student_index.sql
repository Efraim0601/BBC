-- ============================================================================
--  V45 — Retrait des deux index ajoutés à tort par V44
--
--  V44 créait `idx_student_class_roster` et `idx_student_school_niu` en partant
--  d'un constat faux : la table paraissait dépourvue d'index sur `niu` et sur
--  `class_id`. Elle en avait déjà deux, `ix_student_niu (school_id, niu)` et
--  `idx_student_class (school_id, class_id)`, qui couvrent exactement les mêmes
--  recherches — ils avaient simplement échappé à un « \di student* », lequel ne
--  liste que les index dont le NOM commence par « student ».
--
--  Les variantes partielles n'apportent rien de mesurable sur cette volumétrie
--  et se paient à chaque écriture, donc à chaque ligne importée. On les retire.
--  V44 n'est pas modifiée : sa somme de contrôle est déjà enregistrée en base.
-- ============================================================================

DROP INDEX IF EXISTS idx_student_class_roster;
DROP INDEX IF EXISTS idx_student_school_niu;
