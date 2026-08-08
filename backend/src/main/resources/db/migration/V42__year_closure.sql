-- ============================================================================
--  V42 — Clôture de l'année scolaire
--
--  Le passage de classe (V41) déplace les élèves ; il ne vide pas les compteurs.
--  Trois tables seulement débordent d'une année sur l'autre, faute de dimension
--  temporelle : `grade`, `bulletin_validation` et `student_fee`. La clôture les
--  recopie dans une archive datée puis remet les tables vives à zéro, et bascule
--  l'année courante de l'établissement.
--
--  Les archives ne portent PAS de clé étrangère vers student : elles doivent
--  survivre à la radiation d'un élève, c'est tout leur intérêt.
-- ============================================================================

-- ---- Notes archivées -------------------------------------------------------
CREATE TABLE grade_archive (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id     UUID NOT NULL REFERENCES school(id),
    academic_year VARCHAR(16) NOT NULL,
    student_id    UUID NOT NULL,
    class_name    VARCHAR(80),              -- classe fréquentée cette année-là
    subject_code  VARCHAR(8) NOT NULL,
    sequence      INT NOT NULL,
    mark          NUMERIC(4,2) NOT NULL,
    archived_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, academic_year, student_id, subject_code, sequence)
);
CREATE INDEX idx_grade_archive_student ON grade_archive (school_id, student_id, academic_year);

-- ---- Bulletins validés archivés --------------------------------------------
CREATE TABLE bulletin_validation_archive (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id            UUID NOT NULL REFERENCES school(id),
    academic_year        VARCHAR(16) NOT NULL,
    student_id           UUID NOT NULL,
    sequence             INT NOT NULL,
    validated            BOOLEAN NOT NULL DEFAULT false,
    general_appreciation TEXT,
    validated_by         UUID,
    validated_at         TIMESTAMPTZ,
    archived_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, academic_year, student_id, sequence)
);

-- ---- Scolarités archivées --------------------------------------------------
-- L'historique des encaissements reste dans `payment`, daté : seul l'état de
-- compte agrégé de l'année a besoin d'être figé ici avant remise à zéro.
CREATE TABLE student_fee_archive (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id     UUID NOT NULL REFERENCES school(id),
    academic_year VARCHAR(16) NOT NULL,
    student_id    UUID NOT NULL,
    class_name    VARCHAR(80),
    total         BIGINT NOT NULL,
    paid          BIGINT NOT NULL,
    balance       BIGINT NOT NULL,
    tranches_paid INT NOT NULL DEFAULT 0,
    status        VARCHAR(8) NOT NULL,
    archived_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, academic_year, student_id)
);
CREATE INDEX idx_fee_archive_student ON student_fee_archive (school_id, student_id, academic_year);

-- ---- Journal des clôtures ---------------------------------------------------
-- Une année ne se clôture qu'une fois : la contrainte d'unicité est le garde-fou
-- contre un second passage qui archiverait la nouvelle année par-dessus l'ancienne.
CREATE TABLE year_closure (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id            UUID NOT NULL REFERENCES school(id),
    academic_year        VARCHAR(16) NOT NULL,
    next_academic_year   VARCHAR(16) NOT NULL,
    grades_archived      INT NOT NULL DEFAULT 0,
    validations_archived INT NOT NULL DEFAULT 0,
    fees_archived        INT NOT NULL DEFAULT 0,
    fees_created         INT NOT NULL DEFAULT 0,
    students_active      INT NOT NULL DEFAULT 0,
    students_pending     INT NOT NULL DEFAULT 0,
    made_current         BOOLEAN NOT NULL DEFAULT false,
    closed_by            UUID REFERENCES app_user(id),
    closed_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, academic_year)
);
