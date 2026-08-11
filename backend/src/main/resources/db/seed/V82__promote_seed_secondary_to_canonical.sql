-- The demo seed creates legacy secondary definitions after the production
-- migrations have run. Promote those definitions through the same additive
-- canonical path as V77 so a blank demo database exercises both paths.
DO $$
DECLARE
    r RECORD;
    mark_row RECORD;
    assessment_id UUID;
    existing_id UUID;
    curriculum_id UUID;
    order_value INT;
    code_value VARCHAR(40);
BEGIN
    FOR r IN
        SELECT c.id AS competency_id,
               c.school_id,
               c.code AS legacy_code,
               c.description,
               c.max_score,
               c.display_order,
               m.id AS model_id,
               m.academic_session_id,
               m.reporting_period_id,
               m.class_id,
               p.code AS period_code,
               s.code AS subject_code
          FROM secondary_competency c
          JOIN secondary_competency_model m ON m.id=c.model_id
          JOIN academic_reporting_period p ON p.id=m.reporting_period_id
          JOIN subject s ON s.id=m.subject_id
         WHERE m.status <> 'RETIRED'
           AND p.period_type='SEQUENCE'
           AND NOT EXISTS (
               SELECT 1
                 FROM secondary_competency_model newer
                WHERE newer.school_id=m.school_id
                  AND newer.academic_session_id=m.academic_session_id
                  AND newer.reporting_period_id=m.reporting_period_id
                  AND newer.class_id=m.class_id
                  AND newer.subject_id=m.subject_id
                  AND newer.locale=m.locale
                  AND newer.status <> 'RETIRED'
                  AND newer.version > m.version
           )
         ORDER BY c.school_id, m.academic_session_id, m.reporting_period_id,
                  m.class_id, m.subject_id, c.display_order, c.id
    LOOP
        SELECT cs.id INTO curriculum_id
          FROM academic_curriculum_subject cs
         WHERE cs.school_id=r.school_id
           AND cs.academic_session_id=r.academic_session_id
           AND cs.class_id=r.class_id
           AND cs.subject_id=(SELECT subject_id FROM secondary_competency_model WHERE id=r.model_id)
           AND (cs.active_from IS NULL OR cs.active_from <= (SELECT start_date FROM academic_reporting_period WHERE id=r.reporting_period_id))
           AND (cs.active_to IS NULL OR cs.active_to >= (SELECT end_date FROM academic_reporting_period WHERE id=r.reporting_period_id))
         LIMIT 1;

        IF curriculum_id IS NULL THEN
            CONTINUE;
        END IF;

        SELECT a.id INTO assessment_id
          FROM academic_assessment a
         WHERE a.legacy_secondary_competency_id=r.competency_id
         LIMIT 1;
        IF assessment_id IS NOT NULL THEN
            CONTINUE;
        END IF;

        code_value := upper(trim(r.legacy_code));
        IF code_value IS NULL OR code_value='' THEN
            code_value := 'EVAL_' || upper(r.period_code) || '_' || upper(r.subject_code);
        ELSIF length(code_value) > 40 THEN
            code_value := left(regexp_replace(code_value, '[^A-Z0-9_-]+', '_', 'g'), 31)
                          || '_' || substr(md5(r.competency_id::text), 1, 8);
        END IF;

        SELECT a.id INTO existing_id
          FROM academic_assessment a
         WHERE a.school_id=r.school_id
           AND a.reporting_period_id=r.reporting_period_id
           AND a.class_id=r.class_id
           AND upper(a.subject_code)=upper(r.subject_code)
           AND upper(a.code)=upper(code_value)
         LIMIT 1;
        IF existing_id IS NOT NULL THEN
            CONTINUE;
        END IF;

        order_value := greatest(r.display_order, 1);
        WHILE EXISTS (
            SELECT 1
              FROM academic_assessment a
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
    END LOOP;

    FOR mark_row IN
        SELECT scm.id AS mark_id,
               scm.school_id,
               scm.reporting_period_id,
               scm.student_id,
               scm.enrollment_id,
               scm.teacher_id,
               scm.mark,
               scm.value_status,
               a.id AS assessment_id,
               a.academic_session_id,
               a.subject_code
          FROM secondary_competency_mark scm
          JOIN academic_assessment a
            ON a.legacy_secondary_competency_id=scm.competency_id
           AND a.school_id=scm.school_id
         WHERE NOT EXISTS (
               SELECT 1
                 FROM academic_grade g
                WHERE g.legacy_secondary_mark_id=scm.id
           )
    LOOP
        INSERT INTO academic_grade
            (school_id, academic_session_id, reporting_period_id,
             assessment_id, student_id, enrollment_id, subject_code,
             teacher_id, mark, value_status, workflow_status,
             legacy_secondary_mark_id)
        SELECT mark_row.school_id, mark_row.academic_session_id, mark_row.reporting_period_id,
               mark_row.assessment_id, mark_row.student_id, mark_row.enrollment_id,
               upper(mark_row.subject_code), mark_row.teacher_id, mark_row.mark,
               mark_row.value_status, 'DRAFT', mark_row.mark_id
         WHERE NOT EXISTS (
               SELECT 1
                 FROM academic_grade g
                WHERE g.school_id=mark_row.school_id
                  AND g.student_id=mark_row.student_id
                  AND g.assessment_id=mark_row.assessment_id
                  AND upper(g.subject_code)=upper(mark_row.subject_code)
           );
    END LOOP;
END $$;
