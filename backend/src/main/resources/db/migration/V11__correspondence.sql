-- ============================================================================
--  V11 — Carnet de correspondance (correspondence notices)
--        Staff ↔ parent notices with an optional read-acknowledgement /
--        signature. Gives administration a traceable channel and parents a
--        clear "have I read & signed this" status per notice.
-- ============================================================================

CREATE TABLE correspondence (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id        UUID NOT NULL REFERENCES school(id),
    student_id       UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    category         VARCHAR(20) NOT NULL,    -- info | convocation | absence | reminder | congrats
    subject          VARCHAR(160) NOT NULL,
    body             TEXT NOT NULL,
    requires_ack     BOOLEAN NOT NULL DEFAULT true,
    acknowledged_at  TIMESTAMPTZ,
    acknowledged_by  VARCHAR(120),            -- parent name who signed
    sender_name      VARCHAR(120),
    created_by       UUID REFERENCES app_user(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_correspondence_student ON correspondence (school_id, student_id, created_at DESC);

-- Make the new "messages" module visible to existing tenants. Staff with a
-- pastoral role get write; teachers get read. (Fresh prod installs are seeded
-- by ProductionBootstrap; the demo dataset refines this in db/seed.)
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT id, 'principal', 'messages', 'write' FROM school
ON CONFLICT (school_id, role_code, module) DO NOTHING;
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT id, 'prefect', 'messages', 'write' FROM school
ON CONFLICT (school_id, role_code, module) DO NOTHING;
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT id, 'form_teacher', 'messages', 'write' FROM school
ON CONFLICT (school_id, role_code, module) DO NOTHING;
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT id, 'teacher', 'messages', 'read' FROM school
ON CONFLICT (school_id, role_code, module) DO NOTHING;
