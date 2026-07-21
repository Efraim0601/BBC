-- ============================================================================
--  V21 — Class resources: supplies (fournitures) & payable book lists (livres)
--        Per class, staff configure a list of items and PUBLISH it; parents of a
--        child in that class can then see the published list. Book prices are
--        informational only (integer FCFA) — NOT wired into the finance ledger.
-- ============================================================================

CREATE TABLE class_resource_item (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES school(id),
    class_id    UUID NOT NULL REFERENCES school_class(id) ON DELETE CASCADE,
    kind        VARCHAR(8) NOT NULL CHECK (kind IN ('supplies','books')),
    label       VARCHAR(160) NOT NULL,   -- supply label OR book title
    quantity    INT,                     -- supplies: quantity needed (books: null)
    price       BIGINT,                  -- books: unit price in FCFA  (supplies: null)
    note        VARCHAR(200),
    position    INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_class_resource ON class_resource_item (school_id, class_id, kind);

-- Publish state per (class, kind). A row exists once the list has been touched.
CREATE TABLE class_resource_publication (
    school_id    UUID NOT NULL REFERENCES school(id),
    class_id     UUID NOT NULL REFERENCES school_class(id) ON DELETE CASCADE,
    kind         VARCHAR(8) NOT NULL CHECK (kind IN ('supplies','books')),
    published    BOOLEAN NOT NULL DEFAULT false,
    published_at TIMESTAMPTZ,
    PRIMARY KEY (school_id, class_id, kind)
);

-- New "classkit" module (fournitures & livres). Staff configure; parents read via
-- the parent portal endpoints. Fresh prod installs seeded by ProductionBootstrap.
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT id, 'principal', 'classkit', 'write' FROM school
ON CONFLICT (school_id, role_code, module) DO NOTHING;
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT id, 'form_teacher', 'classkit', 'write' FROM school
ON CONFLICT (school_id, role_code, module) DO NOTHING;
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT id, 'teacher', 'classkit', 'read' FROM school
ON CONFLICT (school_id, role_code, module) DO NOTHING;
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT id, 'prefect', 'classkit', 'read' FROM school
ON CONFLICT (school_id, role_code, module) DO NOTHING;
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT id, 'econome', 'classkit', 'read' FROM school
ON CONFLICT (school_id, role_code, module) DO NOTHING;
