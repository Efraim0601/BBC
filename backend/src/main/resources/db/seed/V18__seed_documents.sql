-- ============================================================================
--  V18 (seed) — demo documents & orientation for a few students.
--  Applied only with the `demo` Spring profile (flyway location db/seed).
--  Permission grants for the `documents` module are handled by V17 (all profiles).
-- ============================================================================

-- Document register for demo students (metadata only — file_ref is a filing ref/URL).
INSERT INTO student_document
 (school_id, student_id, kind, title, note, file_ref) VALUES
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000001','birth_cert','Acte de naissance','Original déposé au secrétariat','ARCH-2024/0142'),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000001','photo','Photo d''identité',NULL,'https://files.bbc-sms.local/students/1001/photo.jpg'),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000003','birth_cert','Acte de naissance','Copie certifiée conforme','ARCH-2024/0190'),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000003','prior_report','Bulletin 4ème (année précédente)','Reçu lors de l''inscription','ARCH-2024/0191');

-- One orientation decision for Junior BIYA (student 3) — fin de 3ème.
INSERT INTO orientation_decision
 (school_id, student_id, academic_year, stage, recommendation, decision, council_date) VALUES
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000003','2024-2025','Orientation 3ème','Profil scientifique — bons résultats en mathématiques et SVT.','Orienté en 2nde C (série scientifique).','2025-06-28');
