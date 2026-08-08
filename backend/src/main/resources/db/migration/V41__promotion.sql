-- ============================================================================
--  V41 — Passage de classe (fin d'année)
--
--  Trois briques, correspondant aux trois questions du cycle de vie de l'élève :
--    1. la PROGRESSION  — vers quelle classe un élève monte (mapping configurable) ;
--    2. la RÈGLE        — le seuil de moyenne qui propose admis / redouble ;
--    3. le LOT          — la trace de ce qui a été appliqué, décision par décision,
--                         avec la proposition automatique ET l'arbitrage humain.
--
--  Le module `promotion` est distinct de `journey` : on peut confier l'exécution
--  du passage au censeur sans lui ouvrir la réécriture de l'historique.
-- ============================================================================

-- ---- 1. Progression : quelle classe vient après ---------------------------
-- `grade_order` classe les niveaux dans une section (SIL=1, CP=2, …) : il donne
-- l'ordre d'affichage et sert de base à la déduction automatique du mapping.
-- `terminal` marque une classe de sortie (Terminale, Upper Sixth) : y réussir
-- donne « diplômé », pas « admis ».
ALTER TABLE school_class ADD COLUMN IF NOT EXISTS grade_order   INT     NOT NULL DEFAULT 0;
ALTER TABLE school_class ADD COLUMN IF NOT EXISTS next_class_id UUID    REFERENCES school_class(id) ON DELETE SET NULL;
ALTER TABLE school_class ADD COLUMN IF NOT EXISTS terminal      BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS idx_school_class_order ON school_class (school_id, section_id, grade_order);

-- ---- 2. Règles de passage --------------------------------------------------
-- Une règle porte sur une classe précise, ou sur un couple (niveau, sous-système),
-- ou sur toute l'école (tout à NULL). La plus spécifique gagne.
--   pass_mark      : moyenne annuelle à atteindre pour être proposé admis.
--   council_margin : largeur de la zone grise juste sous le seuil — l'élève y est
--                    proposé « à examiner » plutôt que redoublant d'office.
--   max_repeats    : au-delà de N redoublements déjà enregistrés au parcours, on
--                    n'ose plus proposer un redoublement de plus : conseil de classe.
CREATE TABLE promotion_rule (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id      UUID NOT NULL REFERENCES school(id),
    level          VARCHAR(12) CHECK (level IS NULL OR level IN ('maternelle','primary','secondary')),
    subsystem      VARCHAR(2)  CHECK (subsystem IS NULL OR subsystem IN ('FR','EN')),
    class_id       UUID REFERENCES school_class(id) ON DELETE CASCADE,
    pass_mark      NUMERIC(4,2) NOT NULL DEFAULT 10.00
                   CHECK (pass_mark >= 0 AND pass_mark <= 20),
    council_margin NUMERIC(4,2) NOT NULL DEFAULT 0
                   CHECK (council_margin >= 0 AND council_margin <= 20),
    max_repeats    INT CHECK (max_repeats IS NULL OR max_repeats >= 0),
    updated_by     UUID REFERENCES app_user(id),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Une seule règle par périmètre (les NULL ne se dédupliquant pas tout seuls).
CREATE UNIQUE INDEX uq_promotion_rule_class ON promotion_rule (school_id, class_id)
    WHERE class_id IS NOT NULL;
CREATE UNIQUE INDEX uq_promotion_rule_scope
    ON promotion_rule (school_id, COALESCE(level, '*'), COALESCE(subsystem, '*'))
    WHERE class_id IS NULL;

-- Règle par défaut : 10/20, 1 point de zone conseil, 2 redoublements maximum.
INSERT INTO promotion_rule (school_id, pass_mark, council_margin, max_repeats)
SELECT s.id, 10.00, 1.00, 2 FROM school s
 WHERE NOT EXISTS (SELECT 1 FROM promotion_rule r
                    WHERE r.school_id = s.id
                      AND r.class_id IS NULL AND r.level IS NULL AND r.subsystem IS NULL);

-- ---- 3. Lots appliqués et décisions ----------------------------------------
CREATE TABLE promotion_batch (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id          UUID NOT NULL REFERENCES school(id),
    academic_year      VARCHAR(16) NOT NULL,   -- année qui se termine
    next_academic_year VARCHAR(16) NOT NULL,   -- année d'accueil
    class_id           UUID REFERENCES school_class(id) ON DELETE SET NULL,
    class_name         VARCHAR(80) NOT NULL,
    students_total     INT NOT NULL DEFAULT 0,
    promoted_count     INT NOT NULL DEFAULT 0,
    repeated_count     INT NOT NULL DEFAULT 0,
    graduated_count    INT NOT NULL DEFAULT 0,
    other_count        INT NOT NULL DEFAULT 0,
    overridden_count   INT NOT NULL DEFAULT 0,
    applied_by         UUID REFERENCES app_user(id),
    applied_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_promotion_batch_year ON promotion_batch (school_id, academic_year, applied_at DESC);

-- Une décision par élève et par année : ce que la règle proposait, ce que
-- l'administration a retenu, et pourquoi quand les deux diffèrent.
CREATE TABLE promotion_decision (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id         UUID NOT NULL REFERENCES school(id),
    batch_id          UUID NOT NULL REFERENCES promotion_batch(id) ON DELETE CASCADE,
    student_id        UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    academic_year     VARCHAR(16) NOT NULL,
    from_class_id     UUID,
    from_class_name   VARCHAR(80) NOT NULL,
    to_class_id       UUID,
    to_class_name     VARCHAR(80),
    annual_average    NUMERIC(4,2),
    rank              INT,
    class_size        INT,
    sequences_counted INT NOT NULL DEFAULT 0,
    prior_repeats     INT NOT NULL DEFAULT 0,
    proposed_result   VARCHAR(16) NOT NULL,
    final_result      VARCHAR(16) NOT NULL
                      CHECK (final_result IN ('promoted','repeated','graduated',
                                              'transferred_out','excluded')),
    overridden        BOOLEAN NOT NULL DEFAULT false,
    override_reason   TEXT,
    decided_by        UUID REFERENCES app_user(id),
    decided_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, student_id, academic_year)
);
CREATE INDEX idx_promotion_decision_batch ON promotion_decision (batch_id);
CREATE INDEX idx_promotion_decision_student ON promotion_decision (school_id, student_id);

-- ---- 4. Droits -------------------------------------------------------------
-- Direction et censeur exécutent le passage ; le professeur principal le consulte.
-- La configuration (mapping + règles) reste derrière « Paramètres : Complet ».
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT id, 'principal', 'promotion', 'write' FROM school
ON CONFLICT (school_id, role_code, module) DO NOTHING;
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT id, 'prefect', 'promotion', 'write' FROM school
ON CONFLICT (school_id, role_code, module) DO NOTHING;
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT id, 'form_teacher', 'promotion', 'read' FROM school
ON CONFLICT (school_id, role_code, module) DO NOTHING;
