-- Audited, time-bounded emergency window overrides.  Overrides never rewrite
-- the configured structure; they only provide a separately visible exception.
CREATE TABLE IF NOT EXISTS academic_window_override (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    reporting_period_id UUID REFERENCES academic_reporting_period(id) ON DELETE CASCADE,
    action VARCHAR(32) NOT NULL,
    scope VARCHAR(500) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    opens_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_by UUID REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT academic_window_override_action_check CHECK (action IN ('GRADE_ENTRY','TEACHER_SUBMISSION','REVIEW','VALIDATION','PUBLICATION','CORRECTION')),
    CONSTRAINT academic_window_override_dates_check CHECK (expires_at > opens_at),
    CONSTRAINT academic_window_override_reason_check CHECK (length(btrim(reason)) > 0),
    CONSTRAINT academic_window_override_scope_check CHECK (length(btrim(scope)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_academic_window_override_effective
    ON academic_window_override(school_id, academic_session_id, reporting_period_id, action, opens_at, expires_at);

INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT pg.school_id, pg.role_code, 'ACADEMIC_WINDOW_OVERRIDE', true
FROM permission_grant pg
WHERE pg.module='settings' AND pg.level='write'
ON CONFLICT (school_id, role_code, action_code) DO NOTHING;
