-- V169 — bring databases that already passed V157 up to the renamed tables.
--
-- V157 was renamed from promotion_* to year_promotion_* after some databases
-- had already recorded the migration. Those databases have the journey
-- promotion_* tables but not the annual year_promotion_* tables used by the
-- promotion module entities. Keep this migration idempotent so it is safe on
-- both database shapes.

CREATE TABLE IF NOT EXISTS year_promotion_rule (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id      UUID NOT NULL REFERENCES school(id),
    level          VARCHAR(12) CHECK (level IS NULL OR level IN ('maternelle','primary','secondary')),
    subsystem      VARCHAR(2) CHECK (subsystem IS NULL OR subsystem IN ('FR','EN')),
    class_id       UUID REFERENCES school_class(id) ON DELETE CASCADE,
    pass_mark      NUMERIC(4,2) NOT NULL DEFAULT 10.00
                   CHECK (pass_mark >= 0 AND pass_mark <= 20),
    council_margin NUMERIC(4,2) NOT NULL DEFAULT 0
                   CHECK (council_margin >= 0 AND council_margin <= 20),
    max_repeats    INT CHECK (max_repeats IS NULL OR max_repeats >= 0),
    updated_by     UUID REFERENCES app_user(id),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_year_promotion_rule_class
    ON year_promotion_rule (school_id, class_id)
    WHERE class_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_year_promotion_rule_scope
    ON year_promotion_rule (school_id, COALESCE(level, '*'), COALESCE(subsystem, '*'))
    WHERE class_id IS NULL;

INSERT INTO year_promotion_rule (school_id, pass_mark, council_margin, max_repeats)
SELECT s.id, 10.00, 1.00, 2
  FROM school s
 WHERE NOT EXISTS (
       SELECT 1
         FROM year_promotion_rule r
        WHERE r.school_id = s.id
          AND r.class_id IS NULL
          AND r.level IS NULL
          AND r.subsystem IS NULL
   );

CREATE TABLE IF NOT EXISTS year_promotion_batch (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id          UUID NOT NULL REFERENCES school(id),
    academic_year      VARCHAR(16) NOT NULL,
    next_academic_year VARCHAR(16) NOT NULL,
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

CREATE INDEX IF NOT EXISTS idx_year_promotion_batch
    ON year_promotion_batch (school_id, academic_year, applied_at DESC);

CREATE TABLE IF NOT EXISTS year_promotion_decision (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id         UUID NOT NULL REFERENCES school(id),
    batch_id          UUID NOT NULL REFERENCES year_promotion_batch(id) ON DELETE CASCADE,
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

CREATE INDEX IF NOT EXISTS idx_year_promotion_decision_batch
    ON year_promotion_decision (batch_id);
CREATE INDEX IF NOT EXISTS idx_year_promotion_decision_student
    ON year_promotion_decision (school_id, student_id);
