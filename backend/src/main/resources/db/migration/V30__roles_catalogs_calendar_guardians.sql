-- ============================================================================
--  V30 — Custom-role support helpers + discipline catalogs + attendance
--        calendar (school start / holidays) + father/mother/guardian fields.
-- ============================================================================

-- ---- Attendance opening time + school holidays -----------------------------
ALTER TABLE school ADD COLUMN IF NOT EXISTS school_start_time VARCHAR(5) NOT NULL DEFAULT '07:30';
ALTER TABLE school ADD COLUMN IF NOT EXISTS school_end_time   VARCHAR(5) NOT NULL DEFAULT '17:00';

CREATE TABLE IF NOT EXISTS school_holiday (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id     UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    holiday_date  DATE NOT NULL,
    label         VARCHAR(120) NOT NULL,
    UNIQUE (school_id, holiday_date)
);
CREATE INDEX IF NOT EXISTS idx_school_holiday_school ON school_holiday (school_id, holiday_date);

-- ---- Discipline type / sanction catalogs (per school, editable) ------------
CREATE TABLE IF NOT EXISTS discipline_catalog (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    kind        VARCHAR(16) NOT NULL CHECK (kind IN ('type', 'sanction')),
    code        VARCHAR(40) NOT NULL,
    label_fr    VARCHAR(120) NOT NULL,
    label_en    VARCHAR(120) NOT NULL,
    sort_order  INT NOT NULL DEFAULT 0,
    active      BOOLEAN NOT NULL DEFAULT true,
    UNIQUE (school_id, kind, code)
);
CREATE INDEX IF NOT EXISTS idx_discipline_catalog_school
    ON discipline_catalog (school_id, kind, sort_order);

-- Seed default catalogs for every existing school (idempotent).
INSERT INTO discipline_catalog (school_id, kind, code, label_fr, label_en, sort_order)
SELECT s.id, v.kind, v.code, v.label_fr, v.label_en, v.sort_order
FROM school s
CROSS JOIN (VALUES
    ('type', 'Retard',   'Retard',   'Late',       1),
    ('type', 'Absence',  'Absence',  'Absence',    2),
    ('type', 'Conduite', 'Conduite', 'Conduct',    3),
    ('type', 'Tenue',    'Tenue',    'Dress code', 4),
    ('sanction', 'Avertissement verbal',  'Avertissement verbal',  'Verbal warning',     1),
    ('sanction', 'Avertissement écrit',   'Avertissement écrit',   'Written warning',    2),
    ('sanction', 'Convocation parent',    'Convocation parent',    'Parent summons',     3),
    ('sanction', 'Exclusion temporaire',  'Exclusion temporaire',  'Temporary exclusion',4),
    ('sanction', 'Conseil de discipline', 'Conseil de discipline', 'Disciplinary board', 5)
) AS v(kind, code, label_fr, label_en, sort_order)
ON CONFLICT (school_id, kind, code) DO NOTHING;

-- ---- Detailed parent / guardian contacts on the student card ---------------
ALTER TABLE student ADD COLUMN IF NOT EXISTS father_name      VARCHAR(120);
ALTER TABLE student ADD COLUMN IF NOT EXISTS father_phone     VARCHAR(40);
ALTER TABLE student ADD COLUMN IF NOT EXISTS father_email     VARCHAR(160);
ALTER TABLE student ADD COLUMN IF NOT EXISTS mother_name      VARCHAR(120);
ALTER TABLE student ADD COLUMN IF NOT EXISTS mother_phone     VARCHAR(40);
ALTER TABLE student ADD COLUMN IF NOT EXISTS mother_email     VARCHAR(160);
ALTER TABLE student ADD COLUMN IF NOT EXISTS guardian_name    VARCHAR(120);
ALTER TABLE student ADD COLUMN IF NOT EXISTS guardian_phone   VARCHAR(40);
ALTER TABLE student ADD COLUMN IF NOT EXISTS guardian_email   VARCHAR(160);
ALTER TABLE student ADD COLUMN IF NOT EXISTS guardian_relation VARCHAR(40);

-- Backfill father/mother from the legacy single parent fields when empty.
UPDATE student
   SET father_name  = COALESCE(father_name, parent_name),
       father_phone = COALESCE(father_phone, parent_phone)
 WHERE parent_name IS NOT NULL
   AND father_name IS NULL
   AND mother_name IS NULL
   AND guardian_name IS NULL;
