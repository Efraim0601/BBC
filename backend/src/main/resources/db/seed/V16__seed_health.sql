-- ============================================================================
--  V16 (seed) — demo santé & vie scolaire for a few students.
--  Applied only with the `demo` Spring profile (flyway location db/seed).
--  Permission grants for the `health` module are handled by V15 (all profiles).
-- ============================================================================

-- Medical records (one per student).
INSERT INTO health_record
 (school_id, student_id, blood_group, allergies, conditions, vaccinations, doctor_name, doctor_phone, height_cm, weight_kg) VALUES
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000001','O+','Arachides','Asthme léger','BCG, DTC, ROR à jour','Dr. Mbarga Jean','+237 699 11 22 33',152,44),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000004','A+','Aucune connue',NULL,'Vaccins à jour (carnet 2024)','Dr. Essomba Marie','+237 677 44 55 66',128,27)
ON CONFLICT (school_id, student_id) DO NOTHING;

-- Infirmary visits.
INSERT INTO infirmary_visit
 (school_id, student_id, visit_date, reason, treatment) VALUES
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000001','2024-10-14','Maux de tête','Repos à l''infirmerie, paracétamol, parents informés'),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000001','2025-01-22','Légère entorse cheville (sport)','Glace et bandage, dispense d''EPS 1 semaine'),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000004','2024-11-05','Fièvre','Température prise, parents appelés pour récupération');

-- Extracurricular activities.
INSERT INTO student_activity
 (school_id, student_id, name, category, role, season) VALUES
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000001','Football','sport','Milieu de terrain','2024-2025'),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000001','Club Sciences','club','Membre','2024-2025'),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000004','Chorale','art','Soprano','2024-2025'),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000004','Club Lecture','club','Membre','2024-2025');
