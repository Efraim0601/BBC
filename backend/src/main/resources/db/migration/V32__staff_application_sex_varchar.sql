-- Align staff_application.sex with JPA String mapping (same fix as V3 for
-- employee/student). Hibernate ddl-auto=validate expects VARCHAR, not bpchar.
ALTER TABLE staff_application ALTER COLUMN sex TYPE VARCHAR(1);
