-- BAY-67 compatibility bridge.
--
-- The publication lock state is named LOCKED_BY_PUBLICATION (21 characters).
-- V98 added it to the CHECK constraints but the original V49 status columns
-- remained varchar(16), which made an otherwise valid bulletin publication
-- fail while locking its attendance/council evidence.

ALTER TABLE attendance_period_adjustment
    ALTER COLUMN status TYPE VARCHAR(32);

ALTER TABLE student_period_conduct
    ALTER COLUMN status TYPE VARCHAR(32);
