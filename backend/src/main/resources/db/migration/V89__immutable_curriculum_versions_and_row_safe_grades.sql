-- BAY-33: immutable curriculum aggregates, canonical subject identity, and
-- evidence for legacy rows that cannot be mapped without guessing.

CREATE TABLE academic_curriculum_version (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    scope_type VARCHAR(16) NOT NULL CHECK (scope_type IN ('CLASS','REUSABLE')),
    class_id UUID REFERENCES school_class(id) ON DELETE CASCADE,
    version_number INT NOT NULL CHECK (version_number > 0),
    state VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (state IN ('DRAFT','PUBLISHED','SUPERSEDED')),
    source_version_id UUID REFERENCES academic_curriculum_version(id),
    source_copy_run_id UUID REFERENCES academic_copy_run(id),
    effective_from DATE,
    effective_to DATE,
    created_by UUID REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_by UUID REFERENCES app_user(id),
    published_at TIMESTAMPTZ,
    optimistic_version BIGINT NOT NULL DEFAULT 0,
    canonical_content_hash VARCHAR(64),
    CHECK ((scope_type = 'CLASS' AND class_id IS NOT NULL)
        OR (scope_type = 'REUSABLE' AND class_id IS NULL)),
    CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_from <= effective_to),
    UNIQUE (school_id, academic_session_id, scope_type, class_id, version_number)
);
CREATE UNIQUE INDEX ux_academic_curriculum_published_class
    ON academic_curriculum_version(school_id, academic_session_id, class_id)
    WHERE scope_type = 'CLASS' AND state = 'PUBLISHED';
CREATE UNIQUE INDEX ux_academic_curriculum_published_reusable
    ON academic_curriculum_version(school_id, academic_session_id)
    WHERE scope_type = 'REUSABLE' AND state = 'PUBLISHED';
CREATE INDEX idx_academic_curriculum_version_scope
    ON academic_curriculum_version(school_id, academic_session_id, class_id, state, version_number DESC);

-- The old natural key made a second immutable revision impossible. Evidence
-- identity is now (curriculum version, subject), while the original row UUIDs
-- are retained by the version-1 backfill below.
ALTER TABLE academic_curriculum_subject
    ADD COLUMN IF NOT EXISTS curriculum_version_id UUID;
ALTER TABLE academic_curriculum_subject
    DROP CONSTRAINT IF EXISTS academic_curriculum_subject_school_id_academic_session_id_class_id_subject_id_key;
ALTER TABLE academic_curriculum_subject
    DROP CONSTRAINT IF EXISTS academic_curriculum_subject_school_id_academic_session_id_c_key;

INSERT INTO academic_curriculum_version
    (school_id, academic_session_id, scope_type, class_id, version_number, state,
     effective_from, effective_to, created_at, published_at, canonical_content_hash)
SELECT DISTINCT c.school_id, c.academic_session_id, 'CLASS', c.class_id, 1, 'PUBLISHED',
       s.start_date, s.end_date, now(), now(), NULL
  FROM academic_curriculum_subject c
  JOIN academic_session s ON s.id = c.academic_session_id
 ON CONFLICT (school_id, academic_session_id, scope_type, class_id, version_number) DO NOTHING;

UPDATE academic_curriculum_subject c
   SET curriculum_version_id = v.id
  FROM academic_curriculum_version v
 WHERE v.school_id = c.school_id
   AND v.academic_session_id = c.academic_session_id
   AND v.scope_type = 'CLASS'
   AND v.class_id = c.class_id
   AND v.version_number = 1
   AND c.curriculum_version_id IS NULL;

UPDATE academic_curriculum_version v
   SET canonical_content_hash = md5(COALESCE(x.content, ''))
  FROM (
      SELECT curriculum_version_id,
             string_agg(concat_ws('|', subject_id::text, display_order, coefficient,
                                   max_score, mandatory, pass_threshold, show_subject_rank,
                                   remark_required, COALESCE(active_from::text,''),
                                   COALESCE(active_to::text,'')), E'\n' ORDER BY display_order, subject_id) AS content
        FROM academic_curriculum_subject
       WHERE curriculum_version_id IS NOT NULL
       GROUP BY curriculum_version_id
  ) x
 WHERE v.id = x.curriculum_version_id;

ALTER TABLE academic_curriculum_subject
    ALTER COLUMN curriculum_version_id SET NOT NULL;
CREATE UNIQUE INDEX ux_academic_curriculum_subject_version_subject
    ON academic_curriculum_subject(school_id, curriculum_version_id, subject_id);
CREATE INDEX idx_academic_curriculum_subject_canonical
    ON academic_curriculum_subject(school_id, academic_session_id, class_id, curriculum_version_id, display_order);

ALTER TABLE academic_assessment
    ADD COLUMN IF NOT EXISTS curriculum_version_id UUID REFERENCES academic_curriculum_version(id),
    ADD COLUMN IF NOT EXISTS curriculum_subject_id UUID REFERENCES academic_curriculum_subject(id);
