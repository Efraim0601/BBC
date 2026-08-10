-- Demo-only additive data for the secondary report-card fidelity slice.
-- This migration is intentionally after V76 so it can use the versioned
-- template, branding, competency, and artifact tables.  It never deletes or
-- rewrites legacy promotion rows or existing draft batches.

DO $$
DECLARE
    v_school UUID := '11111111-1111-1111-1111-111111111111';
    v_session UUID;
    v_class RECORD;
    v_period RECORD;
    v_subject RECORD;
    v_model UUID;
    v_competency UUID;
    v_locale VARCHAR(8);
    v_teacher UUID;
    v_png BYTEA := decode('89504E470D0A1A0A0000000D49484452000000010000000108060000001F15C4890000000D49444154789C6360000000020001E221BC330000000049454E44AE426082', 'hex');
BEGIN
    SELECT id INTO v_session FROM academic_session
     WHERE school_id=v_school AND code='2025-2026' LIMIT 1;
    IF v_session IS NULL THEN
        RETURN;
    END IF;

    -- Canonicalise the demo roster to the session/class graph.  Student.class
    -- fields remain compatibility data; report-card queries use these rows.
    UPDATE student s SET class_id=c.id, subsystem=c.subsystem, level=c.level
      FROM school_class c
     WHERE s.school_id=v_school AND c.school_id=v_school
       AND ((s.matricule='BBC-1001' AND c.subsystem='FR' AND c.level='secondary' AND c.name LIKE '4%')
         OR (s.matricule='BBC-1002' AND c.subsystem='FR' AND c.level='secondary' AND c.name LIKE '5%')
         OR (s.matricule='BBC-1003' AND c.subsystem='FR' AND c.level='secondary' AND c.name LIKE '3%')
         OR (s.matricule='BBC-1005' AND c.subsystem='EN' AND c.level='secondary' AND c.name='Form 5'));

    UPDATE student_enrollment e SET school_class_id=s.class_id,
           class_name_snapshot=c.name, level_snapshot=c.level,
           subsystem_snapshot=c.subsystem
      FROM student s JOIN school_class c ON c.id=s.class_id
     WHERE e.school_id=v_school AND e.academic_session_id=v_session
       AND e.student_id=s.id AND s.class_id IS NOT NULL;

    -- A small teacher roster is enough to exercise subject and homeroom
    -- assignment resolution in the printable evidence.
    INSERT INTO employee (id, school_id, code, name, initials, sex, type, email)
    VALUES
      ('aaaaaaaa-0000-0000-0000-000000000002',v_school,'EMP-002','MANGA Elise','ME','F','Permanent','e.manga@bbc.cm'),
      ('aaaaaaaa-0000-0000-0000-000000000003',v_school,'EMP-003','TCHINDA Paul','TP','M','Permanent','p.tchinda@bbc.cm'),
      ('aaaaaaaa-0000-0000-0000-000000000004',v_school,'EMP-004','MBIDA Grace','MG','F','Permanent','g.mbida@bbc.cm')
    ON CONFLICT (school_id, code) DO NOTHING;
    INSERT INTO employee_role (employee_id, role_code)
    SELECT e.id,'teacher' FROM employee e
     WHERE e.school_id=v_school AND e.code IN ('EMP-002','EMP-003','EMP-004')
    ON CONFLICT DO NOTHING;

    INSERT INTO teacher_subject (employee_id, subject_id)
    SELECT e.id,s.id
      FROM employee e CROSS JOIN subject s
     WHERE e.school_id=v_school AND s.school_id=v_school
       AND e.code IN ('EMP-002','EMP-003','EMP-004')
       AND s.code IN ('MATH','FR','EN','HG','SVT','PC')
       AND ((e.code='EMP-002' AND s.code IN ('MATH','PC'))
         OR (e.code='EMP-003' AND s.code IN ('FR','EN','HG'))
         OR (e.code='EMP-004' AND s.code IN ('SVT')))
    ON CONFLICT DO NOTHING;
    INSERT INTO teacher_class (employee_id, class_id)
    SELECT e.id,c.id FROM employee e CROSS JOIN school_class c
     WHERE e.school_id=v_school AND c.school_id=v_school
       AND e.code IN ('EMP-002','EMP-003','EMP-004') AND c.level='secondary'
    ON CONFLICT DO NOTHING;

    -- Three terms plus six evidence sequences.  Windows are deliberately
    -- open in the demo so Settings and lifecycle screens can be exercised.
    INSERT INTO academic_term (school_id,academic_session_id,code,label,sequence_no,start_date,end_date,
                               grade_entry_opens_at,grade_entry_closes_at,bulletin_publish_opens_at,bulletin_publish_closes_at)
    VALUES
      (v_school,v_session,'T1','First Term',1,'2025-09-01','2025-11-30',now()-interval '1 day',now()+interval '365 days',now()-interval '1 day',now()+interval '365 days'),
      (v_school,v_session,'T2','Second Term',2,'2025-12-01','2026-03-31',now()-interval '1 day',now()+interval '365 days',now()-interval '1 day',now()+interval '365 days'),
      (v_school,v_session,'T3','Third Term',3,'2026-04-01','2026-07-31',now()-interval '1 day',now()+interval '365 days',now()-interval '1 day',now()+interval '365 days')
    ON CONFLICT (school_id,academic_session_id,code) DO NOTHING;

    INSERT INTO academic_reporting_period
      (school_id,academic_session_id,academic_term_id,code,label,period_type,display_order,start_date,end_date,
       grade_entry_opens_at,grade_entry_closes_at,review_opens_at,review_closes_at,validation_opens_at,
       validation_closes_at,bulletin_publish_opens_at,bulletin_publish_closes_at,correction_opens_at,
       correction_closes_at,status)
    SELECT v_school,v_session,term.id,p.code,p.label,p.period_type,p.display_order,p.start_date,p.end_date,
           now()-interval '1 day',now()+interval '365 days',now()-interval '1 day',now()+interval '365 days',
           now()-interval '1 day',now()+interval '365 days',now()-interval '1 day',now()+interval '365 days',
           now()-interval '1 day',now()+interval '365 days','OPEN'
      FROM (VALUES
        ('S1','Sequence 1','SEQUENCE',1,'2025-09-01'::date,'2025-10-15'::date,'T1'),
        ('S2','Sequence 2','SEQUENCE',2,'2025-10-16'::date,'2025-11-30'::date,'T1'),
        ('T1_RESULT','First Term Result','TERM_RESULT',3,'2025-09-01'::date,'2025-11-30'::date,'T1'),
        ('S3','Sequence 3','SEQUENCE',4,'2025-12-01'::date,'2026-01-31'::date,'T2'),
        ('S4','Sequence 4','SEQUENCE',5,'2026-02-01'::date,'2026-03-31'::date,'T2'),
        ('T2_RESULT','Second Term Result','TERM_RESULT',6,'2025-12-01'::date,'2026-03-31'::date,'T2'),
        ('S5','Sequence 5','SEQUENCE',7,'2026-04-01'::date,'2026-05-31'::date,'T3'),
        ('S6','Sequence 6','SEQUENCE',8,'2026-06-01'::date,'2026-07-31'::date,'T3'),
        ('T3_RESULT','Third Term Result','TERM_RESULT',9,'2026-04-01'::date,'2026-07-31'::date,'T3'),
        ('ANNUAL','Annual Result','ANNUAL_RESULT',10,'2025-09-01'::date,'2026-07-31'::date,NULL)
      ) AS p(code,label,period_type,display_order,start_date,end_date,term_code)
      LEFT JOIN academic_term term ON term.school_id=v_school AND term.academic_session_id=v_session
                                   AND term.code=p.term_code
    ON CONFLICT (school_id,academic_session_id,code) DO NOTHING;

    -- Freeze the configured dependency graph after the periods exist.
    INSERT INTO academic_reporting_period_dependency
      (school_id,academic_session_id,parent_period_id,child_period_id,weight,optional,display_order)
    SELECT p.school_id,p.academic_session_id,p.id,c.id,0.5,false,c.display_order
      FROM academic_reporting_period p JOIN academic_reporting_period c
        ON c.school_id=p.school_id AND c.academic_session_id=p.academic_session_id
       AND c.code=CASE p.code WHEN 'T1_RESULT' THEN 'S1' WHEN 'T2_RESULT' THEN 'S3' WHEN 'T3_RESULT' THEN 'S5' END
     WHERE p.school_id=v_school AND p.code IN ('T1_RESULT','T2_RESULT','T3_RESULT')
    ON CONFLICT DO NOTHING;
    INSERT INTO academic_reporting_period_dependency
      (school_id,academic_session_id,parent_period_id,child_period_id,weight,optional,display_order)
    SELECT p.school_id,p.academic_session_id,p.id,c.id,0.5,false,c.display_order+1
      FROM academic_reporting_period p JOIN academic_reporting_period c
        ON c.school_id=p.school_id AND c.academic_session_id=p.academic_session_id
       AND c.code=CASE p.code WHEN 'T1_RESULT' THEN 'S2' WHEN 'T2_RESULT' THEN 'S4' WHEN 'T3_RESULT' THEN 'S6' END
     WHERE p.school_id=v_school AND p.code IN ('T1_RESULT','T2_RESULT','T3_RESULT')
    ON CONFLICT DO NOTHING;
    INSERT INTO academic_reporting_period_dependency
      (school_id,academic_session_id,parent_period_id,child_period_id,weight,optional,display_order)
    SELECT p.school_id,p.academic_session_id,p.id,c.id,1.0/3.0,false,c.display_order
      FROM academic_reporting_period p JOIN academic_reporting_period c
        ON c.school_id=p.school_id AND c.academic_session_id=p.academic_session_id
       AND c.code IN ('T1_RESULT','T2_RESULT','T3_RESULT')
     WHERE p.school_id=v_school AND p.code='ANNUAL'
    ON CONFLICT DO NOTHING;

    -- Vary coefficients by class/subject; the session curriculum is the
    -- report-card authority, while subject.coef remains only the default.
    INSERT INTO subject_class_coef (school_id,subject_id,class_id,coef)
    SELECT v_school,s.id,c.id,
           CASE s.code WHEN 'MATH' THEN CASE WHEN c.subsystem='EN' THEN 5 ELSE 4 END
                       WHEN 'FR' THEN 4 WHEN 'EN' THEN 3 ELSE 2 END
      FROM school_class c CROSS JOIN subject s
     WHERE c.school_id=v_school AND c.level='secondary' AND s.school_id=v_school
       AND s.code IN ('MATH','FR','EN','HG','SVT','PC')
    ON CONFLICT (school_id,subject_id,class_id) DO NOTHING;
    INSERT INTO academic_curriculum_subject
      (school_id,academic_session_id,class_id,subject_id,display_order,coefficient,max_score,mandatory,pass_threshold)
    SELECT v_school,v_session,c.id,s.id,row_number() OVER (PARTITION BY c.id ORDER BY s.code),
           CASE s.code WHEN 'MATH' THEN CASE WHEN c.subsystem='EN' THEN 5 ELSE 4 END
                       WHEN 'FR' THEN 4 WHEN 'EN' THEN 3 ELSE 2 END,
           20,true,10
      FROM school_class c CROSS JOIN subject s
     WHERE c.school_id=v_school AND c.level='secondary' AND s.school_id=v_school
       AND s.code IN ('MATH','FR','EN','HG','SVT','PC')
    ON CONFLICT (school_id,academic_session_id,class_id,subject_id) DO NOTHING;
    INSERT INTO academic_class_subject_teacher
      (school_id,academic_session_id,class_id,subject_id,employee_id,role,source)
    SELECT v_school,v_session,c.id,s.id,
           CASE s.code WHEN 'MATH' THEN 'aaaaaaaa-0000-0000-0000-000000000002'::uuid
                       WHEN 'PC' THEN 'aaaaaaaa-0000-0000-0000-000000000002'::uuid
                       WHEN 'SVT' THEN 'aaaaaaaa-0000-0000-0000-000000000004'::uuid
                       ELSE 'aaaaaaaa-0000-0000-0000-000000000003'::uuid END,
           'RESPONSIBLE','MANUAL'
      FROM school_class c CROSS JOIN subject s
     WHERE c.school_id=v_school AND c.level='secondary' AND s.school_id=v_school
       AND s.code IN ('MATH','FR','EN','HG','SVT','PC')
    ON CONFLICT DO NOTHING;
    INSERT INTO class_teacher_assignment
      (school_id,academic_session_id,class_id,employee_id,role,effective_from,status,source)
    SELECT v_school,v_session,c.id,'aaaaaaaa-0000-0000-0000-000000000003'::uuid,'HOMEROOM','2025-09-01','ACTIVE','MANUAL'
      FROM school_class c
     WHERE c.school_id=v_school AND c.level='secondary'
    ON CONFLICT DO NOTHING;

    -- Published, versioned competency descriptions and marks for every
    -- sequence.  These are intentionally independent of primary APC data.
    FOR v_class IN SELECT id,subsystem FROM school_class WHERE school_id=v_school AND level='secondary' LOOP
        v_locale := CASE WHEN v_class.subsystem='EN' THEN 'en' ELSE 'fr' END;
        FOR v_period IN SELECT id,code FROM academic_reporting_period
                         WHERE school_id=v_school AND period_type='SEQUENCE' LOOP
            FOR v_subject IN SELECT id,code FROM subject WHERE school_id=v_school AND code IN ('MATH','FR','EN','HG','SVT','PC') LOOP
                INSERT INTO secondary_competency_model
                  (school_id,academic_session_id,reporting_period_id,class_id,subject_id,locale,name,version,status,source,published_at)
                VALUES (v_school,v_session,v_period.id,v_class.id,v_subject.id,v_locale,
                        CASE WHEN v_locale='fr' THEN 'Competences evaluees - '||v_subject.code
                             ELSE 'Competencies evaluated - '||v_subject.code END,
                        1,'PUBLISHED','SEED',now())
                ON CONFLICT (school_id,academic_session_id,reporting_period_id,class_id,subject_id,locale,version) DO NOTHING
                RETURNING id INTO v_model;
                IF v_model IS NULL THEN
                    SELECT id INTO v_model FROM secondary_competency_model
                     WHERE school_id=v_school AND academic_session_id=v_session AND reporting_period_id=v_period.id
                       AND class_id=v_class.id AND subject_id=v_subject.id AND locale=v_locale AND version=1;
                END IF;
                INSERT INTO secondary_competency (school_id,model_id,code,description,max_score,display_order)
                VALUES
                  (v_school,v_model,'UNDERSTAND',CASE WHEN v_locale='fr'
                    THEN 'Comprendre et mobiliser les notions du programme dans une situation nouvelle.'
                    ELSE 'Understand and mobilise the programme concepts in a new situation.' END,20,1),
                  (v_school,v_model,'APPLY',CASE WHEN v_locale='fr'
                    THEN 'Appliquer une demarche rigoureuse et communiquer un resultat justifie.'
                    ELSE 'Apply a rigorous method and communicate a justified result.' END,20,2)
                ON CONFLICT (school_id,model_id,code) DO NOTHING;
                FOR v_competency IN SELECT id FROM secondary_competency WHERE school_id=v_school AND model_id=v_model ORDER BY display_order LOOP
                    INSERT INTO secondary_competency_mark
                      (school_id,model_id,competency_id,reporting_period_id,student_id,enrollment_id,teacher_id,mark,value_status)
                    SELECT v_school,v_model,v_competency,v_period.id,e.student_id,e.id,
                           (SELECT employee_id FROM academic_class_subject_teacher a
                             WHERE a.school_id=v_school AND a.academic_session_id=v_session
                               AND a.class_id=v_class.id AND a.subject_id=v_subject.id
                               AND a.role='RESPONSIBLE' AND a.active LIMIT 1),
                           CASE WHEN e.student_id='cccccccc-0000-0000-0000-000000000002'::uuid THEN 16 ELSE 14 END,
                           'SCORED'
                      FROM student_enrollment e
                     WHERE e.school_id=v_school AND e.academic_session_id=v_session
                       AND e.school_class_id=v_class.id AND e.status='ACTIVE'
                    ON CONFLICT (school_id,model_id,competency_id,reporting_period_id,student_id) DO NOTHING;
                END LOOP;
                v_model := NULL;
            END LOOP;
        END LOOP;
    END LOOP;

    -- A deterministic image asset for one student; another student is left
    -- without a photo to exercise the explicit no-photo placeholder.
    INSERT INTO profile_photo (owner_type,owner_id,school_id,content_type,bytes,byte_size)
    VALUES ('student','cccccccc-0000-0000-0000-000000000001',v_school,'image/png',v_png, length(v_png))
    ON CONFLICT (owner_type,owner_id) DO NOTHING;
    INSERT INTO profile_photo_version
      (school_id,owner_type,owner_id,content_type,bytes,byte_size,sha256,captured_at)
    VALUES (v_school,'student','cccccccc-0000-0000-0000-000000000001','image/png',v_png,length(v_png),encode(digest(v_png,'sha256'),'hex'),now())
    ON CONFLICT (school_id,owner_type,owner_id,sha256) DO NOTHING;

    -- Finalized attendance, including an absence and lateness, feeds the
    -- report-card attendance aggregate without using legacy daily fallbacks.
    INSERT INTO attendance_session
      (school_id,academic_session_id,school_class_id,session_date,model,period_key,status,finalized_at)
    SELECT v_school,v_session,c.id,'2025-10-15','PERIOD','T1','FINALIZED',now()
      FROM school_class c WHERE c.school_id=v_school AND c.level='secondary'
    ON CONFLICT (school_id,academic_session_id,school_class_id,session_date,period_key) DO NOTHING;
    INSERT INTO attendance_mark
      (school_id,attendance_session_id,student_id,status,late_minutes,marked_at)
    SELECT v_school,a.id,e.student_id,
           CASE WHEN e.student_id='cccccccc-0000-0000-0000-000000000001'::uuid THEN 'ABSENT'
                WHEN e.student_id='cccccccc-0000-0000-0000-000000000002'::uuid THEN 'LATE'
                ELSE 'PRESENT' END,
           CASE WHEN e.student_id='cccccccc-0000-0000-0000-000000000002'::uuid THEN 12 ELSE 0 END,now()
      FROM attendance_session a JOIN student_enrollment e
        ON e.school_id=v_school AND e.academic_session_id=v_session
       AND e.school_class_id=a.school_class_id AND e.status='ACTIVE'
     WHERE a.school_id=v_school AND a.academic_session_id=v_session AND a.period_key='T1'
    ON CONFLICT (school_id,attendance_session_id,student_id) DO NOTHING;

    INSERT INTO attendance_period_adjustment
      (school_id,academic_session_id,reporting_period_id,student_id,justified_absence_hours,
       unjustified_absence_hours,late_minutes,reason,status)
    SELECT v_school,v_session,p.id,'cccccccc-0000-0000-0000-000000000001'::uuid,2.0,1.0,12,
           'Medical note and late arrival recorded for the demo.','APPROVED'
      FROM academic_reporting_period p WHERE p.school_id=v_school AND p.code='T1_RESULT'
    ON CONFLICT DO NOTHING;

    -- Approved council inputs demonstrate distinctions, conduct and a class
    -- decision in the immutable report evidence.
    INSERT INTO student_period_conduct
      (school_id,academic_session_id,reporting_period_id,student_id,honor_roll,encouragement,
       congratulations,conduct_warning,decision_code,council_observation,status)
    SELECT v_school,v_session,p.id,e.student_id,
           e.student_id='cccccccc-0000-0000-0000-000000000001'::uuid,
           e.student_id='cccccccc-0000-0000-0000-000000000002'::uuid,
           e.student_id='cccccccc-0000-0000-0000-000000000001'::uuid,
           e.student_id='cccccccc-0000-0000-0000-000000000003'::uuid,
           CASE WHEN p.code='ANNUAL' THEN 'PROMOTED' ELSE 'MEETS_EXPECTATIONS' END,
           CASE WHEN e.student_id='cccccccc-0000-0000-0000-000000000003'::uuid THEN 'Work and attendance follow-up.' ELSE 'Positive class participation.' END,
           'APPROVED'
      FROM academic_reporting_period p JOIN student_enrollment e
        ON e.school_id=v_school AND e.academic_session_id=v_session AND e.status='ACTIVE'
     WHERE p.school_id=v_school AND p.code IN ('T1_RESULT','ANNUAL')
    ON CONFLICT (school_id,student_id,reporting_period_id) DO NOTHING;
END $$;
