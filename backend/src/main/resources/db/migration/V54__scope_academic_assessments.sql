-- BAY-10: assessments may be generic for compatibility, or scoped to a
-- session class and/or subject. Scoped definitions are used by grade entry
-- and result calculation; generic rows remain valid migration input.
ALTER TABLE academic_assessment
    ADD COLUMN subject_code VARCHAR(32),
    ADD COLUMN class_id UUID REFERENCES school_class(id) ON DELETE CASCADE;

ALTER TABLE academic_assessment
    DROP CONSTRAINT IF EXISTS academic_assessment_school_id_reporting_period_id_code_key;

ALTER TABLE academic_assessment
    DROP CONSTRAINT IF EXISTS academic_assessment_school_id_reporting_period_id_display_order_key;

CREATE UNIQUE INDEX uq_academic_assessment_scope_code
    ON academic_assessment(
        school_id,
        reporting_period_id,
        COALESCE(class_id, '00000000-0000-0000-0000-000000000000'::uuid),
        COALESCE(subject_code, ''),
        code
    );

CREATE UNIQUE INDEX uq_academic_assessment_scope_order
    ON academic_assessment(
        school_id,
        reporting_period_id,
        COALESCE(class_id, '00000000-0000-0000-0000-000000000000'::uuid),
        COALESCE(subject_code, ''),
        display_order
    );

CREATE INDEX idx_academic_assessment_scope
    ON academic_assessment(school_id, reporting_period_id, class_id, subject_code, display_order);
