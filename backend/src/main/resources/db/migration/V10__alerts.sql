-- ============================================================================
--  V10 — Alertes proactives (proactive at-risk detection)
--        A scan engine recomputes signals from the operational data
--        (attendance, discipline, fees, grades) and upserts them here, keyed by
--        dedup_key so acknowledged / resolved alerts are never resurrected.
-- ============================================================================

CREATE TABLE alert (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES school(id),
    student_id  UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    type        VARCHAR(20) NOT NULL,        -- grade_drop | absences | discipline | unpaid
    severity    VARCHAR(10) NOT NULL,        -- info | warn | critical
    title       VARCHAR(160) NOT NULL,
    detail      TEXT,
    dedup_key   VARCHAR(120) NOT NULL,       -- e.g. type:studentId, used to upsert
    status      VARCHAR(10) NOT NULL DEFAULT 'open',   -- open | ack | resolved
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    ack_by      UUID REFERENCES app_user(id),
    ack_at      TIMESTAMPTZ,
    UNIQUE (school_id, dedup_key)
);
CREATE INDEX idx_alert_status ON alert (school_id, status, severity);

-- Make the new "alerts" module visible to existing tenants. Principal & prefect
-- get write; the bursar (econome) and form teacher get read. (Fresh prod
-- installs are seeded by ProductionBootstrap; the demo dataset refines this.)
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT id, 'principal', 'alerts', 'write' FROM school
ON CONFLICT (school_id, role_code, module) DO NOTHING;
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT id, 'prefect', 'alerts', 'write' FROM school
ON CONFLICT (school_id, role_code, module) DO NOTHING;
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT id, 'econome', 'alerts', 'read' FROM school
ON CONFLICT (school_id, role_code, module) DO NOTHING;
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT id, 'form_teacher', 'alerts', 'read' FROM school
ON CONFLICT (school_id, role_code, module) DO NOTHING;
