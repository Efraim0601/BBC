-- ============================================================================
--  V46 — Ressources partagées (bibliothèque documentaire de l'établissement)
--
--  L'admin principal et les administrateurs de section déposent des documents
--  — circulaires, progressions, fiches, formulaires, images — et choisissent
--  QUI les voit. Trois destinataires, et un périmètre :
--
--      audience = all      · tout le monde (personnel + parents)
--                 staff    · le personnel uniquement
--                 parents  · les parents uniquement
--
--      section  = NULL     · toute l'école — réservé à l'admin principal
--                 <cycle>  · maternelle | primary | secondary
--
--  Le binaire ne vit PAS ici, contrairement aux photos de profil : un PDF de
--  plusieurs méga-octets par circulaire ferait enfler le pg_dump jusqu'à le
--  rendre inexploitable. Les fichiers sont dans MinIO, la base ne garde que la
--  clé d'objet — et reste seule maîtresse de qui a le droit de la suivre : le
--  bucket est privé, aucun octet ne sort sans passer par l'API.
--
--  `published` sépare le brouillon du publié : on dépose, on relit, on publie.
--  Tant qu'une ressource n'est pas publiée, elle n'existe que pour ceux qui
--  ont le droit d'écrire.
-- ============================================================================

CREATE TABLE shared_resource (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id        UUID NOT NULL REFERENCES school(id),
    title            VARCHAR(200) NOT NULL,
    description      TEXT,
    -- circular | pedagogy | admin | form | other — sert au tri et à la pastille
    category         VARCHAR(16) NOT NULL DEFAULT 'other',
    audience         VARCHAR(8)  NOT NULL CHECK (audience IN ('all','staff','parents')),
    -- NULL = toute l'école ; sinon le cycle destinataire
    section          VARCHAR(16) CHECK (section IN ('maternelle','primary','secondary')),
    -- Clé de l'objet dans le bucket MinIO (schools/<school>/library/<uuid>.<ext>)
    object_key       VARCHAR(300) NOT NULL,
    file_name        VARCHAR(255) NOT NULL,
    content_type     VARCHAR(160) NOT NULL,
    byte_size        BIGINT NOT NULL,
    published        BOOLEAN NOT NULL DEFAULT false,
    published_at     TIMESTAMPTZ,
    uploaded_by      UUID REFERENCES app_user(id),
    -- Nom figé de l'auteur : le compte peut disparaître, la circulaire reste signée.
    uploaded_by_name VARCHAR(120),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Toutes les lectures partent de l'établissement et retiennent d'abord le
-- périmètre (section), puis le destinataire ; les listes sont antéchronologiques.
CREATE INDEX idx_shared_resource_scope ON shared_resource (school_id, section, audience);
CREATE INDEX idx_shared_resource_recent ON shared_resource (school_id, created_at DESC);

-- ---- Module « library » dans la matrice des rôles --------------------------
-- Écriture pour ceux qui publient — l'admin principal et ses relais de cycle ;
-- lecture pour ceux qui consultent. Les parents n'y figurent pas : ils passent
-- par le portail parent (/api/parent/resources), jamais par la matrice.
INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT s.id, r.code, 'library', 'write'
  FROM school s
 CROSS JOIN (VALUES ('principal'), ('admin_maternelle'), ('admin_primary'), ('admin_secondary')) AS r(code)
ON CONFLICT (school_id, role_code, module) DO NOTHING;

INSERT INTO permission_grant (school_id, role_code, module, level)
SELECT s.id, r.code, 'library', 'read'
  FROM school s
 CROSS JOIN (VALUES ('prefect'), ('form_teacher'), ('teacher'), ('econome')) AS r(code)
ON CONFLICT (school_id, role_code, module) DO NOTHING;
