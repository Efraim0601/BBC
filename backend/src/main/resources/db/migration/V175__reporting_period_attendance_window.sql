-- A sequence may use a narrower date interval for attendance than it uses for
-- grade entry.  Null means "follow the reporting period dates", which keeps
-- existing and newly-created periods backwards compatible.

ALTER TABLE academic_reporting_period
    ADD COLUMN attendance_start_date DATE,
    ADD COLUMN attendance_end_date DATE;

ALTER TABLE academic_reporting_period
    ADD CONSTRAINT chk_reporting_period_attendance_window
    CHECK (
        (attendance_start_date IS NULL AND attendance_end_date IS NULL)
        OR (
            attendance_start_date IS NOT NULL
            AND attendance_end_date IS NOT NULL
            AND attendance_start_date <= attendance_end_date
            AND attendance_start_date >= start_date
            AND attendance_end_date <= end_date
        )
    );
