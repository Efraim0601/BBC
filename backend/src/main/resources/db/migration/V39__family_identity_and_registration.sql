-- BAY-8: family identity, guardian relationships, account lifecycle and retry-safe imports.

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS email VARCHAR(160);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS normalized_email VARCHAR(160);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMPTZ;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS failed_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS credentials_version INT NOT NULL DEFAULT 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_app_user_school_email
    ON app_user(school_id, normalized_email) WHERE normalized_email IS NOT NULL;

CREATE TABLE guardian (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id),
    app_user_id UUID REFERENCES app_user(id) ON DELETE SET NULL,
    display_name VARCHAR(120) NOT NULL,
    email VARCHAR(160),
    normalized_email VARCHAR(160),
    phone VARCHAR(40),
    normalized_phone VARCHAR(40),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE','INVITED','NO_PORTAL','INACTIVE','MERGED')),
    merged_into_id UUID REFERENCES guardian(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, app_user_id)
);
CREATE UNIQUE INDEX uq_guardian_school_email ON guardian(school_id, normalized_email)
    WHERE normalized_email IS NOT NULL AND status <> 'MERGED';
CREATE INDEX idx_guardian_phone ON guardian(school_id, normalized_phone);
CREATE INDEX idx_guardian_name ON guardian(school_id, lower(display_name));

CREATE TABLE student_guardian (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id),
    student_id UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    guardian_id UUID NOT NULL REFERENCES guardian(id),
    relationship_type VARCHAR(32) NOT NULL,
    legal_guardian BOOLEAN NOT NULL DEFAULT true,
    lives_with BOOLEAN NOT NULL DEFAULT false,
    emergency_priority INT,
    pickup_authorized BOOLEAN NOT NULL DEFAULT false,
    finance_responsible BOOLEAN NOT NULL DEFAULT false,
    receives_academic BOOLEAN NOT NULL DEFAULT true,
    receives_attendance BOOLEAN NOT NULL DEFAULT true,
    receives_finance BOOLEAN NOT NULL DEFAULT false,
    receives_discipline BOOLEAN NOT NULL DEFAULT true,
    receives_health BOOLEAN NOT NULL DEFAULT false,
    portal_access BOOLEAN NOT NULL DEFAULT true,
    effective_from DATE NOT NULL DEFAULT CURRENT_DATE,
    effective_to DATE,
    notes TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, student_id, guardian_id),
    CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CHECK (emergency_priority IS NULL OR emergency_priority BETWEEN 1 AND 9)
);
CREATE INDEX idx_student_guardian_student ON student_guardian(school_id, student_id, effective_to);
CREATE INDEX idx_student_guardian_guardian ON student_guardian(school_id, guardian_id, effective_to);

CREATE TABLE guardian_account_token (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id),
    guardian_id UUID NOT NULL REFERENCES guardian(id) ON DELETE CASCADE,
    token_type VARCHAR(16) NOT NULL CHECK (token_type IN ('INVITE','VERIFY_EMAIL','RESET_PASSWORD')),
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_guardian_token_lookup ON guardian_account_token(token_hash, token_type, expires_at);

CREATE TABLE family_import_job (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id),
    schema_version VARCHAR(16) NOT NULL DEFAULT '1',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','VALIDATED','RUNNING','COMPLETED','COMPLETED_ERRORS','FAILED')),
    source_name VARCHAR(200),
    total_rows INT NOT NULL DEFAULT 0,
    valid_rows INT NOT NULL DEFAULT 0,
    created_rows INT NOT NULL DEFAULT 0,
    linked_guardians INT NOT NULL DEFAULT 0,
    failed_rows INT NOT NULL DEFAULT 0,
    created_by UUID REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);

CREATE TABLE family_import_row (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id),
    job_id UUID NOT NULL REFERENCES family_import_job(id) ON DELETE CASCADE,
    row_number INT NOT NULL,
    external_key VARCHAR(120) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','VALID','CREATE','LINK','SKIP','ERROR','COMMITTED')),
    message TEXT,
    student_id UUID REFERENCES student(id),
    guardian_id UUID REFERENCES guardian(id),
    UNIQUE (school_id, job_id, external_key)
);

-- Backfill existing parent accounts and links without deleting the compatibility table.
INSERT INTO guardian(id, school_id, app_user_id, display_name, email, normalized_email, status)
SELECT gen_random_uuid(), u.school_id, u.id, u.display_name, u.email, lower(trim(u.email)),
       CASE WHEN u.active THEN 'ACTIVE' ELSE 'INACTIVE' END
FROM app_user u
WHERE u.role_code = 'parent'
  AND NOT EXISTS (SELECT 1 FROM guardian g WHERE g.school_id=u.school_id AND g.app_user_id=u.id);

INSERT INTO student_guardian(school_id, student_id, guardian_id, relationship_type,
    legal_guardian, pickup_authorized, finance_responsible, receives_finance, portal_access)
SELECT s.school_id, ps.student_id, g.id, 'GUARDIAN', true, true, true, true, true
FROM parent_student ps
JOIN student s ON s.id=ps.student_id
JOIN guardian g ON g.app_user_id=ps.parent_user_id AND g.school_id=s.school_id
ON CONFLICT (school_id, student_id, guardian_id) DO NOTHING;
