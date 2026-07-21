-- School profile.
--
-- The school's identity was hardcoded in the frontend in at least five places: the
-- bulletin header ("République du Cameroun · MINESEC", "Bayo Bilingual Complex",
-- "Maroua"), the payment receipt (same, plus a phone number and "Année 2025-2026"),
-- the parent portal's contact card (contact@bbc.cm, +237 6 99 00 00 00) and the
-- Settings → Général tab, which listed all of it as read-only literals.
--
-- These columns make it real, per tenant, and editable from Settings → Général.

ALTER TABLE school ADD COLUMN IF NOT EXISTS city        varchar(80);
ALTER TABLE school ADD COLUMN IF NOT EXISTS country     varchar(80)  NOT NULL DEFAULT 'Cameroun';
ALTER TABLE school ADD COLUMN IF NOT EXISTS address     varchar(200);
ALTER TABLE school ADD COLUMN IF NOT EXISTS phone       varchar(40);
ALTER TABLE school ADD COLUMN IF NOT EXISTS email       varchar(160);
ALTER TABLE school ADD COLUMN IF NOT EXISTS website     varchar(160);
ALTER TABLE school ADD COLUMN IF NOT EXISTS currency    varchar(8)   NOT NULL DEFAULT 'FCFA';
-- Supervising authority printed on bulletins/PVs, e.g. "République du Cameroun · MINESEC".
ALTER TABLE school ADD COLUMN IF NOT EXISTS authority   varchar(160);

-- Backfill the existing tenant(s) with what the UI used to hardcode, so nothing
-- regresses visually on upgrade.
UPDATE school
   SET city      = COALESCE(city, 'Maroua'),
       country   = COALESCE(country, 'Cameroun'),
       authority = COALESCE(authority, 'République du Cameroun · MINESEC')
 WHERE city IS NULL OR authority IS NULL;
