-- ============================================================================
--  V26 — Convert the `subsystem` columns from CHAR(2) to VARCHAR.
--        V22 (app_user_parcours) and V24 (subject) introduced `subsystem` as
--        CHAR(2). The JPA entity `Subject` maps it as a plain String, which
--        Hibernate (ddl-auto: validate) expects to be VARCHAR — so a CHAR(2)
--        column makes schema validation fail and the backend refuses to start.
--        Same fix (and same reasoning) as V3 "char to varchar". The CHECK
--        constraints keep enforcing the 'FR' / 'EN' domain.
-- ============================================================================

ALTER TABLE subject           ALTER COLUMN subsystem TYPE VARCHAR(2);
ALTER TABLE app_user_parcours ALTER COLUMN subsystem TYPE VARCHAR(2);
