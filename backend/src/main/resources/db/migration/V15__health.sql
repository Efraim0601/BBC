-- ============================================================================
--  V15 — Santé & vie scolaire (student health)
--        Per-student medical record (blood group, allergies, conditions,
--        vaccinations, attending doctor, biometrics), the log of infirmary
--        visits, and the extracurricular activities (clubs, sport, art) a
--        student takes part in. Gives administration & parents a single view
--        of the pupil's wellbeing and school life.
-- ============================================================================

CREATE TABLE health_record (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id     UUID NOT NULL REFERENCES school(id),
    student_id    UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    blood_group   VARCHAR(4),
    allergies     TEXT,
    conditions    TEXT,
    vaccinations  TEXT,
    doctor_name   VARCHAR(120),
    doctor_phone  VARCHAR(40),
    height_cm     INT,
    weight_kg     INT,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, student_id)
);

CREATE TABLE infirmary_visit (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES school(id),
    student_id  UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    visit_date  DATE NOT NULL,
    reason      VARCHAR(160) NOT NULL,
    treatment   TEXT,
    created_by  UUID REFERENCES app_user(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_infirmary_visit_student ON infirmary_visit (school_id, student_id);

CREATE TABLE student_activity (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES school(id),
    student_id  UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    name        VARCHAR(120) NOT NULL,
    category    VARCHAR(16) NOT NULL,        -- club | sport | art | other
    role        VARCHAR(80),
    season      VARCHAR(16)
);
CREATE INDEX idx_student_activity_student ON student_activity (school_id, student_id);

-- Make the new "health" module visible to existing tenants. Principal gets
-- write; the dean & form teacher get read. (Fresh prod installs are seeded by
-- ProductionBootstrap; the demo dataset refines this in db/seed.)
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT id, 'principal', 'health', 'write' FROM school
ON CONFLICT (school_id, role_code, module) DO NOTHING;
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT id, 'prefect', 'health', 'read' FROM school
ON CONFLICT (school_id, role_code, module) DO NOTHING;
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT id, 'form_teacher', 'health', 'read' FROM school
ON CONFLICT (school_id, role_code, module) DO NOTHING;
