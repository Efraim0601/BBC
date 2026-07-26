-- Temporary staff self-registration portal (admin toggle + applications queue).

ALTER TABLE school
    ADD COLUMN IF NOT EXISTS staff_portal_enabled BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS staff_portal_slug    VARCHAR(64),
    ADD COLUMN IF NOT EXISTS staff_portal_token   VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_school_staff_portal_slug
    ON school (staff_portal_slug)
    WHERE staff_portal_slug IS NOT NULL;

-- Applications submitted via the public portal, awaiting HR validation.
CREATE TABLE staff_application (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES school(id),
    status          VARCHAR(16) NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending', 'accepted', 'rejected', 'finalized')),
    name            VARCHAR(120) NOT NULL,
    sex             CHAR(1) CHECK (sex IS NULL OR sex IN ('M', 'F')),
    type            VARCHAR(16) NOT NULL DEFAULT 'Permanent',
    email           VARCHAR(160),
    phone           VARCHAR(40),
    form_class      VARCHAR(64),
    department_hint VARCHAR(120),
    desired_roles   VARCHAR(240),
    notes           TEXT,
    reject_reason   VARCHAR(400),
    employee_id     UUID REFERENCES employee(id),
    submitted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at      TIMESTAMPTZ,
    decided_by      UUID,
    finalized_at    TIMESTAMPTZ
);

CREATE INDEX idx_staff_application_school_status
    ON staff_application (school_id, status, submitted_at DESC);

CREATE INDEX idx_staff_application_email
    ON staff_application (school_id, lower(email))
    WHERE email IS NOT NULL;
