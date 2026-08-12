-- BAY-10/BAY-37 compatibility bridge.
--
-- V100 added revocation metadata to the parent-visibility contract except for
-- the actor column.  The publication lifecycle already persists that actor
-- when superseding a visible bulletin, so production-shaped databases need
-- the missing nullable foreign-key column before publication can complete.

ALTER TABLE bulletin_parent_visibility
    ADD COLUMN IF NOT EXISTS revoked_by UUID REFERENCES app_user(id);
