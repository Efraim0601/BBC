-- Keep every source row in a dry-run, including rows rejected for a
-- duplicate external key.  FamilyImportService performs the semantic
-- external-key validation; the database only needs one stored row per input
-- row within a job so invalid rows can be reported and skipped idempotently.
ALTER TABLE family_import_row
    DROP CONSTRAINT IF EXISTS family_import_row_school_id_job_id_external_key_key;

ALTER TABLE family_import_row
    ADD CONSTRAINT family_import_row_school_id_job_id_row_number_key
    UNIQUE (school_id, job_id, row_number);
