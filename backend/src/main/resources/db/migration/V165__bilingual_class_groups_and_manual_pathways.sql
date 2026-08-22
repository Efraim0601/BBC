-- Bilingual primary/maternelle class groups.
--
-- A student remains enrolled once per academic session.  A class group may
-- expose one or two programme classes (FR/EN), allowing one roster to feed two
-- curricula and two report-card streams without duplicating pupils.

CREATE TABLE academic_cohort (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    code VARCHAR(64) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    level VARCHAR(12) NOT NULL CHECK (level IN ('maternelle','primary','secondary')),
    mode VARCHAR(24) NOT NULL DEFAULT 'SINGLE_PROGRAMME'
        CHECK (mode IN ('SINGLE_PROGRAMME','SHARED_BILINGUAL')),
    attendance_mode VARCHAR(16) NOT NULL DEFAULT 'DAILY_SHARED'
        CHECK (attendance_mode IN ('DAILY_SHARED','PROGRAMME')),
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED')),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, academic_session_id, code)
);
CREATE INDEX idx_academic_cohort_session
    ON academic_cohort(school_id, academic_session_id, level, status);

CREATE TABLE academic_cohort_programme (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    cohort_id UUID NOT NULL REFERENCES academic_cohort(id) ON DELETE CASCADE,
    school_class_id UUID NOT NULL REFERENCES school_class(id) ON DELETE CASCADE,
    subsystem VARCHAR(2) NOT NULL CHECK (subsystem IN ('FR','EN')),
    display_order INT NOT NULL DEFAULT 1,
    report_card_enabled BOOLEAN NOT NULL DEFAULT true,
    active BOOLEAN NOT NULL DEFAULT true,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, academic_session_id, cohort_id, school_class_id),
    UNIQUE (school_id, academic_session_id, cohort_id, subsystem),
    UNIQUE (school_id, academic_session_id, school_class_id)
);
CREATE INDEX idx_academic_cohort_programme_class
    ON academic_cohort_programme(school_id, academic_session_id, school_class_id, active);

ALTER TABLE student_enrollment
    ADD COLUMN IF NOT EXISTS cohort_id UUID REFERENCES academic_cohort(id);
CREATE INDEX IF NOT EXISTS idx_student_enrollment_cohort
    ON student_enrollment(school_id, academic_session_id, cohort_id, status);

-- Compatibility backfill: every existing class/session becomes a one-programme
-- group.  Administrators can later pair two such groups through the setup UI;
-- no historical enrollment is duplicated.
INSERT INTO academic_cohort (
    school_id, academic_session_id, code, display_name, level, mode, status
)
SELECT c.school_id, s.id,
       'CLASS-' || replace(c.id::text, '-', ''),
       c.name,
       c.level,
       'SINGLE_PROGRAMME',
       CASE WHEN s.is_current THEN 'ACTIVE' ELSE 'ARCHIVED' END
  FROM school_class c
  JOIN academic_session s ON s.school_id = c.school_id
ON CONFLICT (school_id, academic_session_id, code) DO NOTHING;

INSERT INTO academic_cohort_programme (
    school_id, academic_session_id, cohort_id, school_class_id,
    subsystem, display_order, report_card_enabled, active
)
SELECT c.school_id, s.id, h.id, c.id, c.subsystem, 1, true, true
  FROM school_class c
  JOIN academic_session s ON s.school_id = c.school_id
  JOIN academic_cohort h
    ON h.school_id = c.school_id
   AND h.academic_session_id = s.id
   AND h.code = 'CLASS-' || replace(c.id::text, '-', '')
ON CONFLICT (school_id, academic_session_id, school_class_id) DO NOTHING;

UPDATE student_enrollment e
   SET cohort_id = p.cohort_id
  FROM academic_cohort_programme p
 WHERE e.cohort_id IS NULL
   AND p.school_id = e.school_id
   AND p.academic_session_id = e.academic_session_id
   AND p.school_class_id = e.school_class_id;

CREATE TABLE student_pathway_choice (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    source_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    target_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    source_cohort_id UUID REFERENCES academic_cohort(id) ON DELETE SET NULL,
    target_cohort_id UUID NOT NULL REFERENCES academic_cohort(id) ON DELETE RESTRICT,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','CONFIRMED','LOCKED','CANCELLED')),
    reason VARCHAR(500),
    chosen_by UUID REFERENCES app_user(id),
    chosen_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, student_id, target_session_id)
);
CREATE INDEX idx_student_pathway_choice_source
    ON student_pathway_choice(school_id, source_session_id, source_cohort_id, status);
CREATE INDEX idx_student_pathway_choice_target
    ON student_pathway_choice(school_id, target_session_id, target_cohort_id, status);