ALTER TABLE academic_grade
    ADD COLUMN IF NOT EXISTS curriculum_version_id UUID REFERENCES academic_curriculum_version(id),
    ADD COLUMN IF NOT EXISTS curriculum_subject_id UUID REFERENCES academic_curriculum_subject(id),
    ADD COLUMN IF NOT EXISTS responsible_assignment_id UUID REFERENCES academic_class_subject_teacher(id),
    ADD COLUMN IF NOT EXISTS policy_decision JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS last_request_id UUID;
CREATE INDEX idx_academic_grade_curriculum_trace
    ON academic_grade(school_id, curriculum_version_id, curriculum_subject_id, reporting_period_id);
CREATE UNIQUE INDEX ux_academic_grade_request_row
    ON academic_grade(school_id, last_request_id, student_id, assessment_id, subject_code)
    WHERE last_request_id IS NOT NULL;

CREATE TABLE academic_grade_save_request (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    actor_user_id UUID REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE academic_grade_save_result (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL REFERENCES academic_grade_save_request(id) ON DELETE CASCADE,
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    student_id UUID REFERENCES student(id),
    assessment_id UUID REFERENCES academic_assessment(id),
    subject_code VARCHAR(32),
    outcome VARCHAR(16) NOT NULL CHECK (outcome IN ('SAVED','UNCHANGED','CONFLICT','INVALID','FORBIDDEN')),
    current_value JSONB NOT NULL DEFAULT '{}'::jsonb,
    field_errors JSONB NOT NULL DEFAULT '{}'::jsonb,
    retryable BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (request_id, student_id, assessment_id, subject_code)
);

CREATE TABLE legacy_grade_migration_exception (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    source_table VARCHAR(64) NOT NULL,
    source_id UUID NOT NULL,
    source_sequence INT,
    reason_code VARCHAR(64) NOT NULL,
    candidates JSONB NOT NULL DEFAULT '[]'::jsonb,
    source_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','RESOLVED','IGNORED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, source_table, source_id)
);
CREATE INDEX idx_legacy_grade_exception_scope
    ON legacy_grade_migration_exception(school_id, status, created_at);

-- Deterministic legacy export: only exactly one session/enrollment/period and
-- assessment candidate may be promoted. Every other row is retained as an
-- explicit exception rather than being guessed or dropped.
DO $$
DECLARE r RECORD; candidate_count INT; candidate RECORD;
BEGIN
  FOR r IN SELECT g.* FROM grade g ORDER BY g.school_id, g.id LOOP
    SELECT count(*) INTO candidate_count
      FROM academic_session s
      JOIN student_enrollment e ON e.school_id=r.school_id AND e.student_id=r.student_id
       AND e.academic_session_id=s.id AND e.status='ACTIVE'
      JOIN academic_reporting_period p ON p.academic_session_id=s.id
       AND p.school_id=r.school_id AND p.period_type='SEQUENCE'
       AND upper(p.code)=('S'||r.sequence)
      JOIN academic_assessment a ON a.school_id=r.school_id AND a.reporting_period_id=p.id
       AND (a.class_id IS NULL OR a.class_id=e.school_class_id)
       AND (a.subject_code IS NULL OR upper(a.subject_code)=upper(r.subject_code))
      JOIN academic_curriculum_subject cs ON cs.school_id=r.school_id
       AND cs.academic_session_id=s.id AND cs.class_id=e.school_class_id
       AND upper((SELECT code FROM subject WHERE id=cs.subject_id))=upper(r.subject_code)
       AND cs.curriculum_version_id=(SELECT v.id FROM academic_curriculum_version v
                                      WHERE v.school_id=r.school_id AND v.academic_session_id=s.id
                                        AND v.class_id=e.school_class_id AND v.state='PUBLISHED'
                                      LIMIT 1);
    IF candidate_count = 1 THEN
      SELECT s.id AS session_id, e.id AS enrollment_id, e.school_class_id AS class_id,
             p.id AS period_id, a.id AS assessment_id, cs.id AS curriculum_subject_id,
             cs.curriculum_version_id AS curriculum_version_id
        INTO candidate
        FROM academic_session s
        JOIN student_enrollment e ON e.school_id=r.school_id AND e.student_id=r.student_id
         AND e.academic_session_id=s.id AND e.status='ACTIVE'
        JOIN academic_reporting_period p ON p.academic_session_id=s.id AND p.school_id=r.school_id
         AND p.period_type='SEQUENCE' AND upper(p.code)=('S'||r.sequence)
        JOIN academic_assessment a ON a.school_id=r.school_id AND a.reporting_period_id=p.id
         AND (a.class_id IS NULL OR a.class_id=e.school_class_id)
         AND (a.subject_code IS NULL OR upper(a.subject_code)=upper(r.subject_code))
        JOIN academic_curriculum_subject cs ON cs.school_id=r.school_id
         AND cs.academic_session_id=s.id AND cs.class_id=e.school_class_id
         AND upper((SELECT code FROM subject WHERE id=cs.subject_id))=upper(r.subject_code)
         AND cs.curriculum_version_id=(SELECT v.id FROM academic_curriculum_version v
                                        WHERE v.school_id=r.school_id AND v.academic_session_id=s.id
                                          AND v.class_id=e.school_class_id AND v.state='PUBLISHED'
                                        LIMIT 1);
      INSERT INTO academic_grade(id,school_id,academic_session_id,reporting_period_id,assessment_id,
          student_id,enrollment_id,subject_code,mark,value_status,workflow_status,
          curriculum_version_id,curriculum_subject_id,policy_decision)
      VALUES (r.id,r.school_id,candidate.session_id,candidate.period_id,candidate.assessment_id,
          r.student_id,candidate.enrollment_id,upper(r.subject_code),r.mark,'SCORED','DRAFT',
          candidate.curriculum_version_id,candidate.curriculum_subject_id,
          jsonb_build_object('source','LEGACY_GRADE','sequence',r.sequence))
      ON CONFLICT (id) DO NOTHING;
    ELSE
      INSERT INTO legacy_grade_migration_exception
          (school_id,source_table,source_id,source_sequence,reason_code,candidates,source_payload)
      VALUES (r.school_id,'grade',r.id,r.sequence,
              CASE WHEN candidate_count=0 THEN 'NO_UNAMBIGUOUS_MAPPING' ELSE 'AMBIGUOUS_MAPPING' END,
              (SELECT COALESCE(jsonb_agg(jsonb_build_object('sessionId',s.id,'enrollmentId',e.id,
                    'periodId',p.id,'assessmentId',a.id)), '[]'::jsonb)
                 FROM academic_session s
                 JOIN student_enrollment e ON e.school_id=r.school_id AND e.student_id=r.student_id
                  AND e.academic_session_id=s.id AND e.status='ACTIVE'
                 JOIN academic_reporting_period p ON p.academic_session_id=s.id AND p.school_id=r.school_id
                  AND p.period_type='SEQUENCE' AND upper(p.code)=('S'||r.sequence)
                 JOIN academic_assessment a ON a.school_id=r.school_id AND a.reporting_period_id=p.id
                  AND (a.class_id IS NULL OR a.class_id=e.school_class_id)
                  AND (a.subject_code IS NULL OR upper(a.subject_code)=upper(r.subject_code))),
              jsonb_build_object('studentId',r.student_id,'subjectCode',r.subject_code,'mark',r.mark));
    END IF;
  END LOOP;
