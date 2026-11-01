-- A timetable can display a source-specific lesson label (for example
-- “H/G/ECM” or “FR (lecture)”) while retaining a canonical class subject for
-- teacher permissions, attendance, and report cards.
ALTER TABLE timetable_slot
    ADD COLUMN IF NOT EXISTS display_label VARCHAR(100);
