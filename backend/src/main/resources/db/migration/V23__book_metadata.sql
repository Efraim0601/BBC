-- ============================================================================
--  V23 — School-textbook (manuels scolaires) metadata on class book lists
--        Books (kind = 'books') become proper textbooks: linked to a subject,
--        with author/edition and a mandatory/optional flag. Parents already see
--        the PUBLISHED book list of their child's class — these fields enrich it.
--        Supplies (kind = 'supplies') leave these columns NULL.
-- ============================================================================

ALTER TABLE class_resource_item ADD COLUMN IF NOT EXISTS subject_code VARCHAR(16);
ALTER TABLE class_resource_item ADD COLUMN IF NOT EXISTS author       VARCHAR(120);
ALTER TABLE class_resource_item ADD COLUMN IF NOT EXISTS mandatory    BOOLEAN;