END $$;

CREATE OR REPLACE FUNCTION reject_published_curriculum_mutation() RETURNS trigger AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    IF EXISTS (SELECT 1 FROM academic_curriculum_version v WHERE v.id=OLD.curriculum_version_id AND v.state='PUBLISHED') THEN
      RAISE EXCEPTION 'Published curriculum versions are immutable';
    END IF;
    RETURN OLD;
  END IF;
  IF TG_OP = 'INSERT' THEN
    IF NEW.curriculum_version_id IS NULL THEN
      SELECT v.id INTO NEW.curriculum_version_id FROM academic_curriculum_version v
       WHERE v.school_id=NEW.school_id AND v.academic_session_id=NEW.academic_session_id
         AND v.scope_type='CLASS' AND v.class_id=NEW.class_id AND v.state='PUBLISHED'
       ORDER BY v.version_number DESC LIMIT 1;
    END IF;
    IF NEW.curriculum_version_id IS NULL THEN RAISE EXCEPTION 'Canonical published curriculum version is required'; END IF;
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
CREATE TRIGGER trg_curriculum_subject_immutability
  BEFORE INSERT OR UPDATE OR DELETE ON academic_curriculum_subject
  FOR EACH ROW EXECUTE FUNCTION reject_published_curriculum_mutation();

CREATE OR REPLACE FUNCTION reject_published_curriculum_version_mutation() RETURNS trigger AS $$
BEGIN
  IF TG_OP = 'DELETE' AND OLD.state='PUBLISHED' THEN RAISE EXCEPTION 'Published curriculum versions are immutable'; END IF;
  IF TG_OP = 'UPDATE' AND OLD.state='PUBLISHED' AND NEW.state <> 'SUPERSEDED' THEN
    IF ROW(NEW.school_id,NEW.academic_session_id,NEW.scope_type,NEW.class_id,NEW.version_number,
           NEW.source_version_id,NEW.source_copy_run_id,NEW.effective_from,NEW.effective_to,
           NEW.created_by,NEW.created_at,NEW.canonical_content_hash)
       IS DISTINCT FROM ROW(OLD.school_id,OLD.academic_session_id,OLD.scope_type,OLD.class_id,OLD.version_number,
           OLD.source_version_id,OLD.source_copy_run_id,OLD.effective_from,OLD.effective_to,
           OLD.created_by,OLD.created_at,OLD.canonical_content_hash)
      THEN RAISE EXCEPTION 'Published curriculum versions are immutable'; END IF;
  END IF;
  RETURN COALESCE(NEW,OLD);
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_curriculum_version_immutability
  BEFORE UPDATE OR DELETE ON academic_curriculum_version
  FOR EACH ROW EXECUTE FUNCTION reject_published_curriculum_version_mutation();
