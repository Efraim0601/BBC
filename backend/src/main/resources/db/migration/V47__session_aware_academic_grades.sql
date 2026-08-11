-- BAY-10/BAY-33/BAY-34: session-aware assessments, grades, and subject remarks.

CREATE TABLE academic_assessment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    reporting_period_id UUID NOT NULL REFERENCES academic_reporting_period(id) ON DELETE CASCADE,
    code VARCHAR(40) NOT NULL,
    label VARCHAR(160) NOT NULL,
    assessment_type VARCHAR(24) NOT NULL DEFAULT 'EVALUATION',
    max_score NUMERIC(6,2) NOT NULL DEFAULT 20 CHECK (max_score > 0),
    weight NUMERIC(8,3) NOT NULL DEFAULT 1 CHECK (weight > 0),
    mandatory BOOLEAN NOT NULL DEFAULT true,
    display_order INT NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, reporting_period_id, code),
    UNIQUE (school_id, reporting_period_id, display_order)
);
CREATE INDEX idx_academic_assessment_period
    ON academic_assessment(school_id, reporting_period_id, display_order);

CREATE TABLE academic_grade (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    reporting_period_id UUID NOT NULL REFERENCES academic_reporting_period(id) ON DELETE CASCADE,
    assessment_id UUID NOT NULL REFERENCES academic_assessment(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    enrollment_id UUID REFERENCES student_enrollment(id),
    subject_code VARCHAR(32) NOT NULL,
    entered_by UUID REFERENCES app_user(id),
    teacher_id UUID REFERENCES employee(id),
    mark NUMERIC(8,3),
    value_status VARCHAR(16) NOT NULL DEFAULT 'MISSING'
        CHECK (value_status IN ('SCORED','MISSING','ABSENT','EXEMPT')),
    workflow_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (workflow_status IN ('DRAFT','SUBMITTED','RETURNED','ACCEPTED','LOCKED')),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_academic_grade_mark CHECK (
        (value_status = 'SCORED' AND mark IS NOT NULL AND mark >= 0)
        OR (value_status <> 'SCORED')
    ),
    UNIQUE (school_id, student_id, assessment_id, subject_code)
);
CREATE INDEX idx_academic_grade_student_period
    ON academic_grade(school_id, student_id, reporting_period_id, subject_code);
CREATE INDEX idx_academic_grade_assessment
    ON academic_grade(school_id, assessment_id, subject_code);

CREATE TABLE subject_result_comment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    reporting_period_id UUID NOT NULL REFERENCES academic_reporting_period(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    enrollment_id UUID REFERENCES student_enrollment(id),
    subject_code VARCHAR(32) NOT NULL,
    teacher_id UUID REFERENCES employee(id),
    comment TEXT,
    appreciation_code VARCHAR(40),
    workflow_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (workflow_status IN ('DRAFT','SUBMITTED','RETURNED','ACCEPTED','LOCKED')),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, student_id, reporting_period_id, subject_code)
);
