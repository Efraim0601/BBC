-- ============================================================================
--  V17 — Documents & orientation
--        Two tables giving administration a per-student document register
--        (metadata only — no binary is stored, just a filing ref/URL) and the
--        record of conseil de classe orientation/conseil decisions.
-- ============================================================================

CREATE TABLE student_document (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id    UUID NOT NULL REFERENCES school(id),
    student_id   UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    kind         VARCHAR(24) NOT NULL,   -- birth_cert | photo | prior_report | certificate | medical | other
    title        VARCHAR(160) NOT NULL,
    note         TEXT,
    file_ref     VARCHAR(300),           -- external URL or filing reference (no binary stored)
    uploaded_by  UUID REFERENCES app_user(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_student_document_student ON student_document (school_id, student_id);

CREATE TABLE orientation_decision (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id      UUID NOT NULL REFERENCES school(id),
    student_id     UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    academic_year  VARCHAR(16) NOT NULL,        -- e.g. 2024-2025
    stage          VARCHAR(60) NOT NULL,        -- e.g. "Orientation 3ème"
    recommendation TEXT,
    decision       TEXT,
    council_date   DATE,
    created_by     UUID REFERENCES app_user(id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_orientation_decision_student ON orientation_decision (school_id, student_id);

-- Make the new "documents" module visible to existing tenants. Principal &
-- prefect get write; the form teacher gets read. (Fresh prod installs are
-- seeded by ProductionBootstrap; the demo dataset refines this in db/seed.)
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT id, 'principal', 'documents', 'write' FROM school
ON CONFLICT (school_id, role_code, module) DO NOTHING;
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT id, 'prefect', 'documents', 'write' FROM school
ON CONFLICT (school_id, role_code, module) DO NOTHING;
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT id, 'form_teacher', 'documents', 'read' FROM school
ON CONFLICT (school_id, role_code, module) DO NOTHING;
