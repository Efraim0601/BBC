-- Repair the generated PostgreSQL identifier from V45 and enforce the
-- authoritative assignment invariant for all future writes.  Existing
-- ambiguous rows remain visible in assignment_discrepancy until repaired.

ALTER TABLE class_progression_path
    DROP CONSTRAINT IF EXISTS class_progression_path_school_id_source_session_id_source_c_key;

CREATE OR REPLACE FUNCTION reject_duplicate_responsible_assignment() RETURNS trigger AS $$
BEGIN
    IF NEW.active AND NEW.role='RESPONSIBLE' AND EXISTS (
        SELECT 1 FROM academic_class_subject_teacher x
         WHERE x.school_id=NEW.school_id AND x.academic_session_id=NEW.academic_session_id
           AND x.class_id=NEW.class_id AND x.subject_id=NEW.subject_id
           AND x.role='RESPONSIBLE' AND x.active AND x.id<>NEW.id
    ) THEN
        RAISE EXCEPTION 'Only one active RESPONSIBLE assignment is allowed for a session class subject';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_unique_responsible_assignment ON academic_class_subject_teacher;
CREATE TRIGGER trg_unique_responsible_assignment
    BEFORE INSERT OR UPDATE OF active,role,employee_id,effective_from,effective_to
    ON academic_class_subject_teacher
    FOR EACH ROW EXECUTE FUNCTION reject_duplicate_responsible_assignment();

CREATE INDEX IF NOT EXISTS idx_assignment_discrepancy_subject
    ON assignment_discrepancy(school_id,academic_session_id,class_id,subject_id,status);

ALTER TABLE timetable_version
    ADD CONSTRAINT chk_timetable_published_dates
    CHECK (status<>'PUBLISHED' OR published_at IS NOT NULL);
