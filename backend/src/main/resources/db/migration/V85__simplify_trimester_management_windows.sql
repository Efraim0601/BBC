-- Replace the normal runtime policy's action/scope matrix with one optional
-- management window per academic trimester.  The V83 rule and override tables
-- intentionally remain for history and rollback; runtime code no longer reads
-- them for effective access decisions.

ALTER TABLE academic_term
    ADD COLUMN IF NOT EXISTS management_window_limited BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS management_opens_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS management_closes_at TIMESTAMPTZ;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conrelid = 'academic_term'::regclass
           AND conname = 'chk_academic_term_management_window'
    ) THEN
        ALTER TABLE academic_term
            ADD CONSTRAINT chk_academic_term_management_window CHECK (
                (
                    management_window_limited = FALSE
                    AND management_opens_at IS NULL
                    AND management_closes_at IS NULL
                )
                OR
                (
                    management_window_limited = TRUE
                    AND (management_opens_at IS NOT NULL OR management_closes_at IS NOT NULL)
                    AND (
                        management_opens_at IS NULL
                        OR management_closes_at IS NULL
                        OR management_closes_at > management_opens_at
                    )
                )
            );
    END IF;
END $$;

COMMENT ON COLUMN academic_term.management_window_limited IS
    'Optional trimester-wide date restriction; replaces normal runtime use of action-specific session, term, and period rules.';
COMMENT ON COLUMN academic_term.management_opens_at IS
    'Inclusive opening instant for the optional trimester management window; term timezone remains authoritative.';
COMMENT ON COLUMN academic_term.management_closes_at IS
    'Inclusive closing instant for the optional trimester management window; term timezone remains authoritative.';

-- Collapse the explicit LIMITED V83 rows into a broad union envelope.  A
-- missing endpoint in any participating rule deliberately keeps that endpoint
-- open-ended.  Overrides are not candidates: they are temporary historical
-- exceptions, not configuration intent.
WITH candidates AS (
    SELECT t.id AS term_id, w.opens_at, w.closes_at
      FROM academic_term t
      JOIN academic_workflow_window_rule w
        ON w.school_id = t.school_id
       AND w.academic_session_id = t.academic_session_id
       AND w.scope_type = 'SESSION'
       AND w.mode = 'LIMITED'
    UNION ALL
    SELECT t.id AS term_id, w.opens_at, w.closes_at
      FROM academic_term t
      JOIN academic_workflow_window_rule w
        ON w.school_id = t.school_id
       AND w.academic_session_id = t.academic_session_id
       AND w.scope_type = 'TERM'
       AND w.academic_term_id = t.id
       AND w.mode = 'LIMITED'
    UNION ALL
    SELECT t.id AS term_id, w.opens_at, w.closes_at
      FROM academic_term t
      JOIN academic_reporting_period p
        ON p.school_id = t.school_id
       AND p.academic_session_id = t.academic_session_id
       AND p.academic_term_id = t.id
      JOIN academic_workflow_window_rule w
        ON w.school_id = p.school_id
       AND w.academic_session_id = p.academic_session_id
       AND w.scope_type = 'PERIOD'
       AND w.reporting_period_id = p.id
       AND w.mode = 'LIMITED'
    UNION ALL
    SELECT t.id AS term_id, w.opens_at, w.closes_at
      FROM academic_term t
      JOIN academic_reporting_period p
        ON p.school_id = t.school_id
       AND p.academic_session_id = t.academic_session_id
       AND p.academic_term_id IS NULL
       AND p.period_type = 'ANNUAL_RESULT'
      JOIN academic_workflow_window_rule w
        ON w.school_id = p.school_id
       AND w.academic_session_id = p.academic_session_id
       AND w.scope_type = 'PERIOD'
       AND w.reporting_period_id = p.id
       AND w.mode = 'LIMITED'
     WHERE t.sequence_no = 3 OR upper(t.code) = 'T3'
), aggregate_by_term AS (
    SELECT t.id AS term_id,
           count(c.term_id) AS candidate_count,
           bool_or(c.opens_at IS NULL) AS opening_is_open,
           min(c.opens_at) AS earliest_opening,
           bool_or(c.closes_at IS NULL) AS closing_is_open,
           max(c.closes_at) AS latest_closing
      FROM academic_term t
      LEFT JOIN candidates c ON c.term_id = t.id
     GROUP BY t.id
)
UPDATE academic_term t
   SET management_window_limited = CASE
       WHEN a.candidate_count > 0
        AND (CASE WHEN a.opening_is_open THEN NULL ELSE a.earliest_opening END IS NOT NULL
          OR CASE WHEN a.closing_is_open THEN NULL ELSE a.latest_closing END IS NOT NULL)
       THEN TRUE ELSE FALSE END,
       management_opens_at = CASE
           WHEN a.candidate_count > 0 AND NOT a.opening_is_open THEN a.earliest_opening
           ELSE NULL
       END,
       management_closes_at = CASE
           WHEN a.candidate_count > 0 AND NOT a.closing_is_open THEN a.latest_closing
           ELSE NULL
       END
  FROM aggregate_by_term a
 WHERE a.term_id = t.id;
