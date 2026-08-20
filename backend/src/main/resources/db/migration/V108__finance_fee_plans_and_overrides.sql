-- BAY-45 / Wave 2: versioned fee plans, installment templates, elections,
-- and student overrides. V60 is already an applied fee-catalogue migration;
-- this forward-only migration adds only the plan slice. Later charge tables
-- must reference fee_plan_line and must not mutate these snapshots.

-- Existing tables predate tenant-safe composite foreign keys. These indexes
-- make every cross-tenant link below prove that both ids belong to one school.
CREATE UNIQUE INDEX uq_school_class_school_id
    ON school_class(school_id, id);
CREATE UNIQUE INDEX uq_student_enrollment_school_id
    ON student_enrollment(school_id, id);

CREATE TABLE installment_template (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    code                VARCHAR(64) NOT NULL,
    name_fr             VARCHAR(160) NOT NULL,
    name_en             VARCHAR(160) NOT NULL,
    lifecycle           VARCHAR(10) NOT NULL DEFAULT 'DRAFT'
        CHECK (lifecycle IN ('DRAFT','ACTIVE','INACTIVE')),
    source_session_id   UUID,
    version             BIGINT NOT NULL DEFAULT 0,
    created_by          UUID REFERENCES app_user(id),
    updated_by          UUID REFERENCES app_user(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_installment_template_school_code UNIQUE (school_id, code),
    CONSTRAINT uq_installment_template_school_id UNIQUE (school_id, id),
    CONSTRAINT chk_installment_template_code
        CHECK (code = upper(code) AND code ~ '^[A-Z0-9_]{1,64}$'),
    CONSTRAINT fk_installment_template_session
        FOREIGN KEY (school_id, source_session_id)
        REFERENCES academic_session(school_id, id)
);

CREATE TABLE installment_template_line (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id               UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    template_id             UUID NOT NULL,
    line_order              INT NOT NULL CHECK (line_order > 0),
    label_fr                VARCHAR(160) NOT NULL,
    label_en                VARCHAR(160) NOT NULL,
    allocation_type         VARCHAR(12) NOT NULL
        CHECK (allocation_type IN ('FIXED','PERCENTAGE')),
    amount_minor            BIGINT,
    percentage_basis_points INT,
    due_rule_type           VARCHAR(24) NOT NULL
        CHECK (due_rule_type IN ('ABSOLUTE_DATE','SESSION_START_OFFSET',
                                 'TERM_START_OFFSET','TERM_END_OFFSET')),
    absolute_due_date       DATE,
    due_offset_days         INT,
    academic_term_id        UUID,
    version                 BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_installment_template_line_order
        UNIQUE (school_id, template_id, line_order),
    CONSTRAINT uq_installment_template_line_school_id UNIQUE (school_id, id),
    CONSTRAINT fk_installment_template_line_template
        FOREIGN KEY (school_id, template_id)
        REFERENCES installment_template(school_id, id) ON DELETE CASCADE,
    CONSTRAINT chk_installment_template_line_allocation CHECK (
        (allocation_type = 'FIXED' AND amount_minor IS NOT NULL
            AND amount_minor >= 0 AND percentage_basis_points IS NULL)
        OR
        (allocation_type = 'PERCENTAGE' AND percentage_basis_points IS NOT NULL
            AND percentage_basis_points BETWEEN 0 AND 10000 AND amount_minor IS NULL)
    ),
    CONSTRAINT chk_installment_template_line_due_rule CHECK (
        (due_rule_type = 'ABSOLUTE_DATE' AND absolute_due_date IS NOT NULL
            AND due_offset_days IS NULL)
        OR
        (due_rule_type <> 'ABSOLUTE_DATE' AND absolute_due_date IS NULL
            AND due_offset_days IS NOT NULL
            AND (academic_term_id IS NOT NULL OR due_rule_type = 'SESSION_START_OFFSET'))
    )
);

CREATE INDEX idx_installment_template_line_template
    ON installment_template_line(school_id, template_id, line_order);

CREATE TABLE fee_plan (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id               UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id     UUID,
    scope_type              VARCHAR(8) CHECK (scope_type IN ('LEVEL','CLASS')),
    level                   VARCHAR(32),
    subsystem               VARCHAR(16),
    school_class_id         UUID,
    plan_version_no         INT DEFAULT 1 CHECK (plan_version_no IS NULL OR plan_version_no > 0),
    lifecycle               VARCHAR(8) NOT NULL DEFAULT 'DRAFT'
        CHECK (lifecycle IN ('DRAFT','ACTIVE','RETIRED')),
    effective_from          DATE DEFAULT current_date,
    effective_to            DATE,
    currency                VARCHAR(3) NOT NULL DEFAULT 'XAF'
        CHECK (currency = upper(currency) AND length(currency) = 3),
    superseded_by_plan_id   UUID,
    -- Compatibility labels/status retained for the BAY-44 dynamic dependency
    -- reader and old fixtures. BAY-45 uses lifecycle and the scoped columns.
    name_fr                 VARCHAR(160),
    name_en                 VARCHAR(160),
    status                  VARCHAR(16),
    created_by               UUID REFERENCES app_user(id),
    updated_by               UUID REFERENCES app_user(id),
    activated_by             UUID REFERENCES app_user(id),
    activated_at             TIMESTAMPTZ,
    retired_by               UUID REFERENCES app_user(id),
    retired_at               TIMESTAMPTZ,
    version                  BIGINT NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_fee_plan_school_id UNIQUE (school_id, id),
    CONSTRAINT uq_fee_plan_version UNIQUE
        (school_id, academic_session_id, scope_type, level, subsystem,
         school_class_id, plan_version_no),
    CONSTRAINT fk_fee_plan_session
        FOREIGN KEY (school_id, academic_session_id)
        REFERENCES academic_session(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_fee_plan_class
        FOREIGN KEY (school_id, school_class_id)
        REFERENCES school_class(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_fee_plan_superseded_by
        FOREIGN KEY (school_id, superseded_by_plan_id)
        REFERENCES fee_plan(school_id, id),
    CONSTRAINT chk_fee_plan_scope_class CHECK (
        scope_type IS NULL OR
        (scope_type = 'LEVEL' AND school_class_id IS NULL)
        OR (scope_type = 'CLASS' AND school_class_id IS NOT NULL)
    ),
    CONSTRAINT chk_fee_plan_dates CHECK (effective_from IS NULL OR effective_to IS NULL OR effective_from <= effective_to)
);

CREATE UNIQUE INDEX uq_fee_plan_one_active_scope
    ON fee_plan(school_id, academic_session_id, scope_type, level, subsystem,
                COALESCE(school_class_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE lifecycle = 'ACTIVE';
CREATE INDEX idx_fee_plan_scope_lookup
    ON fee_plan(school_id, academic_session_id, scope_type, level, subsystem,
                school_class_id, lifecycle);

CREATE TABLE fee_plan_line (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id               UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    fee_plan_id             UUID NOT NULL,
    line_order              INT DEFAULT 1 CHECK (line_order IS NULL OR line_order > 0),
    fee_type_id             UUID,
    fee_type_revision_id    UUID NOT NULL,
    amount_minor            BIGINT DEFAULT 0 CHECK (amount_minor IS NULL OR amount_minor >= 0),
    currency                VARCHAR(3) NOT NULL DEFAULT 'XAF'
        CHECK (currency = upper(currency) AND length(currency) = 3),
    mandatory               BOOLEAN NOT NULL DEFAULT true,
    refundable              BOOLEAN NOT NULL DEFAULT false,
    priority                INT NOT NULL DEFAULT 0,
    installment_template_id UUID,
    version                 BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_fee_plan_line_order UNIQUE (school_id, fee_plan_id, line_order),
    CONSTRAINT uq_fee_plan_line_fee_revision UNIQUE
        (school_id, fee_plan_id, fee_type_revision_id),
    CONSTRAINT uq_fee_plan_line_school_id UNIQUE (school_id, id),
    CONSTRAINT fk_fee_plan_line_plan
        FOREIGN KEY (school_id, fee_plan_id)
        REFERENCES fee_plan(school_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_fee_plan_line_type
        FOREIGN KEY (school_id, fee_type_id)
        REFERENCES fee_type(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_fee_plan_line_revision
        FOREIGN KEY (school_id, fee_type_revision_id)
        REFERENCES fee_type_revision(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_fee_plan_line_template
        FOREIGN KEY (school_id, installment_template_id)
        REFERENCES installment_template(school_id, id) ON DELETE RESTRICT
);

CREATE INDEX idx_fee_plan_line_plan
    ON fee_plan_line(school_id, fee_plan_id, line_order);

CREATE TABLE student_fee_election (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id               UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    student_enrollment_id   UUID NOT NULL,
    fee_plan_line_id        UUID NOT NULL,
    status                  VARCHAR(10) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','ACCEPTED','DECLINED')),
    reason                  VARCHAR(500),
    acted_by                UUID REFERENCES app_user(id),
    acted_at                TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_student_fee_election_line
        UNIQUE (school_id, student_enrollment_id, fee_plan_line_id),
    CONSTRAINT uq_student_fee_election_school_id UNIQUE (school_id, id),
    CONSTRAINT fk_student_fee_election_enrollment
        FOREIGN KEY (school_id, student_enrollment_id)
        REFERENCES student_enrollment(school_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_student_fee_election_line
        FOREIGN KEY (school_id, fee_plan_line_id)
        REFERENCES fee_plan_line(school_id, id) ON DELETE RESTRICT
);

CREATE INDEX idx_student_fee_election_enrollment
    ON student_fee_election(school_id, student_enrollment_id, status);

CREATE TABLE student_fee_override (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id               UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    student_enrollment_id   UUID NOT NULL,
    fee_plan_line_id        UUID NOT NULL,
    override_type           VARCHAR(10) NOT NULL
        CHECK (override_type IN ('AMOUNT','DISCOUNT','EXEMPTION')),
    amount_minor            BIGINT,
    percentage_basis_points INT,
    reason                  VARCHAR(1000) NOT NULL,
    status                  VARCHAR(10) NOT NULL DEFAULT 'REQUESTED'
        CHECK (status IN ('REQUESTED','APPROVED','REJECTED','EXPIRED')),
    effective_from          DATE NOT NULL,
    effective_to            DATE,
    requested_by            UUID REFERENCES app_user(id),
    requested_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_by             UUID REFERENCES app_user(id),
    approved_at             TIMESTAMPTZ,
    decision_reason         VARCHAR(1000),
    version                 BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_student_fee_override_school_id UNIQUE (school_id, id),
    CONSTRAINT fk_student_fee_override_enrollment
        FOREIGN KEY (school_id, student_enrollment_id)
        REFERENCES student_enrollment(school_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_student_fee_override_line
        FOREIGN KEY (school_id, fee_plan_line_id)
        REFERENCES fee_plan_line(school_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_student_fee_override_value CHECK (
        (override_type = 'AMOUNT' AND amount_minor IS NOT NULL
            AND amount_minor >= 0 AND percentage_basis_points IS NULL)
        OR (override_type = 'DISCOUNT' AND percentage_basis_points IS NOT NULL
            AND percentage_basis_points BETWEEN 0 AND 10000 AND amount_minor IS NULL)
        OR (override_type = 'EXEMPTION' AND amount_minor IS NULL
            AND percentage_basis_points IS NULL)
    ),
    CONSTRAINT chk_student_fee_override_dates
        CHECK (effective_to IS NULL OR effective_from <= effective_to)
);

CREATE INDEX idx_student_fee_override_enrollment
    ON student_fee_override(school_id, student_enrollment_id, status, effective_from);

-- Plans are immutable snapshots once active. Retirement is the only allowed
-- mutation of an active plan, and it never changes its scope or lines.
CREATE OR REPLACE FUNCTION reject_fee_plan_mutation() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' AND OLD.lifecycle <> 'DRAFT' THEN
        RAISE EXCEPTION 'active or retired fee plans are immutable; create a new version';
    END IF;
    IF TG_OP = 'UPDATE' AND OLD.lifecycle IN ('ACTIVE','RETIRED') THEN
        IF OLD.lifecycle = 'ACTIVE'
           AND NEW.lifecycle = 'RETIRED'
           AND NEW.school_id = OLD.school_id
           AND NEW.academic_session_id = OLD.academic_session_id
           AND NEW.scope_type = OLD.scope_type
           AND NEW.level = OLD.level
           AND NEW.subsystem = OLD.subsystem
           AND NEW.school_class_id IS NOT DISTINCT FROM OLD.school_class_id
           AND NEW.plan_version_no = OLD.plan_version_no
           AND NEW.effective_from = OLD.effective_from
           AND NEW.effective_to IS NOT DISTINCT FROM OLD.effective_to
           AND NEW.currency = OLD.currency THEN
            RETURN NEW;
        END IF;
        RAISE EXCEPTION 'active or retired fee plans are immutable; create a new version';
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_fee_plan_immutable
    BEFORE UPDATE OR DELETE ON fee_plan
    FOR EACH ROW EXECUTE FUNCTION reject_fee_plan_mutation();

CREATE OR REPLACE FUNCTION reject_fee_plan_line_mutation() RETURNS trigger AS $$
DECLARE
    v_lifecycle VARCHAR(8);
BEGIN
    SELECT lifecycle INTO v_lifecycle
      FROM fee_plan
     WHERE school_id = COALESCE(NEW.school_id, OLD.school_id)
       AND id = COALESCE(NEW.fee_plan_id, OLD.fee_plan_id);
    IF v_lifecycle IS NULL OR v_lifecycle <> 'DRAFT' THEN
        RAISE EXCEPTION 'fee plan lines are editable only while the plan is a draft';
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_fee_plan_line_draft_only
    BEFORE INSERT OR UPDATE OR DELETE ON fee_plan_line
    FOR EACH ROW EXECUTE FUNCTION reject_fee_plan_line_mutation();

-- These tables intentionally do not reference future charge tables. BAY-46
-- charge generation must consume active plan snapshots and keep its own posted
-- charge rows immutable; no plan action is allowed to mutate posted charges.
