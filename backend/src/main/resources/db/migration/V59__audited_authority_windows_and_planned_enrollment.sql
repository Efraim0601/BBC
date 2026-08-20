-- BAY-10/BAY-11 audited foundation after V58.
--
-- This migration deliberately keeps the V44/V52 tables as compatibility
-- projections.  New writes and effective teacher resolution use the
-- session-scoped assignment layer below; applied migrations are never edited.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS class_teacher_assignment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    class_id UUID NOT NULL REFERENCES school_class(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES employee(id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL DEFAULT 'HOMEROOM' CHECK (role IN ('HOMEROOM','ASSISTANT')),
    effective_from DATE NOT NULL,
    effective_to DATE,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    source VARCHAR(24) NOT NULL DEFAULT 'CLASS_SUBJECTS',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (effective_to IS NULL OR effective_to >= effective_from),
    UNIQUE (school_id, academic_session_id, class_id, employee_id, role, effective_from)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_class_teacher_assignment_one_homeroom
    ON class_teacher_assignment(school_id, academic_session_id, class_id)
    WHERE status = 'ACTIVE' AND role = 'HOMEROOM';
CREATE INDEX IF NOT EXISTS idx_class_teacher_assignment_effective
    ON class_teacher_assignment(school_id, academic_session_id, class_id, effective_from, effective_to);

CREATE TABLE IF NOT EXISTS assignment_discrepancy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    class_id UUID NOT NULL REFERENCES school_class(id) ON DELETE CASCADE,
    subject_id UUID REFERENCES subject(id) ON DELETE CASCADE,
    legacy_source VARCHAR(40) NOT NULL,
    legacy_teacher_id UUID REFERENCES employee(id),
    canonical_teacher_id UUID REFERENCES employee(id),
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','RESOLVED','IGNORED')),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    resolved_by UUID REFERENCES app_user(id)
);
CREATE INDEX IF NOT EXISTS idx_assignment_discrepancy_open
    ON assignment_discrepancy(school_id, academic_session_id, status, class_id);

ALTER TABLE timetable_slot ADD COLUMN IF NOT EXISTS assignment_id UUID;
ALTER TABLE timetable_slot ADD COLUMN IF NOT EXISTS assignment_version BIGINT;
ALTER TABLE timetable_slot ADD COLUMN IF NOT EXISTS published_teacher_id UUID REFERENCES employee(id);
ALTER TABLE timetable_slot ADD COLUMN IF NOT EXISTS published_assignment_id UUID;
ALTER TABLE timetable_slot ADD COLUMN IF NOT EXISTS published_assignment_version BIGINT;
CREATE INDEX IF NOT EXISTS idx_timetable_slot_assignment
    ON timetable_slot(school_id, academic_session_id, assignment_id);

-- A primary class uses exactly one dated homeroom assignment.  Existing V44
-- configuration is imported as an initial canonical assignment where present.
INSERT INTO class_teacher_assignment
    (school_id, academic_session_id, class_id, employee_id, role, effective_from, status, source)
SELECT x.school_id, x.academic_session_id, x.class_id, x.homeroom_teacher_id,
       'HOMEROOM', s.start_date, 'ACTIVE', 'V44_TIMETABLE_CONFIG'
  FROM timetable_class_config x
  JOIN academic_session s ON s.id = x.academic_session_id
 WHERE x.homeroom_teacher_id IS NOT NULL
ON CONFLICT DO NOTHING;

-- Record disagreements rather than guessing when a legacy class-subject
-- source contains more than one active responsible row.  The resolver will
-- expose these as an explicit blocker until a user repairs them.
INSERT INTO assignment_discrepancy
    (school_id, academic_session_id, class_id, subject_id, legacy_source,
     legacy_teacher_id, details)
SELECT a.school_id, a.academic_session_id, a.class_id, a.subject_id,
       'ACADEMIC_CLASS_SUBJECT_TEACHER', min(a.employee_id::text)::uuid,
       jsonb_build_object('activeResponsibleCount', count(*))
  FROM academic_class_subject_teacher a
 WHERE a.active AND a.role = 'RESPONSIBLE'
 GROUP BY a.school_id, a.academic_session_id, a.class_id, a.subject_id
HAVING count(*) > 1;

-- Link existing slots to the assignment version used for publication.  Draft
-- slots are also linked so conflicts and edits use one canonical identity.
UPDATE timetable_slot t
   SET assignment_id = a.id,
       assignment_version = a.version,
       published_teacher_id = CASE WHEN x.status = 'PUBLISHED' THEN t.teacher_id END,
       published_assignment_id = CASE WHEN x.status = 'PUBLISHED' THEN a.id END,
       published_assignment_version = CASE WHEN x.status = 'PUBLISHED' THEN a.version END
  FROM timetable_class_config x, class_teacher_assignment a
 WHERE a.school_id = t.school_id AND a.academic_session_id = t.academic_session_id
   AND a.class_id = t.class_id AND a.employee_id = t.teacher_id
   AND a.role = 'HOMEROOM' AND a.status = 'ACTIVE'
   AND t.school_id = x.school_id AND t.academic_session_id = x.academic_session_id
   AND t.class_id = x.class_id AND t.assignment_id IS NULL;

UPDATE timetable_slot t
   SET assignment_id = a.id,
       assignment_version = a.version,
       published_teacher_id = CASE WHEN x.status = 'PUBLISHED' THEN t.teacher_id END,
       published_assignment_id = CASE WHEN x.status = 'PUBLISHED' THEN a.id END,
       published_assignment_version = CASE WHEN x.status = 'PUBLISHED' THEN a.version END
  FROM timetable_class_config x, subject s, academic_class_subject_teacher a
 WHERE s.school_id = t.school_id AND upper(s.code) = upper(t.subject_code)
   AND a.school_id = t.school_id AND a.academic_session_id = t.academic_session_id
   AND a.class_id = t.class_id AND a.subject_id = s.id AND a.employee_id = t.teacher_id
   AND a.role = 'RESPONSIBLE' AND a.active
   AND t.school_id = x.school_id AND t.academic_session_id = x.academic_session_id
   AND t.class_id = x.class_id AND t.assignment_id IS NULL;

ALTER TABLE academic_session ADD COLUMN IF NOT EXISTS timezone VARCHAR(64) NOT NULL DEFAULT 'Africa/Douala';
ALTER TABLE academic_session ADD COLUMN IF NOT EXISTS teacher_submission_opens_at TIMESTAMPTZ;
ALTER TABLE academic_session ADD COLUMN IF NOT EXISTS teacher_submission_closes_at TIMESTAMPTZ;
ALTER TABLE academic_term ADD COLUMN IF NOT EXISTS timezone VARCHAR(64) NOT NULL DEFAULT 'Africa/Douala';
ALTER TABLE academic_term ADD COLUMN IF NOT EXISTS teacher_submission_opens_at TIMESTAMPTZ;
ALTER TABLE academic_term ADD COLUMN IF NOT EXISTS teacher_submission_closes_at TIMESTAMPTZ;
ALTER TABLE academic_reporting_period ADD COLUMN IF NOT EXISTS teacher_submission_opens_at TIMESTAMPTZ;
ALTER TABLE academic_reporting_period ADD COLUMN IF NOT EXISTS teacher_submission_closes_at TIMESTAMPTZ;
ALTER TABLE academic_reporting_period ADD COLUMN IF NOT EXISTS timezone VARCHAR(64) NOT NULL DEFAULT 'Africa/Douala';
ALTER TABLE academic_reporting_period ADD COLUMN IF NOT EXISTS structure_fingerprint VARCHAR(128);

CREATE TABLE IF NOT EXISTS academic_reporting_period_dependency (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    parent_period_id UUID NOT NULL REFERENCES academic_reporting_period(id) ON DELETE CASCADE,
    child_period_id UUID NOT NULL REFERENCES academic_reporting_period(id) ON DELETE CASCADE,
    weight NUMERIC(8,5) NOT NULL DEFAULT 1 CHECK (weight > 0),
    optional BOOLEAN NOT NULL DEFAULT false,
    display_order INT NOT NULL DEFAULT 1,
    UNIQUE (school_id, parent_period_id, child_period_id),
    CHECK (parent_period_id <> child_period_id)
);
CREATE INDEX IF NOT EXISTS idx_reporting_period_dependency_parent
    ON academic_reporting_period_dependency(school_id, parent_period_id, display_order);

-- Fixed result graph: sequence -> trimester result -> annual.  Optional COMP
-- rows are accepted automatically when an installation adds those periods.
INSERT INTO academic_reporting_period_dependency
    (school_id, academic_session_id, parent_period_id, child_period_id, weight, optional, display_order)
SELECT p.school_id, p.academic_session_id, p.id, c.id, 0.5, false, c.display_order
  FROM academic_reporting_period p
  JOIN academic_reporting_period c ON c.school_id=p.school_id
   AND c.academic_session_id=p.academic_session_id
   AND c.code IN ('S1','S2','S3','S4','S5','S6')
 WHERE p.code IN ('T1_RESULT','T2_RESULT','T3_RESULT')
   AND c.code = CASE p.code WHEN 'T1_RESULT' THEN 'S1' WHEN 'T2_RESULT' THEN 'S3' WHEN 'T3_RESULT' THEN 'S5' END
ON CONFLICT DO NOTHING;
INSERT INTO academic_reporting_period_dependency
    (school_id, academic_session_id, parent_period_id, child_period_id, weight, optional, display_order)
SELECT p.school_id, p.academic_session_id, p.id, c.id, 0.5, false, c.display_order + 1
  FROM academic_reporting_period p
  JOIN academic_reporting_period c ON c.school_id=p.school_id
   AND c.academic_session_id=p.academic_session_id
   AND c.code = CASE p.code WHEN 'T1_RESULT' THEN 'S2' WHEN 'T2_RESULT' THEN 'S4' WHEN 'T3_RESULT' THEN 'S6' END
 WHERE p.code IN ('T1_RESULT','T2_RESULT','T3_RESULT')
ON CONFLICT DO NOTHING;
INSERT INTO academic_reporting_period_dependency
    (school_id, academic_session_id, parent_period_id, child_period_id, weight, optional, display_order)
SELECT annual.school_id, annual.academic_session_id, annual.id, term.id, 1.0 / 3.0, false, term.display_order
  FROM academic_reporting_period annual
  JOIN academic_reporting_period term ON term.school_id=annual.school_id
   AND term.academic_session_id=annual.academic_session_id
   AND term.code IN ('T1_RESULT','T2_RESULT','T3_RESULT')
 WHERE annual.code='ANNUAL'
ON CONFLICT DO NOTHING;

-- A promotion decision is prepared for a future enrollment.  Existing
-- ACTIVE/COMPLETED semantics remain intact for historical records.
ALTER TABLE student_enrollment DROP CONSTRAINT IF EXISTS student_enrollment_status_check;
ALTER TABLE student_enrollment DROP CONSTRAINT IF EXISTS student_enrollment_status_check1;
ALTER TABLE student_enrollment ADD CONSTRAINT student_enrollment_status_check
    CHECK (status IN ('PLANNED','ACTIVE','TRANSFERRED','WITHDRAWN','COMPLETED'));
ALTER TABLE student_enrollment ADD COLUMN IF NOT EXISTS planned_on DATE;
ALTER TABLE student_enrollment ADD COLUMN IF NOT EXISTS activation_reason VARCHAR(500);
ALTER TABLE student_enrollment ADD COLUMN IF NOT EXISTS promotion_decision_id UUID REFERENCES promotion_decision(id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_student_enrollment_promotion_decision
    ON student_enrollment(promotion_decision_id) WHERE promotion_decision_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS promotion_transition_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    source_enrollment_id UUID REFERENCES student_enrollment(id),
    target_enrollment_id UUID REFERENCES student_enrollment(id),
    promotion_batch_id UUID REFERENCES promotion_batch(id),
    action VARCHAR(24) NOT NULL CHECK (action IN ('PLANNED','ACTIVATED','SOURCE_COMPLETED','CANCELLED')),
    reason VARCHAR(500),
    actor_user_id UUID REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_promotion_transition_student
    ON promotion_transition_event(school_id, student_id, created_at);

-- Explicit workflow dependencies are seeded for every existing session.  A
-- later migration/apply operation may add COMP as an optional child.
UPDATE academic_reporting_period p
   SET structure_fingerprint = encode(digest(
       p.academic_session_id::text || ':' || p.code || ':' || p.start_date::text || ':' || p.end_date::text,
       'sha256'), 'hex')
 WHERE p.structure_fingerprint IS NULL;
