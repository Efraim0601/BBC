-- BAY-10/BAY-11: close the last write-path and transition invariants after V64.
-- Applied migrations remain immutable; this migration only tightens the live
-- schema and preserves all historical rows.

ALTER TABLE academic_class_subject_teacher
    DROP CONSTRAINT IF EXISTS academic_class_subject_teacher_source_check;
ALTER TABLE academic_class_subject_teacher
    ADD CONSTRAINT academic_class_subject_teacher_source_check
    CHECK (source IN ('TIMETABLE','HOMEROOM','MANUAL','ACADEMIC_SETUP'));

-- Multiple historical assignments are valid; overlapping active RESPONSIBLE
-- ranges are not.  The V62 trigger was intentionally broad while the data was
-- being audited, so replace it with the effective-date invariant now.
CREATE OR REPLACE FUNCTION reject_duplicate_responsible_assignment() RETURNS trigger AS $$
BEGIN
    IF NEW.active AND NEW.role='RESPONSIBLE' AND EXISTS (
        SELECT 1 FROM academic_class_subject_teacher x
         WHERE x.school_id=NEW.school_id AND x.academic_session_id=NEW.academic_session_id
           AND x.class_id=NEW.class_id AND x.subject_id=NEW.subject_id
           AND x.role='RESPONSIBLE' AND x.active AND x.id<>NEW.id
           AND COALESCE(x.effective_from, DATE '-infinity') <= COALESCE(NEW.effective_to, DATE 'infinity')
           AND COALESCE(NEW.effective_from, DATE '-infinity') <= COALESCE(x.effective_to, DATE 'infinity')
    ) THEN
        RAISE EXCEPTION 'Only one active RESPONSIBLE assignment may cover an effective date';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- A student may have at most one current or planned enrollment in a session.
-- This also makes concurrent promotion commits fail safely instead of creating
-- two future enrollments for the same student.
CREATE UNIQUE INDEX IF NOT EXISTS uq_student_enrollment_active_or_planned_session
    ON student_enrollment(school_id, student_id, academic_session_id)
    WHERE status IN ('ACTIVE','PLANNED');

-- Keep promotion review and override authority distinct in the action matrix.
INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT pg.school_id, pg.role_code, action.code, true
FROM permission_grant pg
CROSS JOIN (VALUES
    ('PROMOTION_CONFIGURE'),
    ('PROMOTION_RECOMMEND'),
    ('PROMOTION_OVERRIDE'),
    ('PROMOTION_CORRECT')
) AS action(code)
WHERE pg.module='journey' AND pg.level='write'
ON CONFLICT (school_id, role_code, action_code) DO NOTHING;

-- Record currently visible disagreement between the legacy timetable class
-- config and the canonical academic setup instead of silently choosing one.
INSERT INTO assignment_discrepancy
    (school_id, academic_session_id, class_id, legacy_source,
     legacy_teacher_id, canonical_teacher_id, details)
SELECT cfg.school_id, cfg.academic_session_id, cfg.class_id,
       'TIMETABLE_CLASS_CONFIG', cfg.homeroom_teacher_id, a.employee_id,
       jsonb_build_object('timetableConfigId', cfg.class_id,
                          'reason', 'LEGACY_CONFIG_DIFFERS_FROM_CANONICAL')
  FROM timetable_class_config cfg
  JOIN class_teacher_assignment a
    ON a.school_id=cfg.school_id
   AND a.academic_session_id=cfg.academic_session_id
   AND a.class_id=cfg.class_id
   AND a.role='HOMEROOM'
   AND a.status='ACTIVE'
 WHERE cfg.homeroom_teacher_id IS NOT NULL
   AND cfg.homeroom_teacher_id<>a.employee_id
   AND NOT EXISTS (
       SELECT 1 FROM assignment_discrepancy d
        WHERE d.school_id=cfg.school_id
          AND d.academic_session_id=cfg.academic_session_id
          AND d.class_id=cfg.class_id
          AND d.legacy_source='TIMETABLE_CLASS_CONFIG'
          AND d.status='OPEN'
   );
