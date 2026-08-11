-- BAY-10/BAY-66: replace the unsafe one-year window in the live 2026-2027
-- session with dated, auditable phases in the school's Africa/Douala zone.
UPDATE academic_session
   SET timezone='Africa/Douala',
       teacher_submission_opens_at='2026-09-01 07:00:00+01',
       teacher_submission_closes_at='2027-07-31 18:00:00+01'
 WHERE code='2026-2027';

UPDATE academic_reporting_period p
   SET timezone='Africa/Douala',
       grade_entry_opens_at = CASE p.code
         WHEN 'S1' THEN '2026-09-01 07:00:00+01'::timestamptz
         WHEN 'S2' THEN '2026-10-26 07:00:00+01'::timestamptz
         WHEN 'T1_RESULT' THEN '2026-11-01 07:00:00+01'::timestamptz
         WHEN 'S3' THEN '2026-12-21 07:00:00+01'::timestamptz
         WHEN 'S4' THEN '2027-02-14 07:00:00+01'::timestamptz
         WHEN 'T2_RESULT' THEN '2027-02-15 07:00:00+01'::timestamptz
         WHEN 'S5' THEN '2027-04-11 07:00:00+01'::timestamptz
         WHEN 'S6' THEN '2027-06-06 07:00:00+01'::timestamptz
         WHEN 'T3_RESULT' THEN '2027-06-07 07:00:00+01'::timestamptz
         WHEN 'ANNUAL' THEN '2027-07-01 07:00:00+01'::timestamptz END,
       grade_entry_closes_at = CASE p.code
         WHEN 'S1' THEN '2026-10-09 18:00:00+01'::timestamptz
         WHEN 'S2' THEN '2026-11-27 18:00:00+01'::timestamptz
         WHEN 'T1_RESULT' THEN '2026-11-13 18:00:00+01'::timestamptz
         WHEN 'S3' THEN '2027-01-29 18:00:00+01'::timestamptz
         WHEN 'S4' THEN '2027-03-26 18:00:00+01'::timestamptz
         WHEN 'T2_RESULT' THEN '2027-03-31 18:00:00+01'::timestamptz
         WHEN 'S5' THEN '2027-05-21 18:00:00+01'::timestamptz
         WHEN 'S6' THEN '2027-07-02 18:00:00+01'::timestamptz
         WHEN 'T3_RESULT' THEN '2027-07-09 18:00:00+01'::timestamptz
         WHEN 'ANNUAL' THEN '2027-07-16 18:00:00+01'::timestamptz END,
       teacher_submission_opens_at = CASE p.code
         WHEN 'S1' THEN '2026-10-05 07:00:00+01'::timestamptz
         WHEN 'S2' THEN '2026-11-23 07:00:00+01'::timestamptz
         WHEN 'T1_RESULT' THEN '2026-11-09 07:00:00+01'::timestamptz
         WHEN 'S3' THEN '2027-01-25 07:00:00+01'::timestamptz
         WHEN 'S4' THEN '2027-03-22 07:00:00+01'::timestamptz
         WHEN 'T2_RESULT' THEN '2027-03-29 07:00:00+01'::timestamptz
         WHEN 'S5' THEN '2027-05-17 07:00:00+01'::timestamptz
         WHEN 'S6' THEN '2027-06-28 07:00:00+01'::timestamptz
         WHEN 'T3_RESULT' THEN '2027-07-05 07:00:00+01'::timestamptz
         WHEN 'ANNUAL' THEN '2027-07-12 07:00:00+01'::timestamptz END,
       teacher_submission_closes_at = CASE p.code
         WHEN 'S1' THEN '2026-10-16 18:00:00+01'::timestamptz
         WHEN 'S2' THEN '2026-12-04 18:00:00+01'::timestamptz
         WHEN 'T1_RESULT' THEN '2026-11-20 18:00:00+01'::timestamptz
         WHEN 'S3' THEN '2027-02-05 18:00:00+01'::timestamptz
         WHEN 'S4' THEN '2027-04-02 18:00:00+01'::timestamptz
         WHEN 'T2_RESULT' THEN '2027-04-09 18:00:00+01'::timestamptz
         WHEN 'S5' THEN '2027-05-28 18:00:00+01'::timestamptz
         WHEN 'S6' THEN '2027-07-09 18:00:00+01'::timestamptz
         WHEN 'T3_RESULT' THEN '2027-07-16 18:00:00+01'::timestamptz
         WHEN 'ANNUAL' THEN '2027-07-23 18:00:00+01'::timestamptz END,
       review_opens_at = CASE p.code
         WHEN 'S1' THEN '2026-10-17 07:00:00+01'::timestamptz
         WHEN 'S2' THEN '2026-12-05 07:00:00+01'::timestamptz
         WHEN 'T1_RESULT' THEN '2026-11-21 07:00:00+01'::timestamptz
         WHEN 'S3' THEN '2027-02-06 07:00:00+01'::timestamptz
         WHEN 'S4' THEN '2027-04-03 07:00:00+01'::timestamptz
         WHEN 'T2_RESULT' THEN '2027-04-10 07:00:00+01'::timestamptz
         WHEN 'S5' THEN '2027-05-29 07:00:00+01'::timestamptz
         WHEN 'S6' THEN '2027-07-10 07:00:00+01'::timestamptz
         WHEN 'T3_RESULT' THEN '2027-07-17 07:00:00+01'::timestamptz
         WHEN 'ANNUAL' THEN '2027-07-24 07:00:00+01'::timestamptz END,
       review_closes_at = CASE p.code
         WHEN 'S1' THEN '2026-10-23 18:00:00+01'::timestamptz
         WHEN 'S2' THEN '2026-12-11 18:00:00+01'::timestamptz
         WHEN 'T1_RESULT' THEN '2026-11-27 18:00:00+01'::timestamptz
         WHEN 'S3' THEN '2027-02-12 18:00:00+01'::timestamptz
         WHEN 'S4' THEN '2027-04-09 18:00:00+01'::timestamptz
         WHEN 'T2_RESULT' THEN '2027-04-16 18:00:00+01'::timestamptz
         WHEN 'S5' THEN '2027-06-04 18:00:00+01'::timestamptz
         WHEN 'S6' THEN '2027-07-16 18:00:00+01'::timestamptz
         WHEN 'T3_RESULT' THEN '2027-07-23 18:00:00+01'::timestamptz
         WHEN 'ANNUAL' THEN '2027-07-30 18:00:00+01'::timestamptz END,
       validation_opens_at = CASE p.code
         WHEN 'S1' THEN '2026-10-24 07:00:00+01'::timestamptz
         WHEN 'S2' THEN '2026-12-12 07:00:00+01'::timestamptz
         WHEN 'T1_RESULT' THEN '2026-11-28 07:00:00+01'::timestamptz
         WHEN 'S3' THEN '2027-02-13 07:00:00+01'::timestamptz
         WHEN 'S4' THEN '2027-04-10 07:00:00+01'::timestamptz
         WHEN 'T2_RESULT' THEN '2027-04-17 07:00:00+01'::timestamptz
         WHEN 'S5' THEN '2027-06-05 07:00:00+01'::timestamptz
         WHEN 'S6' THEN '2027-07-17 07:00:00+01'::timestamptz
         WHEN 'T3_RESULT' THEN '2027-07-24 07:00:00+01'::timestamptz
         WHEN 'ANNUAL' THEN '2027-07-31 07:00:00+01'::timestamptz END,
       validation_closes_at = CASE p.code
         WHEN 'S1' THEN '2026-10-30 18:00:00+01'::timestamptz
         WHEN 'S2' THEN '2026-12-18 18:00:00+01'::timestamptz
         WHEN 'T1_RESULT' THEN '2026-12-04 18:00:00+01'::timestamptz
         WHEN 'S3' THEN '2027-02-19 18:00:00+01'::timestamptz
         WHEN 'S4' THEN '2027-04-16 18:00:00+01'::timestamptz
         WHEN 'T2_RESULT' THEN '2027-04-23 18:00:00+01'::timestamptz
         WHEN 'S5' THEN '2027-06-11 18:00:00+01'::timestamptz
         WHEN 'S6' THEN '2027-07-23 18:00:00+01'::timestamptz
         WHEN 'T3_RESULT' THEN '2027-07-30 18:00:00+01'::timestamptz
         WHEN 'ANNUAL' THEN '2027-08-06 18:00:00+01'::timestamptz END,
       bulletin_publish_opens_at = CASE p.code
         WHEN 'S1' THEN '2026-10-31 07:00:00+01'::timestamptz
         WHEN 'S2' THEN '2026-12-19 07:00:00+01'::timestamptz
         WHEN 'T1_RESULT' THEN '2026-12-05 07:00:00+01'::timestamptz
         WHEN 'S3' THEN '2027-02-20 07:00:00+01'::timestamptz
         WHEN 'S4' THEN '2027-04-17 07:00:00+01'::timestamptz
         WHEN 'T2_RESULT' THEN '2027-04-24 07:00:00+01'::timestamptz
         WHEN 'S5' THEN '2027-06-12 07:00:00+01'::timestamptz
         WHEN 'S6' THEN '2027-07-24 07:00:00+01'::timestamptz
         WHEN 'T3_RESULT' THEN '2027-07-31 07:00:00+01'::timestamptz
         WHEN 'ANNUAL' THEN '2027-08-07 07:00:00+01'::timestamptz END,
       bulletin_publish_closes_at = CASE p.code
         WHEN 'S1' THEN '2026-11-06 18:00:00+01'::timestamptz
         WHEN 'S2' THEN '2026-12-25 18:00:00+01'::timestamptz
         WHEN 'T1_RESULT' THEN '2026-12-18 18:00:00+01'::timestamptz
         WHEN 'S3' THEN '2027-02-26 18:00:00+01'::timestamptz
         WHEN 'S4' THEN '2027-04-23 18:00:00+01'::timestamptz
         WHEN 'T2_RESULT' THEN '2027-04-30 18:00:00+01'::timestamptz
         WHEN 'S5' THEN '2027-06-18 18:00:00+01'::timestamptz
         WHEN 'S6' THEN '2027-07-30 18:00:00+01'::timestamptz
         WHEN 'T3_RESULT' THEN '2027-08-06 18:00:00+01'::timestamptz
         WHEN 'ANNUAL' THEN '2027-08-20 18:00:00+01'::timestamptz END,
       correction_opens_at = CASE p.code
         WHEN 'S1' THEN '2026-11-07 07:00:00+01'::timestamptz
         WHEN 'S2' THEN '2026-12-26 07:00:00+01'::timestamptz
         WHEN 'T1_RESULT' THEN '2026-12-19 07:00:00+01'::timestamptz
         WHEN 'S3' THEN '2027-02-27 07:00:00+01'::timestamptz
         WHEN 'S4' THEN '2027-04-24 07:00:00+01'::timestamptz
         WHEN 'T2_RESULT' THEN '2027-05-01 07:00:00+01'::timestamptz
         WHEN 'S5' THEN '2027-06-19 07:00:00+01'::timestamptz
         WHEN 'S6' THEN '2027-07-31 07:00:00+01'::timestamptz
         WHEN 'T3_RESULT' THEN '2027-08-07 07:00:00+01'::timestamptz
         WHEN 'ANNUAL' THEN '2027-08-21 07:00:00+01'::timestamptz END,
       correction_closes_at = CASE p.code
         WHEN 'S1' THEN '2026-11-13 18:00:00+01'::timestamptz
         WHEN 'S2' THEN '2027-01-08 18:00:00+01'::timestamptz
         WHEN 'T1_RESULT' THEN '2027-01-08 18:00:00+01'::timestamptz
         WHEN 'S3' THEN '2027-03-05 18:00:00+01'::timestamptz
         WHEN 'S4' THEN '2027-05-07 18:00:00+01'::timestamptz
         WHEN 'T2_RESULT' THEN '2027-05-14 18:00:00+01'::timestamptz
         WHEN 'S5' THEN '2027-06-25 18:00:00+01'::timestamptz
         WHEN 'S6' THEN '2027-08-06 18:00:00+01'::timestamptz
         WHEN 'T3_RESULT' THEN '2027-08-13 18:00:00+01'::timestamptz
         WHEN 'ANNUAL' THEN '2027-09-03 18:00:00+01'::timestamptz END
 WHERE p.academic_session_id = (SELECT id FROM academic_session WHERE code='2026-2027' ORDER BY start_date DESC LIMIT 1);
