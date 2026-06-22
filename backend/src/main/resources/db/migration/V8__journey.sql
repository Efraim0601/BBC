-- ============================================================================
--  V8 — Parcours longitudinal (student journey)
--       One row per student per academic year: the cumulative school history
--       (class, level, end-of-year average/rank, conseil de classe decision).
--       This is what gives administration & parents a multi-year visibility.
-- ============================================================================

CREATE TABLE journey_entry (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES school(id),
    student_id      UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    academic_year   VARCHAR(16) NOT NULL,        -- e.g. 2024-2025
    class_name      VARCHAR(32) NOT NULL,        -- class attended that year
    level           VARCHAR(12),                 -- primary | secondary
    subsystem       VARCHAR(2),                  -- FR | EN (varchar to match the JPA String mapping)
    result          VARCHAR(16) NOT NULL DEFAULT 'in_progress'
                    CHECK (result IN ('in_progress','promoted','repeated',
                                      'transferred_in','transferred_out','graduated','excluded')),
    general_average NUMERIC(4,2) CHECK (general_average IS NULL
                                        OR (general_average >= 0 AND general_average <= 20)),
    rank            INT,                          -- class rank that year
    class_size      INT,                          -- headcount for context
    decision        TEXT,                          -- conseil de classe decision / mention
    note            TEXT,
    recorded_by     UUID REFERENCES app_user(id),
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, student_id, academic_year)
);
CREATE INDEX idx_journey_student ON journey_entry (school_id, student_id, academic_year);

-- Make the new "journey" module visible to existing tenants. Principal gets
-- write; the dean & form teacher get read. (Fresh prod installs are seeded by
-- ProductionBootstrap; the demo dataset refines this in db/seed.)
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT id, 'principal', 'journey', 'write' FROM school
ON CONFLICT (school_id, role_code, module) DO NOTHING;
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT id, 'prefect', 'journey', 'write' FROM school
ON CONFLICT (school_id, role_code, module) DO NOTHING;
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT id, 'form_teacher', 'journey', 'read' FROM school
ON CONFLICT (school_id, role_code, module) DO NOTHING;
