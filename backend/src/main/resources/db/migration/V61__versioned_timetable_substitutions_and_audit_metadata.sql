-- BAY-52..57 / BAY-39..43 / BAY-34..38
-- Additive structural layer for immutable timetable history, dated
-- substitutions, graph metadata, packet history, and controlled templates.

CREATE TABLE IF NOT EXISTS timetable_version (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    version_no INT NOT NULL CHECK (version_no > 0),
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
    effective_from DATE NOT NULL,
    effective_to DATE,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Africa/Douala',
    copied_from_version_id UUID REFERENCES timetable_version(id),
    published_at TIMESTAMPTZ,
    published_by UUID REFERENCES app_user(id),
    archive_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, academic_session_id, version_no),
    CHECK (effective_to IS NULL OR effective_to >= effective_from)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_timetable_one_published_version
    ON timetable_version(school_id, academic_session_id) WHERE status='PUBLISHED';
CREATE INDEX IF NOT EXISTS idx_timetable_version_session_status
    ON timetable_version(school_id, academic_session_id, status, version_no DESC);

ALTER TABLE timetable_slot ADD COLUMN IF NOT EXISTS timetable_version_id UUID REFERENCES timetable_version(id);

-- Every pre-versioned slot belongs to an explicit version 1.  A session with
-- any published class configuration keeps a published history marker; all
-- other legacy configurations remain editable drafts.
INSERT INTO timetable_version
    (school_id, academic_session_id, version_no, status, effective_from, effective_to, timezone, published_at)
SELECT s.school_id, s.id, 1,
       CASE WHEN EXISTS (
           SELECT 1 FROM timetable_class_config c
            WHERE c.school_id=s.school_id AND c.academic_session_id=s.id AND c.status='PUBLISHED'
       ) THEN 'PUBLISHED' ELSE 'DRAFT' END,
       s.start_date, s.end_date, COALESCE(s.timezone, 'Africa/Douala'),
       CASE WHEN EXISTS (
           SELECT 1 FROM timetable_class_config c
            WHERE c.school_id=s.school_id AND c.academic_session_id=s.id AND c.status='PUBLISHED'
       ) THEN now() ELSE NULL END
  FROM academic_session s
ON CONFLICT (school_id, academic_session_id, version_no) DO NOTHING;

UPDATE timetable_slot t
   SET timetable_version_id=v.id
  FROM timetable_version v
 WHERE v.school_id=t.school_id AND v.academic_session_id=t.academic_session_id
   AND t.timetable_version_id IS NULL;

-- V44 uniqueness was session-wide.  Version-aware uniqueness allows a new
-- draft to coexist with the historical published version without changing
-- the historical rows.
DROP INDEX IF EXISTS ux_timetable_slot_session_cell;
DROP INDEX IF EXISTS ux_timetable_teacher_no_double_booking;
DROP INDEX IF EXISTS ux_timetable_room_no_double_booking;
CREATE UNIQUE INDEX IF NOT EXISTS ux_timetable_slot_version_cell
    ON timetable_slot(school_id,timetable_version_id,class_id,day_idx,slot_idx);
CREATE UNIQUE INDEX IF NOT EXISTS ux_timetable_teacher_version_no_double_booking
    ON timetable_slot(school_id,timetable_version_id,day_idx,slot_idx,teacher_id)
    WHERE teacher_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_timetable_room_version_no_double_booking
    ON timetable_slot(school_id,timetable_version_id,day_idx,slot_idx,lower(room))
    WHERE room IS NOT NULL AND btrim(room)<>'';
CREATE INDEX IF NOT EXISTS idx_timetable_version_teacher
    ON timetable_slot(school_id,timetable_version_id,teacher_id,day_idx,slot_idx);

CREATE TABLE IF NOT EXISTS timetable_room (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    code VARCHAR(64) NOT NULL,
    label VARCHAR(160) NOT NULL,
    capacity INT CHECK (capacity IS NULL OR capacity > 0),
    resource_type VARCHAR(32) NOT NULL DEFAULT 'ROOM',
    active BOOLEAN NOT NULL DEFAULT true,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (school_id, code)
);
CREATE TABLE IF NOT EXISTS timetable_room_availability (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    room_id UUID NOT NULL REFERENCES timetable_room(id) ON DELETE CASCADE,
    day_idx INT NOT NULL CHECK (day_idx BETWEEN 0 AND 6),
    slot_idx INT NOT NULL CHECK (slot_idx BETWEEN 0 AND 15),
    available BOOLEAN NOT NULL DEFAULT true,
    reason VARCHAR(240),
    UNIQUE (room_id, day_idx, slot_idx)
);
INSERT INTO timetable_room(school_id,code,label)
SELECT DISTINCT school_id, btrim(room), btrim(room)
  FROM timetable_slot
 WHERE room IS NOT NULL AND btrim(room)<>''
ON CONFLICT (school_id,code) DO NOTHING;

