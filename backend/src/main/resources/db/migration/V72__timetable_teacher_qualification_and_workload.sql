-- BAY-54: optional qualification and workload policies for the canonical
-- teacher assignment.  Empty policy tables preserve the existing workflow;
-- configured policies become publish blockers with repairable details.

CREATE TABLE IF NOT EXISTS timetable_teacher_workload_policy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES employee(id) ON DELETE CASCADE,
    max_slots_per_day INT CHECK (max_slots_per_day IS NULL OR max_slots_per_day > 0),
    max_slots_per_week INT CHECK (max_slots_per_week IS NULL OR max_slots_per_week > 0),
    effective_from DATE NOT NULL,
    effective_to DATE,
    reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CHECK (max_slots_per_day IS NOT NULL OR max_slots_per_week IS NOT NULL),
    UNIQUE (school_id, employee_id, effective_from)
);
CREATE INDEX IF NOT EXISTS idx_teacher_workload_policy_effective
    ON timetable_teacher_workload_policy(school_id, employee_id, effective_from, effective_to);

CREATE TABLE IF NOT EXISTS timetable_teacher_qualification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES employee(id) ON DELETE CASCADE,
    qualification_code VARCHAR(80) NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE,
    evidence_reference VARCHAR(240),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (valid_to IS NULL OR valid_to >= valid_from),
    UNIQUE (school_id, employee_id, qualification_code, valid_from)
);
CREATE INDEX IF NOT EXISTS idx_teacher_qualification_effective
    ON timetable_teacher_qualification(school_id, employee_id, qualification_code, valid_from, valid_to);

CREATE TABLE IF NOT EXISTS timetable_subject_qualification_requirement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    subject_code VARCHAR(32) NOT NULL,
    qualification_code VARCHAR(80) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (effective_to IS NULL OR effective_to >= effective_from),
    UNIQUE (school_id, academic_session_id, subject_code, qualification_code, effective_from)
);
CREATE INDEX IF NOT EXISTS idx_subject_qualification_requirement
    ON timetable_subject_qualification_requirement(school_id, academic_session_id, subject_code, effective_from, effective_to);
