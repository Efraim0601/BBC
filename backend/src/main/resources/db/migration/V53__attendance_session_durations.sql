-- BAY-10/BAY-67: make attendance analytics hour-based as well as count-based.
ALTER TABLE attendance_session
    ADD COLUMN duration_minutes INT NOT NULL DEFAULT 0 CHECK (duration_minutes >= 0);

-- Backfill daily calls from the session's configured school day.
UPDATE attendance_session a
   SET duration_minutes = COALESCE(EXTRACT(EPOCH FROM (d.end_time - d.start_time)) / 60, 0)::int
  FROM school_calendar_day d
 WHERE a.academic_session_id = d.academic_session_id
   AND a.school_id = d.school_id
   AND a.model = 'DAILY'
   AND d.day_of_week = EXTRACT(ISODOW FROM a.session_date)::int
   AND a.duration_minutes = 0;

-- Backfill subject-period calls from the timetable's configured time slot.
UPDATE attendance_session a
   SET duration_minutes = COALESCE(EXTRACT(EPOCH FROM (p.end_time - p.start_time)) / 60, 0)::int
  FROM timetable_period p
 WHERE a.school_id = p.school_id
   AND a.model = 'PERIOD'
   AND a.period_key = 'P' || (p.slot_idx + 1)::text
   AND p.active
   AND a.duration_minutes = 0;
