-- BAY-52..57: make resource availability explicit and auditable.  The
-- published timetable remains immutable; drift is calculated against the
-- canonical dated assignment at read time.

CREATE TABLE IF NOT EXISTS timetable_teacher_availability (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES employee(id) ON DELETE CASCADE,
    day_idx INT NOT NULL CHECK (day_idx BETWEEN 0 AND 6),
    slot_idx INT NOT NULL CHECK (slot_idx BETWEEN 0 AND 15),
    available BOOLEAN NOT NULL DEFAULT true,
    reason VARCHAR(240),
    UNIQUE (school_id, employee_id, day_idx, slot_idx)
);

CREATE INDEX IF NOT EXISTS idx_timetable_teacher_availability
    ON timetable_teacher_availability(school_id, employee_id, day_idx, slot_idx);

CREATE INDEX IF NOT EXISTS idx_timetable_room_availability_effective
    ON timetable_room_availability(school_id, room_id, day_idx, slot_idx, available);
