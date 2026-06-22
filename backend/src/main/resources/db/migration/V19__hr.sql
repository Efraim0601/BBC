-- ============================================================================
--  V19 — HR / Operations: departments + leave management
--  Closes the "staff aren't organised into departments / no leave" review gaps.
-- ============================================================================

-- ---- Departments ------------------------------------------------------------
CREATE TABLE department (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES school(id),
    name            VARCHAR(80) NOT NULL,
    head_employee_id UUID REFERENCES employee(id),
    UNIQUE (school_id, name)
);

-- Each employee may belong to one department.
ALTER TABLE employee ADD COLUMN department_id UUID REFERENCES department(id);

-- ---- Leave management -------------------------------------------------------
CREATE TABLE leave_request (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES school(id),
    employee_id UUID NOT NULL REFERENCES employee(id) ON DELETE CASCADE,
    type        VARCHAR(20) NOT NULL,                 -- annual | sick | maternity | unpaid | other
    start_date  DATE NOT NULL,
    end_date    DATE NOT NULL,
    days        INT NOT NULL DEFAULT 0,               -- working span in days (inclusive)
    reason      TEXT,
    status      VARCHAR(12) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','approved','rejected')),
    decided_by  UUID REFERENCES app_user(id),
    decided_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_leave_school ON leave_request (school_id, created_at DESC);
CREATE INDEX idx_leave_employee ON leave_request (employee_id);
