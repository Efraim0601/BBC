-- BAY-7: shared foundation for sessions, enrollment history, calendars,
-- immutable audit/idempotency, generated documents, and action permissions.

CREATE TABLE academic_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    code VARCHAR(32) NOT NULL,
    label VARCHAR(64) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','OPEN','CLOSED','ARCHIVED')),
    is_current BOOLEAN NOT NULL DEFAULT false,
    grade_entry_opens_at TIMESTAMPTZ,
    grade_entry_closes_at TIMESTAMPTZ,
    bulletin_publish_opens_at TIMESTAMPTZ,
    bulletin_publish_closes_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_academic_session_dates CHECK (start_date <= end_date),
    UNIQUE (school_id, code)
);
CREATE UNIQUE INDEX uq_academic_session_current
    ON academic_session(school_id) WHERE is_current;
CREATE INDEX idx_academic_session_school_dates
    ON academic_session(school_id, start_date, end_date);

CREATE TABLE academic_term (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    code VARCHAR(32) NOT NULL,
    label VARCHAR(80) NOT NULL,
    sequence_no INT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    grade_entry_opens_at TIMESTAMPTZ,
    grade_entry_closes_at TIMESTAMPTZ,
    bulletin_publish_opens_at TIMESTAMPTZ,
    bulletin_publish_closes_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_academic_term_dates CHECK (start_date <= end_date),
    UNIQUE (school_id, academic_session_id, code),
    UNIQUE (school_id, academic_session_id, sequence_no)
);
CREATE INDEX idx_academic_term_session
    ON academic_term(school_id, academic_session_id, sequence_no);

-- Backfill the managed session model from the legacy academic_year table.
INSERT INTO academic_session (
    id, school_id, code, label, start_date, end_date, status, is_current
)
SELECT ay.id, ay.school_id, ay.label, ay.label,
       make_date(ay.start_year, 9, 1), make_date(ay.start_year + 1, 7, 31),
       CASE WHEN ay.is_current THEN 'OPEN' ELSE 'CLOSED' END, ay.is_current
FROM academic_year ay
ON CONFLICT (school_id, code) DO NOTHING;

CREATE TABLE student_enrollment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id),
    school_class_id UUID REFERENCES school_class(id),
    class_name_snapshot VARCHAR(64),
    level_snapshot VARCHAR(20),
    subsystem_snapshot VARCHAR(8),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE','TRANSFERRED','WITHDRAWN','COMPLETED')),
    enrolled_on DATE NOT NULL,
    exited_on DATE,
    source VARCHAR(24) NOT NULL DEFAULT 'MIGRATION',
    reason VARCHAR(500),
    previous_enrollment_id UUID REFERENCES student_enrollment(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_student_enrollment_dates CHECK (exited_on IS NULL OR exited_on >= enrolled_on)
);
CREATE UNIQUE INDEX uq_student_enrollment_active_session
    ON student_enrollment(school_id, student_id, academic_session_id)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_student_enrollment_roster
    ON student_enrollment(school_id, academic_session_id, school_class_id, status);
CREATE INDEX idx_student_enrollment_history
    ON student_enrollment(school_id, student_id, academic_session_id, enrolled_on);

INSERT INTO student_enrollment (
    school_id, student_id, academic_session_id, school_class_id,
    class_name_snapshot, level_snapshot, subsystem_snapshot,
    status, enrolled_on, source
)
SELECT st.school_id, st.id, s.id, st.class_id,
       st.class_name, st.level, st.subsystem,
       'ACTIVE', GREATEST(s.start_date, st.created_at::date), 'MIGRATION'
FROM student st
JOIN academic_session s ON s.school_id = st.school_id AND s.is_current
WHERE st.active
ON CONFLICT DO NOTHING;

CREATE TABLE school_calendar_day (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    day_of_week INT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    teaching_day BOOLEAN NOT NULL DEFAULT true,
    start_time TIME,
    end_time TIME,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (school_id, academic_session_id, day_of_week)
);

INSERT INTO school_calendar_day (
    school_id, academic_session_id, day_of_week, teaching_day, start_time, end_time
)
SELECT s.school_id, s.id, d,
       d BETWEEN 1 AND 5,
       sch.school_start_time::time, sch.school_end_time::time
FROM academic_session s
JOIN school sch ON sch.id = s.school_id
CROSS JOIN generate_series(1, 7) d
ON CONFLICT DO NOTHING;

CREATE TABLE expected_school_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    school_class_id UUID NOT NULL REFERENCES school_class(id) ON DELETE CASCADE,
    session_date DATE NOT NULL,
    model VARCHAR(16) NOT NULL DEFAULT 'DAILY'
        CHECK (model IN ('DAILY','PERIOD')),
    period_key VARCHAR(64) NOT NULL DEFAULT 'DAILY',
    source VARCHAR(24) NOT NULL DEFAULT 'CALENDAR',
    source_version VARCHAR(64) NOT NULL,
    cancelled BOOLEAN NOT NULL DEFAULT false,
    closure_reason VARCHAR(240),
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, academic_session_id, school_class_id, session_date, period_key)
);
CREATE INDEX idx_expected_school_session_range
    ON expected_school_session(school_id, academic_session_id, session_date, school_class_id);