CREATE TABLE IF NOT EXISTS timetable_substitution (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    timetable_version_id UUID REFERENCES timetable_version(id),
    occurrence_date DATE NOT NULL,
    class_id UUID NOT NULL REFERENCES school_class(id) ON DELETE CASCADE,
    subject_code VARCHAR(32),
    day_idx INT NOT NULL CHECK (day_idx BETWEEN 0 AND 6),
    slot_idx INT NOT NULL CHECK (slot_idx BETWEEN 0 AND 15),
    original_teacher_id UUID REFERENCES employee(id),
    replacement_teacher_id UUID REFERENCES employee(id),
    action VARCHAR(16) NOT NULL CHECK (action IN ('SUBSTITUTE','CANCEL')),
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','APPROVED','CANCELLED')),
    approved_by UUID REFERENCES app_user(id),
    approved_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((action='CANCEL' AND replacement_teacher_id IS NULL) OR
           (action='SUBSTITUTE' AND replacement_teacher_id IS NOT NULL)),
    UNIQUE (school_id, academic_session_id, occurrence_date, class_id, day_idx, slot_idx)
);
CREATE INDEX IF NOT EXISTS idx_timetable_substitution_date
    ON timetable_substitution(school_id, academic_session_id, occurrence_date, status);

-- Keep legacy path rows readable while giving them a graph/version identity.
CREATE TABLE IF NOT EXISTS progression_graph_version (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    source_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    target_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    version_no INT NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
    copied_from_id UUID REFERENCES progression_graph_version(id),
    published_at TIMESTAMPTZ,
    published_by UUID REFERENCES app_user(id),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (school_id, source_session_id, target_session_id, version_no),
    CHECK (source_session_id<>target_session_id)
);
ALTER TABLE class_progression_path ADD COLUMN IF NOT EXISTS graph_version_id UUID REFERENCES progression_graph_version(id);
ALTER TABLE class_progression_path ADD COLUMN IF NOT EXISTS edge_type VARCHAR(16) NOT NULL DEFAULT 'DEFAULT';
ALTER TABLE class_progression_path ADD COLUMN IF NOT EXISTS display_order INT NOT NULL DEFAULT 1;
ALTER TABLE class_progression_path ADD COLUMN IF NOT EXISTS allow_skip BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE class_progression_path ADD COLUMN IF NOT EXISTS skip_reason VARCHAR(500);
INSERT INTO progression_graph_version(school_id,source_session_id,target_session_id,version_no,status)
SELECT DISTINCT school_id,source_session_id,target_session_id,1,'DRAFT'
  FROM class_progression_path
ON CONFLICT DO NOTHING;
UPDATE class_progression_path p
   SET graph_version_id=g.id
  FROM progression_graph_version g
 WHERE g.school_id=p.school_id AND g.source_session_id=p.source_session_id
   AND g.target_session_id=p.target_session_id AND p.graph_version_id IS NULL;
ALTER TABLE class_progression_path
    DROP CONSTRAINT IF EXISTS class_progression_path_school_id_source_session_id_source_class_id_target_session_id_key;
CREATE UNIQUE INDEX IF NOT EXISTS ux_progression_graph_edge
    ON class_progression_path(school_id,graph_version_id,source_class_id,target_class_id)
    WHERE active AND target_class_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS promotion_rule_set (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    version_no INT NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
    conditions JSONB NOT NULL DEFAULT '{}'::jsonb,
    published_at TIMESTAMPTZ,
    published_by UUID REFERENCES app_user(id),
    UNIQUE (school_id, academic_session_id, version_no)
);
ALTER TABLE promotion_rule ADD COLUMN IF NOT EXISTS rule_set_id UUID REFERENCES promotion_rule_set(id);

CREATE TABLE IF NOT EXISTS academic_grade_packet_transition (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    packet_id UUID NOT NULL REFERENCES academic_grade_packet(id) ON DELETE CASCADE,
    from_status VARCHAR(16),
    to_status VARCHAR(16) NOT NULL,
    reason VARCHAR(500),
    actor_user_id UUID REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_grade_packet_transition_packet
    ON academic_grade_packet_transition(school_id,packet_id,created_at);

ALTER TABLE academic_assessment ADD COLUMN IF NOT EXISTS component_type VARCHAR(24) NOT NULL DEFAULT 'SEQUENCE';
ALTER TABLE academic_assessment ADD COLUMN IF NOT EXISTS lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE academic_assessment ADD COLUMN IF NOT EXISTS curriculum_subject_id UUID REFERENCES academic_curriculum_subject(id);
ALTER TABLE academic_grade_packet ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(120);

ALTER TABLE document_template ADD COLUMN IF NOT EXISTS template_family VARCHAR(32) NOT NULL DEFAULT 'GENERIC';
ALTER TABLE document_template ADD COLUMN IF NOT EXISTS product VARCHAR(16) NOT NULL DEFAULT 'GENERIC';
ALTER TABLE document_template ADD COLUMN IF NOT EXISTS subsystem VARCHAR(8);
ALTER TABLE document_template ADD COLUMN IF NOT EXISTS status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED';
ALTER TABLE document_template ADD COLUMN IF NOT EXISTS effective_from DATE;
ALTER TABLE document_template ADD COLUMN IF NOT EXISTS effective_to DATE;
CREATE INDEX IF NOT EXISTS idx_document_template_selection
    ON document_template(school_id,template_family,product,subsystem,locale,status,template_version DESC);

CREATE TABLE IF NOT EXISTS journey_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    event_type VARCHAR(32) NOT NULL,
    academic_session_id UUID REFERENCES academic_session(id),
    source_type VARCHAR(40),
    source_id UUID,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    visibility VARCHAR(16) NOT NULL DEFAULT 'INTERNAL'
        CHECK (visibility IN ('INTERNAL','PARENT')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_journey_event_student
    ON journey_event(school_id,student_id,created_at);
