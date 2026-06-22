-- ============================================================================
--  V13 — Cahier de textes & devoirs (coursebook)
--        One row per class / subject / day: what was covered in class plus the
--        homework assigned and its due date. This is the daily class log that
--        teachers fill in and that parents & administration can consult.
-- ============================================================================

CREATE TABLE coursebook_entry (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id     UUID NOT NULL REFERENCES school(id),
    class_name    VARCHAR(32) NOT NULL,        -- class the lesson was given to
    subject_code  VARCHAR(8)  NOT NULL,        -- MATH | FR | EN | HG | SVT | PC | EPS | INFO
    entry_date    DATE NOT NULL,               -- day the lesson took place
    content       TEXT NOT NULL,               -- what was covered in class
    homework      TEXT,                        -- assignment set, if any
    due_date      DATE,                        -- when the homework is due
    created_by    UUID REFERENCES app_user(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_coursebook_class ON coursebook_entry (school_id, class_name, entry_date DESC);

-- Make the new "coursebook" module visible to existing tenants. Principal,
-- form teacher and teacher get write; the prefect gets read. (Fresh prod
-- installs are seeded by ProductionBootstrap; the demo dataset refines this.)
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT id, 'principal', 'coursebook', 'write' FROM school
ON CONFLICT (school_id, role_code, module) DO NOTHING;
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT id, 'prefect', 'coursebook', 'read' FROM school
ON CONFLICT (school_id, role_code, module) DO NOTHING;
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT id, 'form_teacher', 'coursebook', 'write' FROM school
ON CONFLICT (school_id, role_code, module) DO NOTHING;
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT id, 'teacher', 'coursebook', 'write' FROM school
ON CONFLICT (school_id, role_code, module) DO NOTHING;
