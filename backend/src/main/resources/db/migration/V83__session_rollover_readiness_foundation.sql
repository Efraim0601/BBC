-- Session rollover and grade-entry readiness foundation.
--
-- V78-V82 are demo seed migrations and intentionally keep their historical
-- numbering.  Production schema changes therefore continue at V83.

CREATE TABLE IF NOT EXISTS academic_workflow_window_rule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    scope_type VARCHAR(16) NOT NULL CHECK (scope_type IN ('SESSION','TERM','PERIOD')),
    academic_term_id UUID REFERENCES academic_term(id) ON DELETE CASCADE,
    reporting_period_id UUID REFERENCES academic_reporting_period(id) ON DELETE CASCADE,
    action VARCHAR(32) NOT NULL CHECK (action IN ('GRADE_ENTRY','TEACHER_SUBMISSION','REVIEW','VALIDATION','PUBLICATION','CORRECTION')),
    mode VARCHAR(16) NOT NULL CHECK (mode IN ('INHERIT','UNRESTRICTED','LIMITED')),
    opens_at TIMESTAMPTZ,
    closes_at TIMESTAMPTZ,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Africa/Douala',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((scope_type='SESSION' AND academic_term_id IS NULL AND reporting_period_id IS NULL)
        OR (scope_type='TERM' AND academic_term_id IS NOT NULL AND reporting_period_id IS NULL)
        OR (scope_type='PERIOD' AND reporting_period_id IS NOT NULL)),
    CHECK ((mode IN ('INHERIT','UNRESTRICTED') AND opens_at IS NULL AND closes_at IS NULL)
        OR (mode='LIMITED' AND (opens_at IS NOT NULL OR closes_at IS NOT NULL)
            AND (opens_at IS NULL OR closes_at IS NULL OR closes_at > opens_at)))
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_workflow_window_session
    ON academic_workflow_window_rule(school_id, academic_session_id, action)
    WHERE scope_type='SESSION';
CREATE UNIQUE INDEX IF NOT EXISTS ux_workflow_window_term
    ON academic_workflow_window_rule(school_id, academic_session_id, academic_term_id, action)
    WHERE scope_type='TERM';
CREATE UNIQUE INDEX IF NOT EXISTS ux_workflow_window_period
    ON academic_workflow_window_rule(school_id, academic_session_id, reporting_period_id, action)
    WHERE scope_type='PERIOD';
CREATE INDEX IF NOT EXISTS idx_workflow_window_scope
    ON academic_workflow_window_rule(school_id, academic_session_id, scope_type, action);

-- Every scope/action gets a row.  This makes a blank session rule an explicit
-- UNRESTRICTED policy while blank term/period rules remain INHERIT policies.
DO $$
DECLARE
    s RECORD;
    t RECORD;
    p RECORD;
    a TEXT;
    open_at TIMESTAMPTZ;
    close_at TIMESTAMPTZ;
    session_mode TEXT;
    term_mode TEXT;
    period_mode TEXT;
