-- Primary/Kindergarten and Secondary use different bell schedules.  Existing
-- periods are preserved as the Primary profile; no existing timetable slot or
-- attendance record is rewritten.
ALTER TABLE timetable_period
    ADD COLUMN IF NOT EXISTS period_profile VARCHAR(12);

UPDATE timetable_period
   SET period_profile='PRIMARY'
 WHERE period_profile IS NULL OR btrim(period_profile)='';

ALTER TABLE timetable_period
    ALTER COLUMN period_profile SET NOT NULL;

ALTER TABLE timetable_period
    DROP CONSTRAINT IF EXISTS timetable_period_period_profile_check;
ALTER TABLE timetable_period
    ADD CONSTRAINT timetable_period_period_profile_check
    CHECK (period_profile IN ('PRIMARY','SECONDARY'));

ALTER TABLE timetable_period
    DROP CONSTRAINT IF EXISTS timetable_period_school_id_slot_idx_key;
DROP INDEX IF EXISTS uq_timetable_period_profile_slot;
CREATE UNIQUE INDEX uq_timetable_period_profile_slot
    ON timetable_period(school_id, period_profile, slot_idx);

-- Resource availability used the same global slot number as the former
-- bell schedule. Keep all existing entries with the primary timetable and
-- make secondary availability independently configurable.
ALTER TABLE timetable_room_availability
    ADD COLUMN IF NOT EXISTS period_profile VARCHAR(12);
UPDATE timetable_room_availability
   SET period_profile='PRIMARY'
 WHERE period_profile IS NULL OR btrim(period_profile)='';
ALTER TABLE timetable_room_availability
    ALTER COLUMN period_profile SET NOT NULL;
ALTER TABLE timetable_room_availability
    DROP CONSTRAINT IF EXISTS timetable_room_availability_period_profile_check;
ALTER TABLE timetable_room_availability
    ADD CONSTRAINT timetable_room_availability_period_profile_check
    CHECK (period_profile IN ('PRIMARY','SECONDARY'));
ALTER TABLE timetable_room_availability
    DROP CONSTRAINT IF EXISTS timetable_room_availability_room_id_day_idx_slot_idx_key;
DROP INDEX IF EXISTS ux_timetable_room_availability_profile_slot;
CREATE UNIQUE INDEX ux_timetable_room_availability_profile_slot
    ON timetable_room_availability(room_id, period_profile, day_idx, slot_idx);

ALTER TABLE timetable_teacher_availability
    ADD COLUMN IF NOT EXISTS period_profile VARCHAR(12);
UPDATE timetable_teacher_availability
   SET period_profile='PRIMARY'
 WHERE period_profile IS NULL OR btrim(period_profile)='';
ALTER TABLE timetable_teacher_availability
    ALTER COLUMN period_profile SET NOT NULL;
ALTER TABLE timetable_teacher_availability
    DROP CONSTRAINT IF EXISTS timetable_teacher_availability_period_profile_check;
ALTER TABLE timetable_teacher_availability
    ADD CONSTRAINT timetable_teacher_availability_period_profile_check
    CHECK (period_profile IN ('PRIMARY','SECONDARY'));
ALTER TABLE timetable_teacher_availability
    DROP CONSTRAINT IF EXISTS timetable_teacher_availabilit_school_id_employee_id_day_idx_key;
DROP INDEX IF EXISTS ux_timetable_teacher_availability_profile_slot;
CREATE UNIQUE INDEX ux_timetable_teacher_availability_profile_slot
    ON timetable_teacher_availability(school_id, employee_id, period_profile, day_idx, slot_idx);

-- The old unique indexes treat P1 in every level as the same clock time.
-- That would both reject valid primary/secondary schedules and still miss
-- overlaps where the two profiles use different indices. Service-level
-- interval validation below protects the real clock intervals instead.
DROP INDEX IF EXISTS ux_timetable_teacher_version_no_double_booking;
DROP INDEX IF EXISTS ux_timetable_room_version_no_double_booking;

-- The secondary schedule supplied by BBC has seven one-hour teaching blocks
-- and two breaks.  Breaks are deliberately not timetable periods, so they can
-- never create attendance sessions.
INSERT INTO timetable_period(school_id, period_profile, slot_idx, label, start_time, end_time, active)
SELECT s.id, 'SECONDARY', p.slot_idx, p.label, p.start_time, p.end_time, true
  FROM school s
 CROSS JOIN (VALUES
    (0, 'P1', '07:30'::time, '08:30'::time),
    (1, 'P2', '08:30'::time, '09:30'::time),
    (2, 'P3', '09:30'::time, '10:30'::time),
    (3, 'P4', '10:45'::time, '11:45'::time),
    (4, 'P5', '11:45'::time, '12:45'::time),
    (5, 'P6', '13:00'::time, '14:00'::time),
    (6, 'P7', '14:00'::time, '15:00'::time)
 ) p(slot_idx, label, start_time, end_time)
ON CONFLICT (school_id, period_profile, slot_idx) DO NOTHING;
