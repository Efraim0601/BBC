-- V126 — the full-school lifecycle fixture uses descriptive subject codes such
-- as LANGUAGE_ACTIVITIES and COMPUTER_SCIENCE.  The original VARCHAR(8)
-- limits predate subsystem-aware curriculum and silently reject those codes.
-- Widen every legacy subject-code carrier together so the same code remains
-- usable in setup, grade entry, timetable and coursebook journeys.
ALTER TABLE subject
    ALTER COLUMN code TYPE VARCHAR(32);

ALTER TABLE grade
    ALTER COLUMN subject_code TYPE VARCHAR(32);

ALTER TABLE timetable_slot
    ALTER COLUMN subject_code TYPE VARCHAR(32);

ALTER TABLE coursebook_entry
    ALTER COLUMN subject_code TYPE VARCHAR(32);
