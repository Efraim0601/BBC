-- BAY-10/BAY-66: hierarchical reporting periods and explicit academic windows.
-- academic_term remains the trimester; this table adds sequences, term results,
-- and the independent annual result milestone.

CREATE TABLE academic_reporting_period (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    academic_term_id UUID REFERENCES academic_term(id) ON DELETE CASCADE,
    code VARCHAR(32) NOT NULL,
    label VARCHAR(120) NOT NULL,
    period_type VARCHAR(20) NOT NULL CHECK (period_type IN ('SEQUENCE','TERM_RESULT','ANNUAL_RESULT')),
    display_order INT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    grade_entry_opens_at TIMESTAMPTZ,
    grade_entry_closes_at TIMESTAMPTZ,
    review_opens_at TIMESTAMPTZ,
    review_closes_at TIMESTAMPTZ,
    validation_opens_at TIMESTAMPTZ,
    validation_closes_at TIMESTAMPTZ,
    bulletin_publish_opens_at TIMESTAMPTZ,
    bulletin_publish_closes_at TIMESTAMPTZ,
    correction_opens_at TIMESTAMPTZ,
    correction_closes_at TIMESTAMPTZ,
    calculation_policy VARCHAR(64) NOT NULL DEFAULT 'DEFAULT',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','OPEN','CLOSED','PUBLISHED','ARCHIVED')),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_reporting_period_dates CHECK (start_date <= end_date),
    CONSTRAINT chk_reporting_period_parent CHECK (
        (period_type = 'ANNUAL_RESULT' AND academic_term_id IS NULL)
        OR (period_type <> 'ANNUAL_RESULT' AND academic_term_id IS NOT NULL)
    ),
    UNIQUE (school_id, academic_session_id, code),
    UNIQUE (school_id, academic_session_id, display_order)
);

CREATE INDEX idx_reporting_period_session_order
    ON academic_reporting_period(school_id, academic_session_id, display_order);
CREATE INDEX idx_reporting_period_term_order
    ON academic_reporting_period(school_id, academic_term_id, display_order);

CREATE OR REPLACE FUNCTION validate_reporting_period_dates() RETURNS trigger AS $$
BEGIN
    IF NEW.period_type = 'ANNUAL_RESULT' THEN
        IF NEW.start_date < (SELECT start_date FROM academic_session WHERE id = NEW.academic_session_id)
           OR NEW.end_date > (SELECT end_date FROM academic_session WHERE id = NEW.academic_session_id) THEN
            RAISE EXCEPTION 'Annual reporting period must remain inside its academic session';
        END IF;
    ELSE
        IF NEW.start_date < (SELECT start_date FROM academic_term WHERE id = NEW.academic_term_id)
           OR NEW.end_date > (SELECT end_date FROM academic_term WHERE id = NEW.academic_term_id) THEN
            RAISE EXCEPTION 'Reporting period must remain inside its academic term';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_reporting_period_dates
    BEFORE INSERT OR UPDATE ON academic_reporting_period
    FOR EACH ROW EXECUTE FUNCTION validate_reporting_period_dates();
