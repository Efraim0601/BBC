-- Primary competence-based report cards.
--
-- A grid describes one primary level (I = SIL/CP or Class 1-2, II = CE1/CE2
-- or Class 3-4, III = CM1/CM2 or Class 5-6) for one subsystem: the national
-- competences, their sub-competences (taught as subjects) and the points of
-- each evaluation type (oral, written, practical, attitude).  Both classes of
-- a level therefore share one bulletin.  Grids are versioned: editing a grid
-- publishes a new version and classes keep the version they were set up with.
CREATE TABLE primary_competence_grid (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    subsystem CHAR(2) NOT NULL CHECK (subsystem IN ('FR', 'EN')),
    level_band SMALLINT NOT NULL CHECK (level_band BETWEEN 1 AND 3),
    version INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    UNIQUE (school_id, subsystem, level_band, version)
);

CREATE TABLE primary_competence_grid_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    grid_id UUID NOT NULL REFERENCES primary_competence_grid(id) ON DELETE CASCADE,
    competence_code VARCHAR(8) NOT NULL,
    competence_label JSONB NOT NULL DEFAULT '{}'::jsonb,
    code VARCHAR(8) NOT NULL,
    label JSONB NOT NULL DEFAULT '{}'::jsonb,
    points NUMERIC(6,2) NOT NULL CHECK (points > 0),
    oral_points NUMERIC(6,2) CHECK (oral_points IS NULL OR oral_points > 0),
    written_points NUMERIC(6,2) CHECK (written_points IS NULL OR written_points > 0),
    practical_points NUMERIC(6,2) CHECK (practical_points IS NULL OR practical_points > 0),
    attitude_points NUMERIC(6,2) CHECK (attitude_points IS NULL OR attitude_points > 0),
    -- Items sharing an alternative group are mutually exclusive per pupil
    -- (6A1 sport for able pupils / 6A2 for physically challenged pupils).
    alternative_group VARCHAR(8),
    counts_in_total BOOLEAN NOT NULL DEFAULT true,
    display_order INT NOT NULL,
    UNIQUE (grid_id, code),
    CHECK (coalesce(oral_points, 0) + coalesce(written_points, 0)
         + coalesce(practical_points, 0) + coalesce(attitude_points, 0) = points)
);

-- Which grid version a class uses in a session.  A class with a row here is
-- graded by competence: its curriculum and sequence evaluations are generated
-- from the grid.
CREATE TABLE primary_competence_class (
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    academic_session_id UUID NOT NULL REFERENCES academic_session(id) ON DELETE CASCADE,
    class_id UUID NOT NULL REFERENCES school_class(id) ON DELETE CASCADE,
    grid_id UUID NOT NULL REFERENCES primary_competence_grid(id),
    applied_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    applied_by UUID,
    PRIMARY KEY (school_id, academic_session_id, class_id)
);