BEGIN
    FOR s IN SELECT * FROM academic_session LOOP
        FOREACH a IN ARRAY ARRAY['GRADE_ENTRY','TEACHER_SUBMISSION','REVIEW','VALIDATION','PUBLICATION','CORRECTION'] LOOP
            open_at := CASE a
                WHEN 'GRADE_ENTRY' THEN s.grade_entry_opens_at
                WHEN 'TEACHER_SUBMISSION' THEN s.teacher_submission_opens_at
                WHEN 'PUBLICATION' THEN s.bulletin_publish_opens_at
                WHEN 'VALIDATION' THEN s.bulletin_publish_opens_at
                ELSE NULL END;
            close_at := CASE a
                WHEN 'GRADE_ENTRY' THEN s.grade_entry_closes_at
                WHEN 'TEACHER_SUBMISSION' THEN s.teacher_submission_closes_at
                WHEN 'PUBLICATION' THEN s.bulletin_publish_closes_at
                WHEN 'VALIDATION' THEN s.bulletin_publish_closes_at
                ELSE NULL END;
            session_mode := CASE WHEN open_at IS NULL AND close_at IS NULL THEN 'UNRESTRICTED' ELSE 'LIMITED' END;
            INSERT INTO academic_workflow_window_rule
                (school_id, academic_session_id, scope_type, action, mode, opens_at, closes_at, timezone)
            VALUES (s.school_id, s.id, 'SESSION', a, session_mode, open_at, close_at,
                    COALESCE(s.timezone, 'Africa/Douala'))
            ON CONFLICT DO NOTHING;
        END LOOP;
    END LOOP;

    FOR t IN SELECT * FROM academic_term LOOP
        FOREACH a IN ARRAY ARRAY['GRADE_ENTRY','TEACHER_SUBMISSION','REVIEW','VALIDATION','PUBLICATION','CORRECTION'] LOOP
            open_at := CASE a
                WHEN 'GRADE_ENTRY' THEN t.grade_entry_opens_at
                WHEN 'TEACHER_SUBMISSION' THEN t.teacher_submission_opens_at
                WHEN 'PUBLICATION' THEN t.bulletin_publish_opens_at
                WHEN 'VALIDATION' THEN t.bulletin_publish_opens_at
                ELSE NULL END;
            close_at := CASE a
                WHEN 'GRADE_ENTRY' THEN t.grade_entry_closes_at
                WHEN 'TEACHER_SUBMISSION' THEN t.teacher_submission_closes_at
                WHEN 'PUBLICATION' THEN t.bulletin_publish_closes_at
                WHEN 'VALIDATION' THEN t.bulletin_publish_closes_at
                ELSE NULL END;
            term_mode := CASE WHEN open_at IS NULL AND close_at IS NULL THEN 'INHERIT' ELSE 'LIMITED' END;
            INSERT INTO academic_workflow_window_rule
                (school_id, academic_session_id, scope_type, academic_term_id, action, mode, opens_at, closes_at, timezone)
            VALUES (t.school_id, t.academic_session_id, 'TERM', t.id, a, term_mode, open_at, close_at,
                    COALESCE(t.timezone, 'Africa/Douala'))
            ON CONFLICT DO NOTHING;
        END LOOP;
    END LOOP;

    FOR p IN SELECT * FROM academic_reporting_period LOOP
        FOREACH a IN ARRAY ARRAY['GRADE_ENTRY','TEACHER_SUBMISSION','REVIEW','VALIDATION','PUBLICATION','CORRECTION'] LOOP
            open_at := CASE a
                WHEN 'GRADE_ENTRY' THEN p.grade_entry_opens_at
                WHEN 'TEACHER_SUBMISSION' THEN p.teacher_submission_opens_at
                WHEN 'REVIEW' THEN p.review_opens_at
                WHEN 'VALIDATION' THEN p.validation_opens_at
                WHEN 'PUBLICATION' THEN p.bulletin_publish_opens_at
                WHEN 'CORRECTION' THEN p.correction_opens_at END;
            close_at := CASE a
                WHEN 'GRADE_ENTRY' THEN p.grade_entry_closes_at
                WHEN 'TEACHER_SUBMISSION' THEN p.teacher_submission_closes_at
                WHEN 'REVIEW' THEN p.review_closes_at
                WHEN 'VALIDATION' THEN p.validation_closes_at
                WHEN 'PUBLICATION' THEN p.bulletin_publish_closes_at
                WHEN 'CORRECTION' THEN p.correction_closes_at END;
            period_mode := CASE WHEN open_at IS NULL AND close_at IS NULL THEN 'INHERIT' ELSE 'LIMITED' END;
            INSERT INTO academic_workflow_window_rule
                (school_id, academic_session_id, scope_type, academic_term_id, reporting_period_id,
                 action, mode, opens_at, closes_at, timezone)
            VALUES (p.school_id, p.academic_session_id, 'PERIOD', p.academic_term_id, p.id,
                    a, period_mode, open_at, close_at, COALESCE(p.timezone, 'Africa/Douala'))
            ON CONFLICT DO NOTHING;
        END LOOP;
    END LOOP;
END $$;

ALTER TABLE academic_grade_packet
    ADD COLUMN IF NOT EXISTS responsible_assignment_id UUID,
    ADD COLUMN IF NOT EXISTS responsible_assignment_version BIGINT,
    ADD COLUMN IF NOT EXISTS last_saved_by UUID REFERENCES app_user(id),
    ADD COLUMN IF NOT EXISTS last_saved_at TIMESTAMPTZ;
UPDATE academic_grade_packet SET last_saved_at=COALESCE(last_saved_at, updated_at)
 WHERE last_saved_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_grade_packet_assignment_provenance
    ON academic_grade_packet(school_id, responsible_assignment_id, responsible_assignment_version);

CREATE TABLE IF NOT EXISTS academic_copy_run (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    copy_type VARCHAR(32) NOT NULL CHECK (copy_type IN ('SESSION_CONFIGURATION','CURRICULUM')),
    source_session_id UUID NOT NULL REFERENCES academic_session(id),
    target_session_id UUID NOT NULL REFERENCES academic_session(id),
    scope_key VARCHAR(120),
    preview_fingerprint VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(120),
    status VARCHAR(24) NOT NULL CHECK (status IN ('PREVIEWED','APPLIED','STALE','REJECTED')),
    created_count INT NOT NULL DEFAULT 0,
    updated_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    result_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    actor_user_id UUID REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    applied_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_academic_copy_run_target
    ON academic_copy_run(school_id, copy_type, target_session_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS ux_academic_copy_run_idempotency
    ON academic_copy_run(school_id, copy_type, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
