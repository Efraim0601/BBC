-- ============================================================================
--  V2 — extra schema: Parent Portal (suggestions) + Academic (bulletin validation)
--       Schema only — demo data lives in db/seed (applied with the `demo` profile).
-- ============================================================================

-- ---- Parent suggestions box (CDC §14) --------------------------------------
CREATE TABLE parent_suggestion (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES school(id),
    parent_user_id  UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    category        VARCHAR(20) NOT NULL,        -- suggestion | question | complaint | thanks
    message         TEXT NOT NULL,
    status          VARCHAR(12) NOT NULL DEFAULT 'new',  -- new | read | handled
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_suggestion_school ON parent_suggestion (school_id, created_at DESC);

-- ---- Bulletin validation + general appreciation (CDC §6.3/6.4) -------------
CREATE TABLE bulletin_validation (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id               UUID NOT NULL REFERENCES school(id),
    student_id              UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    sequence                INT NOT NULL,
    validated               BOOLEAN NOT NULL DEFAULT false,
    general_appreciation    TEXT,
    validated_by            UUID REFERENCES app_user(id),
    validated_at            TIMESTAMPTZ,
    UNIQUE (school_id, student_id, sequence)
);
