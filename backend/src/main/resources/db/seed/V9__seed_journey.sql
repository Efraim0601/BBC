-- ============================================================================
--  V9 (seed) — demo longitudinal parcours for a few students.
--  Applied only with the `demo` Spring profile (flyway location db/seed).
--  Permission grants for the `journey` module are handled by V8 (all profiles).
-- ============================================================================

-- Cédric FOTSO (BBC-1001) — currently 4ème: two completed years + current.
INSERT INTO journey_entry
 (school_id, student_id, academic_year, class_name, level, subsystem, result, general_average, rank, class_size, decision) VALUES
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000001','2022-2023','6ème','secondary','FR','promoted',12.40,18,42,'Admis en classe supérieure'),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000001','2023-2024','5ème','secondary','FR','promoted',13.10,12,40,'Admis — Encouragements'),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000001','2024-2025','4ème','secondary','FR','in_progress',NULL,NULL,38,NULL);

-- Adèle NKENG (BBC-1002) — currently 5ème: a repeat then promotion.
INSERT INTO journey_entry
 (school_id, student_id, academic_year, class_name, level, subsystem, result, general_average, rank, class_size, decision) VALUES
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000002','2022-2023','6ème','secondary','FR','repeated',9.20,35,41,'Redouble la classe de 6ème'),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000002','2023-2024','6ème','secondary','FR','promoted',11.80,20,40,'Admis en classe supérieure'),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000002','2024-2025','5ème','secondary','FR','in_progress',NULL,NULL,39,NULL);

-- Estelle MANGA (BBC-1004) — primary, transferred in then promoted.
INSERT INTO journey_entry
 (school_id, student_id, academic_year, class_name, level, subsystem, result, general_average, rank, class_size, decision) VALUES
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000004','2023-2024','CE2','primary','FR','transferred_in',14.50,5,30,'Intégrée — bon niveau'),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000004','2024-2025','CM1','primary','FR','in_progress',NULL,NULL,28,NULL);
