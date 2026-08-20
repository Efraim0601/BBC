-- BAY-10/BAY-33: session-versioned curriculum and responsible teachers.
CREATE TABLE academic_subject_group (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    code VARCHAR(40) NOT NULL,
    label JSONB NOT NULL DEFAULT '{}'::jsonb,
    display_order INT NOT NULL DEFAULT 1,
    show_subtotal BOOLEAN NOT NULL DEFAULT true,
    show_rank BOOLEAN NOT NULL DEFAULT false,
    average_policy VARCHAR(32) NOT NULL DEFAULT 'WEIGHTED_COEFFICIENT',
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (school_id, academic_session_id, code),
    UNIQUE (school_id, academic_session_id, display_order)
);

CREATE TABLE academic_curriculum_subject (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    class_id UUID NOT NULL REFERENCES school_class(id) ON DELETE CASCADE,
    subject_id UUID NOT NULL REFERENCES subject(id) ON DELETE CASCADE,
    group_id UUID REFERENCES academic_subject_group(id) ON DELETE SET NULL,
    display_order INT NOT NULL DEFAULT 1,
    coefficient INT NOT NULL DEFAULT 1 CHECK (coefficient > 0),
    max_score NUMERIC(6,2) NOT NULL DEFAULT 20 CHECK (max_score > 0),
    mandatory BOOLEAN NOT NULL DEFAULT true,
    pass_threshold NUMERIC(6,2) NOT NULL DEFAULT 10 CHECK (pass_threshold >= 0),
    show_subject_rank BOOLEAN NOT NULL DEFAULT true,
    remark_required BOOLEAN NOT NULL DEFAULT false,
    active_from DATE,
    active_to DATE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, academic_session_id, class_id, subject_id),
    CHECK (active_to IS NULL OR active_from IS NULL OR active_from <= active_to)
);
CREATE INDEX idx_academic_curriculum_class ON academic_curriculum_subject(school_id, academic_session_id, class_id, display_order);

CREATE TABLE academic_class_subject_teacher (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    class_id UUID NOT NULL REFERENCES school_class(id) ON DELETE CASCADE,
    subject_id UUID NOT NULL REFERENCES subject(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES employee(id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL CHECK (role IN ('RESPONSIBLE','ASSISTANT','HOMEROOM')),
    effective_from DATE,
    effective_to DATE,
    source VARCHAR(16) NOT NULL DEFAULT 'MANUAL' CHECK (source IN ('TIMETABLE','HOMEROOM','MANUAL')),
    active BOOLEAN NOT NULL DEFAULT true,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, academic_session_id, class_id, subject_id, employee_id, role)
);
CREATE INDEX idx_academic_teacher_assignment ON academic_class_subject_teacher(school_id, academic_session_id, class_id, subject_id, active);

-- Compatibility backfill: the pre-existing class coefficient table is the
-- source for the current session until an administrator edits the new layer.
INSERT INTO academic_curriculum_subject (school_id, academic_session_id, class_id, subject_id, display_order, coefficient, max_score, mandatory, pass_threshold)
SELECT scc.school_id, ses.id, scc.class_id, scc.subject_id,
       row_number() OVER (PARTITION BY ses.id, scc.class_id ORDER BY s.code),
       scc.coef, 20, true, 10
  FROM subject_class_coef scc
  JOIN academic_session ses ON ses.school_id=scc.school_id AND ses.is_current
  JOIN subject s ON s.id=scc.subject_id
ON CONFLICT (school_id, academic_session_id, class_id, subject_id) DO NOTHING;

-- Existing timetable assignments are the initial responsible-teacher source.
INSERT INTO academic_class_subject_teacher (school_id, academic_session_id, class_id, subject_id, employee_id, role, source)
SELECT c.school_id, ses.id, tc.class_id, ts.subject_id, tc.employee_id, 'RESPONSIBLE', 'TIMETABLE'
  FROM teacher_class tc
  JOIN school_class c ON c.id=tc.class_id
  JOIN academic_session ses ON ses.school_id=c.school_id AND ses.is_current
  JOIN teacher_subject ts ON ts.employee_id=tc.employee_id
ON CONFLICT DO NOTHING;

