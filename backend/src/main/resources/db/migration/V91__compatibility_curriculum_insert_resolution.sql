-- Compatibility writes may still arrive in the pre-version shape. Resolve
-- those inserts into a canonical v1 without weakening published immutability.
CREATE OR REPLACE FUNCTION reject_published_curriculum_mutation() RETURNS trigger AS $$
DECLARE
  resolved_version UUID;
BEGIN
  IF TG_OP = 'DELETE' THEN
    IF EXISTS (SELECT 1 FROM academic_curriculum_version v WHERE v.id=OLD.curriculum_version_id AND v.state='PUBLISHED') THEN
      RAISE EXCEPTION 'Published curriculum versions are immutable';
    END IF;
    RETURN OLD;
  END IF;
  IF TG_OP = 'INSERT' THEN
    resolved_version := NEW.curriculum_version_id;
    IF resolved_version IS NULL THEN
      SELECT v.id INTO resolved_version FROM academic_curriculum_version v
       WHERE v.school_id=NEW.school_id AND v.academic_session_id=NEW.academic_session_id
         AND v.scope_type='CLASS' AND v.class_id=NEW.class_id AND v.state='PUBLISHED'
       ORDER BY v.version_number DESC LIMIT 1;
    END IF;
    IF resolved_version IS NULL THEN
      INSERT INTO academic_curriculum_version
          (school_id,academic_session_id,scope_type,class_id,version_number,state,effective_from,effective_to)
      SELECT NEW.school_id, s.id, 'CLASS', NEW.class_id,
             COALESCE((SELECT max(v.version_number)+1 FROM academic_curriculum_version v
                        WHERE v.school_id=NEW.school_id AND v.academic_session_id=s.id
                          AND v.scope_type='CLASS' AND v.class_id=NEW.class_id),1),
             'PUBLISHED', s.start_date, s.end_date
        FROM academic_session s
       WHERE s.id=NEW.academic_session_id AND s.school_id=NEW.school_id
      RETURNING id INTO resolved_version;
    END IF;
    IF resolved_version IS NULL THEN RAISE EXCEPTION 'Canonical published curriculum version is required'; END IF;
    NEW.curriculum_version_id := resolved_version;
    RETURN NEW;
  END IF;
  IF EXISTS (SELECT 1 FROM academic_curriculum_version v WHERE v.id=OLD.curriculum_version_id AND v.state='PUBLISHED') THEN
    RAISE EXCEPTION 'Published curriculum versions are immutable';
  END IF;
  IF NEW.curriculum_version_id <> OLD.curriculum_version_id THEN
    RAISE EXCEPTION 'Curriculum subject identity cannot change';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
