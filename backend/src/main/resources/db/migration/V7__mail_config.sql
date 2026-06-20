-- ============================================================================
--  V7 — per-tenant SMTP configuration (editable from the admin Settings UI).
--  (V4..V6 are reserved by the demo seed in db/seed; this stays a schema-only
--   migration applied in every profile.)
-- ============================================================================
CREATE TABLE mail_config (
    school_id              UUID PRIMARY KEY REFERENCES school(id),
    enabled                BOOLEAN NOT NULL DEFAULT false,
    host                   VARCHAR(160),
    port                   INT NOT NULL DEFAULT 587,
    username               VARCHAR(160),
    password               VARCHAR(255),
    from_address           VARCHAR(160),
    from_name              VARCHAR(120),
    use_tls                BOOLEAN NOT NULL DEFAULT true,
    notify_on_user_create  BOOLEAN NOT NULL DEFAULT true,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
