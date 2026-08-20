-- Teacher academic access control: scoped, audited, time-bounded exceptions.
-- The canonical teaching sources remain class_teacher_assignment,
-- academic_curriculum_subject, and academic_class_subject_teacher.

CREATE TABLE IF NOT EXISTS academic_access_delegation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES employee(id) ON DELETE RESTRICT,
    class_id UUID NOT NULL REFERENCES school_class(id) ON DELETE RESTRICT,
    subject_id UUID REFERENCES subject(id) ON DELETE RESTRICT,
    subject_code VARCHAR(32),
    capability_code VARCHAR(64) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','ACTIVE','REVOKED','EXPIRED','REJECTED')),
    reason VARCHAR(1000) NOT NULL,
    requested_by UUID NOT NULL REFERENCES app_user(id),
    approved_by UUID REFERENCES app_user(id),
    approved_at TIMESTAMPTZ,
    revoked_by UUID REFERENCES app_user(id),
    revoked_at TIMESTAMPTZ,
    revocation_reason VARCHAR(1000),
    source VARCHAR(16) NOT NULL DEFAULT 'MANUAL'
        CHECK (source IN ('MANUAL','SUBSTITUTION','IMPORT')),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CHECK ((subject_id IS NULL AND subject_code IS NULL)
        OR (subject_id IS NOT NULL AND subject_code IS NOT NULL)),
    CHECK ((status = 'ACTIVE' AND approved_by IS NOT NULL AND approved_at IS NOT NULL)
        OR status <> 'ACTIVE'),
    CHECK ((status = 'REVOKED' AND revoked_by IS NOT NULL AND revoked_at IS NOT NULL)
        OR status <> 'REVOKED')
);

CREATE INDEX IF NOT EXISTS idx_academic_access_delegation_scope
    ON academic_access_delegation(
        school_id, academic_session_id, employee_id, class_id,
        subject_code, capability_code, status, effective_from, effective_to);

CREATE INDEX IF NOT EXISTS idx_academic_access_delegation_expiry
    ON academic_access_delegation(school_id, status, effective_to)
    WHERE status = 'ACTIVE';

-- Do not allow equivalent active grants to overlap. Historical and revoked
-- grants remain immutable/auditable and may coexist with a new grant.
CREATE OR REPLACE FUNCTION reject_overlapping_academic_access_delegation()
RETURNS trigger AS $$
BEGIN
    IF NEW.status = 'ACTIVE' AND EXISTS (
        SELECT 1
          FROM academic_access_delegation x
         WHERE x.school_id = NEW.school_id
           AND x.academic_session_id = NEW.academic_session_id
           AND x.employee_id = NEW.employee_id
           AND x.class_id = NEW.class_id
           AND COALESCE(x.subject_code, '') = COALESCE(NEW.subject_code, '')
           AND x.capability_code = NEW.capability_code
           AND x.status = 'ACTIVE'
           AND x.id <> NEW.id
           AND daterange(x.effective_from, COALESCE(x.effective_to + 1, 'infinity'::date), '[)')
               && daterange(NEW.effective_from, COALESCE(NEW.effective_to + 1, 'infinity'::date), '[)')
    ) THEN
        RAISE EXCEPTION 'Equivalent active academic access delegations may not overlap';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_academic_access_delegation_overlap
    ON academic_access_delegation;
CREATE TRIGGER trg_academic_access_delegation_overlap
    BEFORE INSERT OR UPDATE OF employee_id, class_id, subject_code, capability_code,
        effective_from, effective_to, status
    ON academic_access_delegation
    FOR EACH ROW EXECUTE FUNCTION reject_overlapping_academic_access_delegation();

-- These action grants are the coarse management gate. They do not grant any
-- student/subject data by themselves; the AcademicAccessPolicyService still
-- resolves the requested class, enrollment, assignment, subject and date.
INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT s.id, r.code, a.code,
       CASE
           WHEN r.code IN ('principal') THEN true
           WHEN r.code = 'prefect' AND a.code IN (
               'ACADEMIC_CLASS_RESULTS_VIEW', 'ACADEMIC_REPORT_CARD_VIEW',
               'ACADEMIC_GRADE_PACKET_REVIEW', 'ACADEMIC_REPORT_CARD_VALIDATE',
               'ACADEMIC_REPORT_CARD_PUBLISH', 'ACADEMIC_ACCESS_AUDIT_VIEW') THEN true
           ELSE false
       END
  FROM school s
  JOIN role r ON r.code IN ('principal','prefect','teacher','form_teacher')
 CROSS JOIN (VALUES
    ('ACADEMIC_CLASS_RESULTS_VIEW'),
    ('ACADEMIC_REPORT_CARD_VIEW'),
    ('ACADEMIC_SUBJECT_GRADE_EDIT'),
    ('ACADEMIC_ASSESSMENT_MANAGE'),
    ('ACADEMIC_GRADE_PACKET_REVIEW'),
    ('ACADEMIC_REPORT_CARD_VALIDATE'),
    ('ACADEMIC_REPORT_CARD_PUBLISH'),
    ('ACADEMIC_ACCESS_DELEGATE'),
    ('ACADEMIC_ACCESS_AUDIT_VIEW'),
    ('ACADEMIC_COUNCIL_INPUT_EDIT')
 ) a(code)
ON CONFLICT (school_id, role_code, action_code)
DO UPDATE SET allowed = EXCLUDED.allowed;
