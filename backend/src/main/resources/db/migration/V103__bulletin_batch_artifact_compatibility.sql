-- BAY-38 compatibility bridge.
--
-- V76 created bulletin_batch_artifact for the original batch-export products,
-- while V102 adds the product-aware batch worker.  IF NOT EXISTS in V102
-- intentionally preserves the V76 table, so this migration evolves that
-- table without dropping existing artifact rows or changing their keys.

ALTER TABLE bulletin_batch_artifact
    ADD COLUMN IF NOT EXISTS item_id UUID
        REFERENCES bulletin_batch_item(id) ON DELETE CASCADE;

ALTER TABLE bulletin_batch_artifact
    ADD COLUMN IF NOT EXISTS storage_key VARCHAR(500);

-- Existing V76 rows use file_storage_key.  Keep that historical column and
-- mirror it into the runtime column used by the product-aware worker.
UPDATE bulletin_batch_artifact
SET storage_key = file_storage_key
WHERE storage_key IS NULL;

ALTER TABLE bulletin_batch_artifact
    ALTER COLUMN storage_key SET NOT NULL;

ALTER TABLE bulletin_batch_artifact
    ADD COLUMN IF NOT EXISTS metadata JSONB;

UPDATE bulletin_batch_artifact
SET metadata = '{}'::jsonb
WHERE metadata IS NULL;

ALTER TABLE bulletin_batch_artifact
    ALTER COLUMN metadata SET DEFAULT '{}'::jsonb,
    ALTER COLUMN metadata SET NOT NULL;

-- V76 only allowed its original artifact types.  Remove that check without
-- relying on Flyway's generated constraint name, then install the union of
-- historical and product-aware types.
DO $$
DECLARE
    constraint_row RECORD;
BEGIN
    FOR constraint_row IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'bulletin_batch_artifact'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%artifact_type%'
    LOOP
        EXECUTE format(
            'ALTER TABLE bulletin_batch_artifact DROP CONSTRAINT %I',
            constraint_row.conname
        );
    END LOOP;
END $$;

ALTER TABLE bulletin_batch_artifact
    ADD CONSTRAINT bulletin_batch_artifact_type_check
    CHECK (artifact_type IN (
        'HONOR_CERTIFICATE',
        'CLASS_STATISTICS',
        'PV_REGISTER',
        'MANIFEST',
        'ARCHIVE',
        'DIAGNOSTIC',
        'DOCUMENT'
    ));

