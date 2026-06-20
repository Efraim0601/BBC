-- ============================================================================
--  Seed: Bayo Bilingual Complex demo tenant + roles + permission matrix.
--  Demo login -> username: principal  /  password: password   (CHANGE IN PROD)
-- ============================================================================

-- Fixed tenant id so other rows can reference it deterministically.
INSERT INTO school (id, code, name, motto)
VALUES ('11111111-1111-1111-1111-111111111111', 'BBC', 'Bayo Bilingual Complex', 'Excellence bilingue');

INSERT INTO academic_year (school_id, label, start_year, is_current)
VALUES ('11111111-1111-1111-1111-111111111111', '2025-2026', 2025, true);

-- ---- Roles ------------------------------------------------------------------
INSERT INTO role (code, label_fr, label_en) VALUES
 ('principal',    'Principal',          'Principal'),
 ('prefect',      'Préfet d''études',   'Dean of studies'),
 ('econome',      'Économe',            'Bursar'),
 ('form_teacher', 'Prof. Principal',    'Form Teacher'),
 ('teacher',      'Enseignant',         'Teacher'),
 ('parent',       'Parent',             'Parent');

-- ---- Permission matrix (role x module -> level) -----------------------------
INSERT INTO permission_grant (school_id, role_code, module, level) VALUES
 -- principal
 ('11111111-1111-1111-1111-111111111111','principal','dashboard','write'),
 ('11111111-1111-1111-1111-111111111111','principal','presence','read'),
 ('11111111-1111-1111-1111-111111111111','principal','students','write'),
 ('11111111-1111-1111-1111-111111111111','principal','hr','write'),
 ('11111111-1111-1111-1111-111111111111','principal','academic','write'),
 ('11111111-1111-1111-1111-111111111111','principal','finance','read'),
 ('11111111-1111-1111-1111-111111111111','principal','timetable','write'),
 ('11111111-1111-1111-1111-111111111111','principal','events','write'),
 ('11111111-1111-1111-1111-111111111111','principal','discipline','write'),
 ('11111111-1111-1111-1111-111111111111','principal','reports','read'),
 ('11111111-1111-1111-1111-111111111111','principal','settings','write'),
 -- prefect
 ('11111111-1111-1111-1111-111111111111','prefect','dashboard','read'),
 ('11111111-1111-1111-1111-111111111111','prefect','presence','write'),
 ('11111111-1111-1111-1111-111111111111','prefect','students','read'),
 ('11111111-1111-1111-1111-111111111111','prefect','academic','read'),
 ('11111111-1111-1111-1111-111111111111','prefect','timetable','write'),
 ('11111111-1111-1111-1111-111111111111','prefect','events','write'),
 ('11111111-1111-1111-1111-111111111111','prefect','discipline','write'),
 ('11111111-1111-1111-1111-111111111111','prefect','reports','read'),
 -- econome
 ('11111111-1111-1111-1111-111111111111','econome','dashboard','read'),
 ('11111111-1111-1111-1111-111111111111','econome','students','read'),
 ('11111111-1111-1111-1111-111111111111','econome','finance','write'),
 ('11111111-1111-1111-1111-111111111111','econome','reports','read'),
 -- form_teacher
 ('11111111-1111-1111-1111-111111111111','form_teacher','dashboard','read'),
 ('11111111-1111-1111-1111-111111111111','form_teacher','presence','read'),
 ('11111111-1111-1111-1111-111111111111','form_teacher','students','read'),
 ('11111111-1111-1111-1111-111111111111','form_teacher','academic','write'),
 ('11111111-1111-1111-1111-111111111111','form_teacher','timetable','read'),
 ('11111111-1111-1111-1111-111111111111','form_teacher','events','read'),
 ('11111111-1111-1111-1111-111111111111','form_teacher','discipline','write'),
 -- teacher
 ('11111111-1111-1111-1111-111111111111','teacher','dashboard','read'),
 ('11111111-1111-1111-1111-111111111111','teacher','presence','read'),
 ('11111111-1111-1111-1111-111111111111','teacher','academic','write'),
 ('11111111-1111-1111-1111-111111111111','teacher','timetable','read'),
 ('11111111-1111-1111-1111-111111111111','teacher','events','read'),
 -- parent
 ('11111111-1111-1111-1111-111111111111','parent','parent','read');

-- ---- Sections & classes -----------------------------------------------------
INSERT INTO section (id, school_id, label, subsystem, level) VALUES
 ('pri-fr','11111111-1111-1111-1111-111111111111','Primaire FR','FR','primary'),
 ('pri-en','11111111-1111-1111-1111-111111111111','Primary EN','EN','primary'),
 ('sec-fr','11111111-1111-1111-1111-111111111111','Secondaire FR','FR','secondary'),
 ('sec-en','11111111-1111-1111-1111-111111111111','Secondary EN','EN','secondary');

