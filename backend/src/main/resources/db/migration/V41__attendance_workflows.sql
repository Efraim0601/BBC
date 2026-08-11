-- BAY-9: policy-driven daily/period roll call, audited corrections and analytics.

CREATE TABLE attendance_policy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    level VARCHAR(20) NOT NULL CHECK (level IN ('maternelle','primary','secondary')),
    model VARCHAR(16) NOT NULL CHECK (model IN ('DAILY','PERIOD')),
    late_after_minutes INT NOT NULL DEFAULT 0 CHECK (late_after_minutes >= 0),
    chronic_absence_percent NUMERIC(5,2) NOT NULL DEFAULT 20.00
        CHECK (chronic_absence_percent BETWEEN 0 AND 100),
    require_absence_reason BOOLEAN NOT NULL DEFAULT false,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, level)
);

INSERT INTO attendance_policy (school_id, level, model)
SELECT id, p.level, p.model
FROM school
CROSS JOIN (VALUES
    ('maternelle','DAILY'), ('primary','DAILY'), ('secondary','PERIOD')
) AS p(level, model)
ON CONFLICT DO NOTHING;

CREATE TABLE attendance_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id),
    expected_session_id UUID REFERENCES expected_school_session(id),
    school_class_id UUID NOT NULL REFERENCES school_class(id),
    session_date DATE NOT NULL,
    model VARCHAR(16) NOT NULL CHECK (model IN ('DAILY','PERIOD')),
    period_key VARCHAR(64) NOT NULL,
    subject_code VARCHAR(32),
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','FINALIZED','REOPENED')),
    version BIGINT NOT NULL DEFAULT 0,
    finalized_at TIMESTAMPTZ,
    finalized_by UUID REFERENCES app_user(id),
    reopened_at TIMESTAMPTZ,
    reopened_by UUID REFERENCES app_user(id),
    reopen_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, academic_session_id, school_class_id, session_date, period_key)
);
CREATE INDEX idx_attendance_session_scope
    ON attendance_session(school_id, session_date, school_class_id, status);

CREATE TABLE attendance_mark (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    attendance_session_id UUID NOT NULL REFERENCES attendance_session(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL DEFAULT 'UNMARKED'
        CHECK (status IN ('UNMARKED','PRESENT','ABSENT','LATE','EXCUSED')),
    reason VARCHAR(240),
    note VARCHAR(500),
    late_minutes INT NOT NULL DEFAULT 0 CHECK (late_minutes >= 0),
    source VARCHAR(24) NOT NULL DEFAULT 'ROSTER',
    device_record_id UUID REFERENCES attendance_record(id),
    marked_at TIMESTAMPTZ,
    marked_by UUID REFERENCES app_user(id),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (school_id, attendance_session_id, student_id)
);
CREATE INDEX idx_attendance_mark_student
    ON attendance_mark(school_id, student_id, attendance_session_id, status);

CREATE TABLE attendance_session_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    attendance_session_id UUID NOT NULL REFERENCES attendance_session(id) ON DELETE CASCADE,
    actor_user_id UUID REFERENCES app_user(id),
    actor_username VARCHAR(64),
    action VARCHAR(32) NOT NULL,
    reason VARCHAR(500),
    details JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_attendance_session_event
    ON attendance_session_event(school_id, attendance_session_id, occurred_at DESC);

INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT pg.school_id, pg.role_code, a.action_code,
       CASE WHEN a.required_level = 'read'
            THEN pg.level IN ('read','write') ELSE pg.level = 'write' END
FROM permission_grant pg
JOIN (VALUES
    ('presence','read','ATTENDANCE_ROSTER_VIEW'),
    ('presence','write','ATTENDANCE_MARK'),
    ('presence','write','ATTENDANCE_FINALIZE'),
    ('presence','write','ATTENDANCE_REOPEN'),
    ('presence','read','ATTENDANCE_ANALYTICS_VIEW'),
    ('presence','write','ATTENDANCE_POLICY_MANAGE'),
    ('presence','write','ATTENDANCE_RECONCILE')
) AS a(module, required_level, action_code) ON a.module = pg.module
ON CONFLICT DO NOTHING;
