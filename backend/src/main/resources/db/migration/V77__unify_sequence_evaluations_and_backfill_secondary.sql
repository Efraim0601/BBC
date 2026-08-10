-- Academic evaluation milestone unification.
--
-- This transition is additive.  The canonical academic_assessment and
-- academic_grade tables become the live path for every subsystem while the
-- secondary competency tables remain available for compatibility and audit.
-- Bulletin snapshots are deliberately not updated by this migration.

CREATE TABLE IF NOT EXISTS academic_assessment_generation_batch (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    class_id UUID NOT NULL REFERENCES school_class(id) ON DELETE CASCADE,
    mode VARCHAR(24) NOT NULL CHECK (mode IN ('ONE_SEQUENCE','ALL_SEQUENCES','LEGACY_BACKFILL')),
    idempotency_key VARCHAR(120),
    scope_fingerprint VARCHAR(128) NOT NULL,
    requested_count INT NOT NULL DEFAULT 0,
    created_count INT NOT NULL DEFAULT 0,
    existing_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    actor_user_id UUID REFERENCES app_user(id),
    result_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_assessment_generation_batch_scope
    ON academic_assessment_generation_batch(school_id, academic_session_id, class_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_assessment_generation_batch_idempotency
    ON academic_assessment_generation_batch(school_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

ALTER TABLE academic_assessment
    ADD COLUMN IF NOT EXISTS source VARCHAR(24) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN IF NOT EXISTS generation_batch_id UUID REFERENCES academic_assessment_generation_batch(id),
    ADD COLUMN IF NOT EXISTS legacy_secondary_competency_id UUID;

ALTER TABLE academic_grade
    ADD COLUMN IF NOT EXISTS legacy_secondary_mark_id UUID;

CREATE UNIQUE INDEX IF NOT EXISTS uq_academic_assessment_legacy_secondary
    ON academic_assessment(legacy_secondary_competency_id)
    WHERE legacy_secondary_competency_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_academic_grade_legacy_secondary_mark
    ON academic_grade(legacy_secondary_mark_id)
    WHERE legacy_secondary_mark_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS academic_secondary_migration_conflict (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    source_type VARCHAR(32) NOT NULL,
    source_id UUID NOT NULL,
    academic_session_id UUID,
    reporting_period_id UUID,
    class_id UUID,
    subject_code VARCHAR(32),
    reason_code VARCHAR(64) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_academic_secondary_migration_conflict_scope
    ON academic_secondary_migration_conflict(school_id, academic_session_id, reporting_period_id, created_at);

DO $$
DECLARE
    r RECORD;
    curriculum_id UUID;
    assessment_id UUID;
    existing_id UUID;
    grade_id UUID;
    existing_mark NUMERIC;
    existing_status VARCHAR;
    code_value VARCHAR(40);
    order_value INT;
BEGIN
    -- Only the latest active model for a class/subject/sequence/locale is
    -- promoted. Older versions remain untouched and are auditable in their
    -- original tables.
    FOR r IN
        SELECT c.id AS competency_id, c.code AS legacy_code, c.description,
               c.max_score, c.display_order, m.id AS model_id,
               m.school_id, m.academic_session_id, m.reporting_period_id,
               m.class_id, m.subject_id, m.locale, p.code AS period_code,
               s.code AS subject_code, m.version
          FROM secondary_competency c
          JOIN secondary_competency_model m ON m.id=c.model_id
          JOIN academic_reporting_period p ON p.id=m.reporting_period_id
          JOIN subject s ON s.id=m.subject_id
         WHERE m.status <> 'RETIRED'
           AND p.period_type='SEQUENCE'
           AND NOT EXISTS (
               SELECT 1 FROM secondary_competency_model newer
                WHERE newer.school_id=m.school_id
                  AND newer.academic_session_id=m.academic_session_id
                  AND newer.reporting_period_id=m.reporting_period_id
                  AND newer.class_id=m.class_id
                  AND newer.subject_id=m.subject_id
                  AND newer.locale=m.locale
                  AND newer.status <> 'RETIRED'
                  AND newer.version > m.version
           )
         ORDER BY m.school_id, m.academic_session_id, m.reporting_period_id,
                  m.class_id, m.subject_id, c.display_order, c.id
    LOOP
        SELECT c.id INTO curriculum_id
          FROM academic_curriculum_subject c
         WHERE c.school_id=r.school_id
           AND c.academic_session_id=r.academic_session_id
           AND c.class_id=r.class_id
           AND c.subject_id=r.subject_id
           AND (c.active_from IS NULL OR c.active_from <= (SELECT start_date FROM academic_reporting_period WHERE id=r.reporting_period_id))
           AND (c.active_to IS NULL OR c.active_to >= (SELECT end_date FROM academic_reporting_period WHERE id=r.reporting_period_id))
         LIMIT 1;

        IF curriculum_id IS NULL THEN
            INSERT INTO academic_secondary_migration_conflict
                (school_id, source_type, source_id, academic_session_id,
                 reporting_period_id, class_id, subject_code, reason_code, details)
            VALUES (r.school_id, 'SECONDARY_COMPETENCY', r.competency_id,
                    r.academic_session_id, r.reporting_period_id, r.class_id,
                    r.subject_code, 'SUBJECT_NOT_ASSIGNED_TO_CLASS',
                    jsonb_build_object('modelId', r.model_id, 'legacyCode', r.legacy_code));
            CONTINUE;
        END IF;

        code_value := upper(trim(r.legacy_code));
        IF code_value IS NULL OR code_value='' THEN
            code_value := 'EVAL_' || upper(r.period_code) || '_' || upper(r.subject_code);
        ELSIF length(code_value) > 40 THEN
            code_value := left(regexp_replace(code_value, '[^A-Z0-9_-]+', '_', 'g'), 31)
                          || '_' || substr(md5(r.competency_id::text), 1, 8);
        END IF;

        SELECT a.id INTO assessment_id
          FROM academic_assessment a
         WHERE a.legacy_secondary_competency_id=r.competency_id
         LIMIT 1;
        IF assessment_id IS NULL THEN
            SELECT a.id INTO existing_id
              FROM academic_assessment a
             WHERE a.school_id=r.school_id
               AND a.reporting_period_id=r.reporting_period_id
               AND a.class_id=r.class_id
               AND upper(a.subject_code)=upper(r.subject_code)
               AND upper(a.code)=upper(code_value)
             LIMIT 1;
            IF existing_id IS NOT NULL THEN
                INSERT INTO academic_secondary_migration_conflict
                    (school_id, source_type, source_id, academic_session_id,
                     reporting_period_id, class_id, subject_code, reason_code, details)
                VALUES (r.school_id, 'SECONDARY_COMPETENCY', r.competency_id,
                        r.academic_session_id, r.reporting_period_id, r.class_id,
                        r.subject_code, 'ASSESSMENT_CODE_ALREADY_EXISTS',
                        jsonb_build_object('code', code_value, 'existingAssessmentId', existing_id));
                CONTINUE;
            END IF;

            order_value := greatest(r.display_order, 1);
            WHILE EXISTS (
                SELECT 1 FROM academic_assessment a
                 WHERE a.school_id=r.school_id
                   AND a.reporting_period_id=r.reporting_period_id
                   AND a.class_id=r.class_id
                   AND upper(a.subject_code)=upper(r.subject_code)
                   AND a.display_order=order_value
            ) LOOP
                order_value := order_value + 1;
            END LOOP;

            INSERT INTO academic_assessment
                (school_id, academic_session_id, reporting_period_id,
                 subject_code, class_id, code, label, assessment_type,
                 max_score, weight, mandatory, display_order, source,
                 legacy_secondary_competency_id)
            VALUES (r.school_id, r.academic_session_id, r.reporting_period_id,
                    upper(r.subject_code), r.class_id, code_value,
                    left(trim(r.description), 160), 'SEQUENCE_EVALUATION',
                    greatest(r.max_score, 0.01), 1, true, order_value,
                    'LEGACY_SECONDARY', r.competency_id)
            RETURNING id INTO assessment_id;
        END IF;
    END LOOP;

    -- Promote marks without replacing a canonical value that is already
    -- present. A mismatch is retained as an explicit reconciliation item.
    FOR r IN
        SELECT m.id AS mark_id, m.school_id, m.model_id, m.competency_id,
               m.reporting_period_id, m.student_id, m.enrollment_id,
               m.teacher_id, m.mark, m.value_status,
               a.id AS assessment_id, a.academic_session_id, a.class_id,
               a.subject_code
          FROM secondary_competency_mark m
          JOIN academic_assessment a ON a.legacy_secondary_competency_id=m.competency_id
         WHERE m.school_id=a.school_id
    LOOP
        SELECT g.id, g.mark, g.value_status INTO grade_id, existing_mark, existing_status
          FROM academic_grade g
         WHERE g.school_id=r.school_id
           AND g.student_id=r.student_id
           AND g.assessment_id=r.assessment_id
           AND upper(g.subject_code)=upper(r.subject_code)
         LIMIT 1;

        IF grade_id IS NULL THEN
            INSERT INTO academic_grade
                (school_id, academic_session_id, reporting_period_id,
                 assessment_id, student_id, enrollment_id, subject_code,
                 teacher_id, mark, value_status, workflow_status,
                 legacy_secondary_mark_id)
            VALUES (r.school_id, r.academic_session_id, r.reporting_period_id,
                    r.assessment_id, r.student_id, r.enrollment_id,
                    upper(r.subject_code), r.teacher_id, r.mark,
                    r.value_status, 'DRAFT', r.mark_id);
        ELSIF existing_mark IS NOT DISTINCT FROM r.mark
              AND existing_status IS NOT DISTINCT FROM r.value_status THEN
            UPDATE academic_grade SET legacy_secondary_mark_id=r.mark_id
             WHERE id=grade_id AND legacy_secondary_mark_id IS NULL;
        ELSE
            INSERT INTO academic_secondary_migration_conflict
                (school_id, source_type, source_id, academic_session_id,
                 reporting_period_id, class_id, subject_code, reason_code, details)
            VALUES (r.school_id, 'SECONDARY_COMPETENCY_MARK', r.mark_id,
                    r.academic_session_id, r.reporting_period_id, r.class_id,
                    r.subject_code, 'CANONICAL_GRADE_CONFLICT',
                    jsonb_build_object('canonicalGradeId', grade_id,
                                       'legacyMark', r.mark,
                                       'canonicalMark', existing_mark,
                                       'legacyStatus', r.value_status,
                                       'canonicalStatus', existing_status));
        END IF;
    END LOOP;
END $$;
