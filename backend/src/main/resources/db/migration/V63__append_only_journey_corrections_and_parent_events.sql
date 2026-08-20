-- BAY-11: retain manual journey history and make corrections/voids auditable.

ALTER TABLE journey_entry ADD COLUMN IF NOT EXISTS voided_at TIMESTAMPTZ;
ALTER TABLE journey_entry ADD COLUMN IF NOT EXISTS voided_by UUID REFERENCES app_user(id);
ALTER TABLE journey_entry ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);

CREATE TABLE IF NOT EXISTS journey_entry_revision (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    journey_entry_id UUID NOT NULL REFERENCES journey_entry(id) ON DELETE RESTRICT,
    action VARCHAR(16) NOT NULL CHECK (action IN ('CORRECTED','VOIDED')),
    academic_year VARCHAR(32) NOT NULL,
    class_name VARCHAR(120) NOT NULL,
    level VARCHAR(32),
    subsystem VARCHAR(8),
    result VARCHAR(32) NOT NULL,
    general_average NUMERIC(6,2),
    rank INT,
    class_size INT,
    decision TEXT,
    note TEXT,
    actor_user_id UUID REFERENCES app_user(id),
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_journey_entry_revision_entry
    ON journey_entry_revision(school_id, journey_entry_id, created_at);

CREATE INDEX IF NOT EXISTS idx_journey_event_parent_student
    ON journey_event(school_id, student_id, visibility, created_at);
