-- A shared bilingual cohort has one enrollment but one report-card stream per
-- programme/class.  Keep the legacy NULL value for existing single-programme
-- bulletins and use this column for new class-scoped snapshots.
ALTER TABLE bulletin_version
    ADD COLUMN IF NOT EXISTS programme_class_id UUID REFERENCES school_class(id);

CREATE INDEX IF NOT EXISTS idx_bulletin_version_student_period_programme
    ON bulletin_version(school_id, student_id, reporting_period_id, programme_class_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_bulletin_version_period_programme_state
    ON bulletin_version(school_id, reporting_period_id, programme_class_id, state);
