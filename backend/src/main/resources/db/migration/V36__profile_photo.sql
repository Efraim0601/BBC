-- ============================================================================
--  V36 — Photo de profil des élèves et du personnel.
--
--  Les images sont stockées EN BASE (bytea) et non sur un volume de fichiers :
--  la sauvegarde de l'établissement est un pg_dump, un répertoire monté n'y
--  serait pas et disparaîtrait au premier `make reset`. Les photos sont
--  redimensionnées et compressées par le navigateur avant l'envoi (512 px,
--  JPEG), soit quelques dizaines de kilo-octets par personne.
--
--  Une seule photo par personne : la clé primaire est (owner_type, owner_id),
--  un nouvel envoi remplace l'ancienne.
-- ============================================================================
CREATE TABLE profile_photo (
    owner_type   VARCHAR(10) NOT NULL CHECK (owner_type IN ('student','employee')),
    owner_id     UUID        NOT NULL,
    school_id    UUID        NOT NULL REFERENCES school(id),
    content_type VARCHAR(40) NOT NULL,
    bytes        BYTEA       NOT NULL,
    byte_size    INTEGER     NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (owner_type, owner_id)
);

-- Le nettoyage se fait par établissement (suppression d'un tenant, purge).
CREATE INDEX idx_profile_photo_school ON profile_photo (school_id);