CREATE TABLE audit_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    actor_user_id UUID REFERENCES app_user(id),
    actor_username VARCHAR(64),
    action VARCHAR(120) NOT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id VARCHAR(80),
    before_data JSONB,
    after_data JSONB,
    reason VARCHAR(500),
    request_id VARCHAR(100),
    correlation_id VARCHAR(100),
    ip_address VARCHAR(64),
    user_agent VARCHAR(300),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_event_aggregate
    ON audit_event(school_id, aggregate_type, aggregate_id, occurred_at DESC);
CREATE INDEX idx_audit_event_actor
    ON audit_event(school_id, actor_user_id, occurred_at DESC);

CREATE OR REPLACE FUNCTION reject_audit_event_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_audit_event_immutable
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION reject_audit_event_mutation();

CREATE TABLE idempotency_key (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    endpoint VARCHAR(160) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_json JSONB NOT NULL,
    response_type VARCHAR(240),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    UNIQUE (school_id, endpoint, idempotency_key)
);
CREATE INDEX idx_idempotency_expiry ON idempotency_key(expires_at);

CREATE TABLE document_template (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    type VARCHAR(48) NOT NULL,
    locale VARCHAR(8) NOT NULL DEFAULT 'fr',
    name VARCHAR(120) NOT NULL,
    template_version INT NOT NULL DEFAULT 1,
    body_template TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, type, locale, template_version)
);

CREATE TABLE generated_document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    document_template_id UUID REFERENCES document_template(id),
    document_type VARCHAR(48) NOT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id VARCHAR(80) NOT NULL,
    aggregate_version VARCHAR(80) NOT NULL DEFAULT '1',
    locale VARCHAR(8) NOT NULL DEFAULT 'fr',
    document_number VARCHAR(80) NOT NULL,
    title VARCHAR(180) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    mime_type VARCHAR(80) NOT NULL DEFAULT 'application/pdf',
    size_bytes BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ISSUED'
        CHECK (status IN ('GENERATED','ISSUED','REVOKED','SUPERSEDED')),
    visibility VARCHAR(16) NOT NULL DEFAULT 'STAFF'
        CHECK (visibility IN ('STAFF','PARENT','STUDENT','PUBLIC')),
    generated_by UUID REFERENCES app_user(id),
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    issued_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revoked_by UUID REFERENCES app_user(id),
    revoke_reason VARCHAR(500),
    UNIQUE (school_id, document_number),
    UNIQUE (school_id, document_type, aggregate_type, aggregate_id, aggregate_version, locale)
);
CREATE INDEX idx_generated_document_aggregate
    ON generated_document(school_id, aggregate_type, aggregate_id, generated_at DESC);

CREATE TABLE permission_action_grant (
    id BIGSERIAL PRIMARY KEY,
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    role_code VARCHAR(32) NOT NULL REFERENCES role(code),
    action_code VARCHAR(80) NOT NULL,
    allowed BOOLEAN NOT NULL DEFAULT false,
    UNIQUE (school_id, role_code, action_code)
);

-- Fine-grained defaults inherit the existing module-level intent.
INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT pg.school_id, pg.role_code, a.action_code,
       CASE WHEN a.required_level = 'read'
            THEN pg.level IN ('read','write')
            ELSE pg.level = 'write' END
FROM permission_grant pg
JOIN (VALUES
    ('settings','write','SESSION_MANAGE'),
    ('settings','read','SESSION_VIEW'),
    ('students','write','ENROLLMENT_MANAGE'),
    ('students','read','ENROLLMENT_VIEW'),
    ('settings','write','CALENDAR_MANAGE'),
    ('settings','read','CALENDAR_VIEW'),
    ('documents','write','DOCUMENT_GENERATE'),
    ('documents','write','DOCUMENT_REVOKE'),
    ('documents','read','DOCUMENT_VIEW'),
    ('settings','read','AUDIT_VIEW')
) AS a(module, required_level, action_code) ON a.module = pg.module
ON CONFLICT DO NOTHING;

-- Built-in generic templates. Schools can version/replace them later.
INSERT INTO document_template (school_id, type, locale, name, body_template)
SELECT s.id, t.type, t.locale, t.name, t.body
FROM school s
CROSS JOIN (VALUES
    ('ENROLLMENT_CERTIFICATE','fr','Certificat de scolarité','Certifie que {{studentName}} ({{matricule}}) est inscrit(e) en {{className}} pour l''année {{sessionLabel}}.'),
    ('ENROLLMENT_CERTIFICATE','en','Enrollment certificate','This certifies that {{studentName}} ({{matricule}}) is enrolled in {{className}} for {{sessionLabel}}.'),
    ('GENERIC','fr','Document officiel','{{content}}'),
    ('GENERIC','en','Official document','{{content}}')
) AS t(type, locale, name, body)
ON CONFLICT DO NOTHING;
