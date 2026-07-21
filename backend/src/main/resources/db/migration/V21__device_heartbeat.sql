-- Fingerprint-reader heartbeat.
--
-- The UI showed a hardcoded green "En ligne / Online" badge on both the Attendance
-- page and Settings → Général: nothing ever polled the reader, so an unplugged device
-- still read as online. There was no column to poll either.
--
-- last_seen_at is stamped on every device check-in, which makes "online" a fact
-- (seen recently) rather than a decoration. location/model replace the literals
-- "Entrée principale" / "ZKTeco MultiBio 800" that were baked into the frontend.

ALTER TABLE device ADD COLUMN IF NOT EXISTS last_seen_at timestamptz;
ALTER TABLE device ADD COLUMN IF NOT EXISTS location     varchar(120);
ALTER TABLE device ADD COLUMN IF NOT EXISTS model        varchar(80);

UPDATE device
   SET location = COALESCE(location, 'Entrée principale'),
       model    = COALESCE(model, 'ZKTeco MultiBio 800')
 WHERE location IS NULL OR model IS NULL;
