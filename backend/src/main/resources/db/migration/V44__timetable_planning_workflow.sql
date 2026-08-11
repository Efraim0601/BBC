-- Session-aware timetable plans, configurable periods, and strict conflict guards.
CREATE TABLE timetable_period (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    slot_idx INT NOT NULL CHECK (slot_idx BETWEEN 0 AND 15),
    label VARCHAR(40) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    UNIQUE (school_id, slot_idx),
    CHECK (end_time > start_time)
);

INSERT INTO timetable_period(school_id,slot_idx,label,start_time,end_time)
SELECT s.id, p.slot_idx, p.label, p.start_time, p.end_time
FROM school s CROSS JOIN (VALUES
 (0,'P1','07:30'::time,'08:25'::time),(1,'P2','08:30'::time,'09:25'::time),
 (2,'P3','09:30'::time,'10:25'::time),(3,'P4','10:30'::time,'11:25'::time),
 (4,'P5','11:30'::time,'12:25'::time),(5,'P6','12:30'::time,'13:25'::time),
 (6,'P7','13:30'::time,'14:25'::time),(7,'P8','14:30'::time,'15:25'::time),
 (8,'P9','15:30'::time,'16:25'::time)
) p(slot_idx,label,start_time,end_time)
ON CONFLICT DO NOTHING;

CREATE TABLE timetable_class_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    class_id UUID NOT NULL REFERENCES school_class(id) ON DELETE CASCADE,
    model VARCHAR(16) NOT NULL CHECK (model IN ('HOMEROOM','DEPARTMENTAL')),
    homeroom_teacher_id UUID REFERENCES employee(id),
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PUBLISHED')),
    published_at TIMESTAMPTZ,
    published_by UUID REFERENCES app_user(id),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, academic_session_id, class_id),
    CHECK (model <> 'HOMEROOM' OR homeroom_teacher_id IS NOT NULL OR status='DRAFT')
);

INSERT INTO timetable_class_config(school_id,academic_session_id,class_id,model)
SELECT c.school_id,s.id,c.id,CASE WHEN lower(c.level)='secondary' THEN 'DEPARTMENTAL' ELSE 'HOMEROOM' END
FROM school_class c JOIN academic_session s ON s.school_id=c.school_id AND s.is_current
ON CONFLICT DO NOTHING;

ALTER TABLE timetable_slot ADD COLUMN academic_session_id UUID REFERENCES academic_session(id) ON DELETE CASCADE;
UPDATE timetable_slot t SET academic_session_id=(
  SELECT s.id FROM academic_session s WHERE s.school_id=t.school_id ORDER BY s.is_current DESC,s.start_date DESC LIMIT 1
) WHERE academic_session_id IS NULL;

ALTER TABLE timetable_slot DROP CONSTRAINT IF EXISTS timetable_slot_school_id_class_id_day_idx_slot_idx_key;
CREATE UNIQUE INDEX ux_timetable_slot_session_cell
 ON timetable_slot(school_id,academic_session_id,class_id,day_idx,slot_idx);
CREATE UNIQUE INDEX ux_timetable_teacher_no_double_booking
 ON timetable_slot(school_id,academic_session_id,day_idx,slot_idx,teacher_id)
 WHERE teacher_id IS NOT NULL;
CREATE UNIQUE INDEX ux_timetable_room_no_double_booking
 ON timetable_slot(school_id,academic_session_id,day_idx,slot_idx,lower(room))
 WHERE room IS NOT NULL AND btrim(room)<>'';
CREATE INDEX idx_timetable_teacher_schedule
 ON timetable_slot(school_id,academic_session_id,teacher_id,day_idx,slot_idx);
