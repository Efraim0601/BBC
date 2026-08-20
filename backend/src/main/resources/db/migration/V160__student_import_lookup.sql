-- ============================================================================
--  V160 — Index de rapprochement pour l'import de registre
--
--  L'import d'une classe rapproche chaque ligne des élèves déjà inscrits, pour
--  compléter leur fiche au lieu de créer un doublon. Il lit donc, une fois par
--  lot, l'effectif de la classe cible puis les NIU déjà attribués dans l'école.
--  Sans index, ces deux lectures balaient toute la table `student` ; l'ancien
--  code faisait bien pire (un balayage par ligne importée).
--
--  Les deux index sont partiels : seuls les élèves actifs sont rapprochés, et
--  la grande majorité des fiches n'ont pas de NIU (44 sur 641 en production).
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_student_class_roster
    ON student (school_id, class_id)
    WHERE active;

CREATE INDEX IF NOT EXISTS idx_student_school_niu
    ON student (school_id, niu)
    WHERE active AND niu IS NOT NULL;
