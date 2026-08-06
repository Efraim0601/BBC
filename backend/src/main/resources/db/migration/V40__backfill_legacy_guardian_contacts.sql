-- Convert legacy father/mother/tutor contact blocks into first-class guardians.
WITH contacts AS (
  SELECT school_id,id student_id,'FATHER' relationship_type,father_name display_name,father_email email,father_phone phone FROM student WHERE father_name IS NOT NULL
  UNION ALL SELECT school_id,id,'MOTHER',mother_name,mother_email,mother_phone FROM student WHERE mother_name IS NOT NULL
  UNION ALL SELECT school_id,id,COALESCE(NULLIF(upper(guardian_relation),''),'GUARDIAN'),guardian_name,guardian_email,guardian_phone FROM student WHERE guardian_name IS NOT NULL
), canonical AS (
  SELECT DISTINCT ON (school_id, identity_key) school_id,display_name,email,phone,
    lower(trim(email)) normalized_email,regexp_replace(COALESCE(phone,''),'[^0-9+]','','g') normalized_phone
  FROM (SELECT c.*,COALESCE(lower(trim(email)),NULLIF(regexp_replace(COALESCE(phone,''),'[^0-9+]','','g'),''),lower(trim(display_name))) identity_key FROM contacts c) x
  ORDER BY school_id,identity_key,display_name
)
INSERT INTO guardian(id,school_id,display_name,email,normalized_email,phone,normalized_phone,status)
SELECT gen_random_uuid(),c.school_id,c.display_name,c.email,c.normalized_email,c.phone,NULLIF(c.normalized_phone,''),'NO_PORTAL'
FROM canonical c
WHERE NOT EXISTS (SELECT 1 FROM guardian g WHERE g.school_id=c.school_id AND (
  (c.normalized_email IS NOT NULL AND g.normalized_email=c.normalized_email) OR
  (c.normalized_email IS NULL AND c.normalized_phone<>'' AND g.normalized_phone=c.normalized_phone) OR
  (c.normalized_email IS NULL AND c.normalized_phone='' AND lower(g.display_name)=lower(c.display_name))));

WITH contacts AS (
  SELECT school_id,id student_id,'FATHER' relationship_type,father_name display_name,father_email email,father_phone phone FROM student WHERE father_name IS NOT NULL
  UNION ALL SELECT school_id,id,'MOTHER',mother_name,mother_email,mother_phone FROM student WHERE mother_name IS NOT NULL
  UNION ALL SELECT school_id,id,COALESCE(NULLIF(upper(guardian_relation),''),'GUARDIAN'),guardian_name,guardian_email,guardian_phone FROM student WHERE guardian_name IS NOT NULL
)
INSERT INTO student_guardian(school_id,student_id,guardian_id,relationship_type,legal_guardian,pickup_authorized,receives_academic,receives_attendance,portal_access)
SELECT c.school_id,c.student_id,g.id,c.relationship_type,true,true,true,true,false
FROM contacts c JOIN LATERAL (
 SELECT id FROM guardian g WHERE g.school_id=c.school_id AND g.status<>'MERGED' AND (
   (c.email IS NOT NULL AND g.normalized_email=lower(trim(c.email))) OR
   (c.email IS NULL AND c.phone IS NOT NULL AND g.normalized_phone=regexp_replace(c.phone,'[^0-9+]','','g')) OR
   (c.email IS NULL AND c.phone IS NULL AND lower(g.display_name)=lower(trim(c.display_name))))
 ORDER BY CASE WHEN c.email IS NOT NULL AND g.normalized_email=lower(trim(c.email)) THEN 0 ELSE 1 END LIMIT 1
) g ON true
ON CONFLICT(school_id,student_id,guardian_id) DO NOTHING;