INSERT INTO school_class (school_id, section_id, name, subsystem, level)
SELECT '11111111-1111-1111-1111-111111111111', sec, cls, sub, lvl
FROM (VALUES
 ('pri-fr','SIL','FR','primary'),('pri-fr','CP','FR','primary'),('pri-fr','CE1','FR','primary'),
 ('pri-fr','CE2','FR','primary'),('pri-fr','CM1','FR','primary'),('pri-fr','CM2','FR','primary'),
 ('sec-fr','6ème','FR','secondary'),('sec-fr','5ème','FR','secondary'),('sec-fr','4ème','FR','secondary'),
 ('sec-fr','3ème','FR','secondary'),('sec-fr','2nde','FR','secondary'),('sec-fr','1ère','FR','secondary'),
 ('sec-fr','Terminale','FR','secondary'),
 ('pri-en','Class 1','EN','primary'),('pri-en','Class 2','EN','primary'),('pri-en','Class 3','EN','primary'),
 ('sec-en','Form 1','EN','secondary'),('sec-en','Form 5','EN','secondary'),('sec-en','Upper Sixth','EN','secondary')
) AS c(sec,cls,sub,lvl);

-- ---- Subjects ---------------------------------------------------------------
INSERT INTO subject (school_id, code, label, coef) VALUES
 ('11111111-1111-1111-1111-111111111111','MATH','{"fr":"Mathématiques","en":"Mathematics"}',4),
 ('11111111-1111-1111-1111-111111111111','FR','{"fr":"Français","en":"French"}',4),
 ('11111111-1111-1111-1111-111111111111','EN','{"fr":"Anglais","en":"English"}',3),
 ('11111111-1111-1111-1111-111111111111','HG','{"fr":"Histoire-Géo","en":"History-Geo"}',3),
 ('11111111-1111-1111-1111-111111111111','SVT','{"fr":"SVT","en":"Biology"}',3),
 ('11111111-1111-1111-1111-111111111111','PC','{"fr":"Physique-Chimie","en":"Physics-Chem"}',3),
 ('11111111-1111-1111-1111-111111111111','EPS','{"fr":"EPS","en":"PE"}',2),
 ('11111111-1111-1111-1111-111111111111','INFO','{"fr":"Informatique","en":"Computer Science"}',2);

-- ---- Fee configuration ------------------------------------------------------
INSERT INTO fee_config (school_id, level, subsystem, total, tranches, items) VALUES
 ('11111111-1111-1111-1111-111111111111','primary',NULL,95000,'[40000,30000,25000]',
   '[{"name":"Scolarité","amount":75000},{"name":"APE","amount":8000},{"name":"Uniforme","amount":12000}]'),
 ('11111111-1111-1111-1111-111111111111','secondary',NULL,145000,'[60000,45000,40000]',
   '[{"name":"Scolarité","amount":120000},{"name":"APE","amount":10000},{"name":"Uniforme","amount":15000}]');

-- ---- Demo employees & login accounts ----------------------------------------
-- password_hash below is BCrypt for the literal "password".
INSERT INTO employee (id, school_id, code, name, initials, sex, type, email, monthly_salary) VALUES
 ('aaaaaaaa-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111','EMP-001','NGANOU Aïcha','NA','F','Permanent','a.nganou@bbc.cm',850000),
 ('aaaaaaaa-0000-0000-0000-000000000008','11111111-1111-1111-1111-111111111111','EMP-008','MBAH Junior','MJ','M','Permanent','j.mbah@bbc.cm',320000);
INSERT INTO employee_role (employee_id, role_code) VALUES
 ('aaaaaaaa-0000-0000-0000-000000000001','principal'),
 ('aaaaaaaa-0000-0000-0000-000000000008','econome');

INSERT INTO app_user (school_id, username, password_hash, display_name, initials, role_code, employee_id) VALUES
 ('11111111-1111-1111-1111-111111111111','principal','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy','NGANOU Aïcha','NA','principal','aaaaaaaa-0000-0000-0000-000000000001'),
 ('11111111-1111-1111-1111-111111111111','econome','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy','MBAH Junior','MJ','econome','aaaaaaaa-0000-0000-0000-000000000008');

-- ---- Demo attendance device -------------------------------------------------
INSERT INTO device (school_id, label, api_key) VALUES
 ('11111111-1111-1111-1111-111111111111','Lecteur empreinte — Portail A','dev-key-bbc-portal-a');
