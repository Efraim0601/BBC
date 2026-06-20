-- ============================================================================
--  V5 (seed) — demo data for students, fees, grades, parent portal & events.
--  Applied only with the `demo` Spring profile (flyway location db/seed).
-- ============================================================================
-- Students (deterministic ids). class_id left null; class_name is the working key.
INSERT INTO student (id, school_id, matricule, first_name, last_name, sex, dob, class_name, subsystem, level, parent_name, parent_phone, photo_hue) VALUES
 ('cccccccc-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111','BBC-1001','Cédric','FOTSO','M','2012-03-14','4ème','FR','secondary','MBARGA Jean','+237 670 11 22 33', 200),
 ('cccccccc-0000-0000-0000-000000000002','11111111-1111-1111-1111-111111111111','BBC-1002','Adèle','NKENG','F','2013-07-02','5ème','FR','secondary','MBARGA Jean','+237 670 11 22 33', 320),
 ('cccccccc-0000-0000-0000-000000000003','11111111-1111-1111-1111-111111111111','BBC-1003','Junior','BIYA','M','2011-11-20','3ème','FR','secondary','ESSOMBA Paul','+237 699 44 55 66', 40),
 ('cccccccc-0000-0000-0000-000000000004','11111111-1111-1111-1111-111111111111','BBC-1004','Estelle','MANGA','F','2014-01-09','CM1','FR','primary','TALLA Marie','+237 677 88 99 00', 280),
 ('cccccccc-0000-0000-0000-000000000005','11111111-1111-1111-1111-111111111111','BBC-1005','Boris','ONDOUA','M','2010-05-25','Form 5','EN','secondary','NGONO Alice','+237 691 22 33 44', 150),
 ('cccccccc-0000-0000-0000-000000000006','11111111-1111-1111-1111-111111111111','BBC-1006','Aminatou','SONE','F','2015-09-30','Class 3','EN','primary','NGONO Alice','+237 691 22 33 44', 90);

-- Per-student fee situation (primary total 95000 / secondary 145000)
INSERT INTO student_fee (school_id, student_id, total, paid, balance, tranches_paid, status) VALUES
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000001',145000,100000,45000,2,'partial'),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000002',145000,145000,0,3,'paid'),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000003',145000,60000,85000,1,'partial'),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000004',95000,0,95000,0,'unpaid'),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000005',145000,145000,0,3,'paid'),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000006',95000,40000,55000,1,'partial');

-- Grades for two students (seq 1 & 2) across core subjects — feeds bulletins/PV
INSERT INTO grade (school_id, student_id, subject_code, sequence, mark) VALUES
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000001','MATH',1,14.5),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000001','MATH',2,15.0),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000001','FR',1,12.0),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000001','FR',2,13.5),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000001','EN',1,11.0),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000001','EN',2,12.5),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000001','PC',1,16.0),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000001','PC',2,15.5),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000002','MATH',1,9.0),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000002','MATH',2,10.5),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000002','FR',1,13.0),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000002','FR',2,14.0),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000002','EN',1,15.0),
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000002','EN',2,14.5);

-- Parent account (username: parent1 / password: password) linked to 2 children
INSERT INTO app_user (id, school_id, username, password_hash, display_name, initials, role_code) VALUES
 ('bbbbbbbb-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111','parent1','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy','MBARGA Jean','MJ','parent');
INSERT INTO parent_student (parent_user_id, student_id) VALUES
 ('bbbbbbbb-0000-0000-0000-000000000001','cccccccc-0000-0000-0000-000000000001'),
 ('bbbbbbbb-0000-0000-0000-000000000001','cccccccc-0000-0000-0000-000000000002');

-- A couple of events
INSERT INTO school_event (school_id, title, type, event_date, description, audience, target_classes, notified) VALUES
 ('11111111-1111-1111-1111-111111111111','Réunion parents-enseignants','meeting','2026-06-28','Rencontre trimestrielle — présence souhaitée.','all','[]', false),
 ('11111111-1111-1111-1111-111111111111','Composition 3e trimestre','exam','2026-07-05','Début des compositions de fin d''année.','classes','["3ème","Form 5"]', false);
