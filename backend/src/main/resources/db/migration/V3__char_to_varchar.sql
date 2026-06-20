-- Align CHAR columns with their JPA String mappings (Hibernate schema-validation
-- expects VARCHAR, not bpchar/CHAR). Values are fixed-length codes, so the
-- CHAR -> VARCHAR conversion is lossless. CHECK constraints are preserved.
ALTER TABLE employee     ALTER COLUMN sex       TYPE VARCHAR(1);
ALTER TABLE student      ALTER COLUMN sex       TYPE VARCHAR(1);
ALTER TABLE student      ALTER COLUMN subsystem TYPE VARCHAR(2);
ALTER TABLE section      ALTER COLUMN subsystem TYPE VARCHAR(2);
ALTER TABLE school_class ALTER COLUMN subsystem TYPE VARCHAR(2);
ALTER TABLE fee_config   ALTER COLUMN subsystem TYPE VARCHAR(2);
