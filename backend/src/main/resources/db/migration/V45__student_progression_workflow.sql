-- BAY-11: configurable progression paths, explainable promotion decisions,
-- manual overrides, and transactional promotion batches.

CREATE TABLE class_progression_path (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    source_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    source_class_id UUID NOT NULL REFERENCES school_class(id),
    target_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    target_class_id UUID REFERENCES school_class(id),
    terminal BOOLEAN NOT NULL DEFAULT false,
    active BOOLEAN NOT NULL DEFAULT true,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_progression_destination CHECK (terminal OR target_class_id IS NOT NULL),
    CONSTRAINT chk_progression_sessions CHECK (source_session_id <> target_session_id),
    UNIQUE (school_id, source_session_id, source_class_id, target_session_id)
);
CREATE INDEX idx_progression_path_lookup
    ON class_progression_path(school_id, source_session_id, target_session_id, source_class_id);

CREATE TABLE promotion_rule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    subsystem VARCHAR(8),
    level VARCHAR(20),
    promote_min NUMERIC(4,2) NOT NULL DEFAULT 10.00,
    review_min NUMERIC(4,2) NOT NULL DEFAULT 8.00,
    require_final_average BOOLEAN NOT NULL DEFAULT true,
    active BOOLEAN NOT NULL DEFAULT true,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_promotion_thresholds CHECK (
        review_min >= 0 AND promote_min <= 20 AND review_min <= promote_min
    ),
    UNIQUE NULLS NOT DISTINCT (school_id, academic_session_id, subsystem, level)
);

CREATE TABLE promotion_batch (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    source_session_id UUID NOT NULL REFERENCES academic_session(id),
    target_session_id UUID NOT NULL REFERENCES academic_session(id),
    name VARCHAR(140) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','COMMITTED','CANCELLED')),
    idempotency_key VARCHAR(120),
    created_by UUID REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    committed_by UUID REFERENCES app_user(id),
    committed_at TIMESTAMPTZ,
    commit_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_promotion_batch_sessions CHECK (source_session_id <> target_session_id),
    UNIQUE (school_id, idempotency_key)
);

CREATE TABLE promotion_decision (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    batch_id UUID NOT NULL REFERENCES promotion_batch(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES student(id),
    source_enrollment_id UUID NOT NULL REFERENCES student_enrollment(id),
    source_class_id UUID NOT NULL REFERENCES school_class(id),
    mapped_target_class_id UUID REFERENCES school_class(id),
    target_class_id UUID REFERENCES school_class(id),
    final_average NUMERIC(4,2),
    recommendation VARCHAR(16) NOT NULL
        CHECK (recommendation IN ('PROMOTE','REPEAT','REVIEW','GRADUATE')),
    final_decision VARCHAR(16) NOT NULL
        CHECK (final_decision IN ('PROMOTE','REPEAT','REVIEW','GRADUATE','HOLD')),
    override_reason VARCHAR(500),
    evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
    decided_by UUID REFERENCES app_user(id),
    decided_at TIMESTAMPTZ,
    committed_enrollment_id UUID REFERENCES student_enrollment(id),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (school_id, batch_id, student_id)
);
CREATE INDEX idx_promotion_decision_batch
    ON promotion_decision(school_id, batch_id, source_class_id, student_id);

ALTER TABLE journey_entry
    ADD COLUMN source_session_id UUID REFERENCES academic_session(id),
    ADD COLUMN target_session_id UUID REFERENCES academic_session(id),
    ADD COLUMN promotion_batch_id UUID REFERENCES promotion_batch(id),
    ADD COLUMN recommendation VARCHAR(16),
    ADD COLUMN final_decision VARCHAR(16),
    ADD COLUMN target_class_name VARCHAR(64),
    ADD COLUMN override_reason VARCHAR(500),
    ADD COLUMN decision_by UUID REFERENCES app_user(id),
    ADD COLUMN decision_at TIMESTAMPTZ;

INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT pg.school_id, pg.role_code, action.code, true
FROM permission_grant pg
CROSS JOIN (VALUES
    ('PROGRESSION_VIEW'),
    ('PROGRESSION_CONFIGURE'),
    ('PROMOTION_REVIEW'),
    ('PROMOTION_COMMIT')
) AS action(code)
WHERE pg.module = 'journey'
  AND (action.code = 'PROGRESSION_VIEW' OR pg.level = 'write')
ON CONFLICT (school_id, role_code, action_code) DO NOTHING;
