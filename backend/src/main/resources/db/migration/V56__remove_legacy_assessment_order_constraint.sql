-- The legacy unique constraint used one display order for the whole period.
-- Scoped assessments need the same order to be reusable per class/subject.
ALTER TABLE academic_assessment
    DROP CONSTRAINT IF EXISTS academic_assessment_school_id_reporting_period_id_display_o_key;
