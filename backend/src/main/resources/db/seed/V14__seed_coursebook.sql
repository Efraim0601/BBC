-- ============================================================================
--  V14 (seed) — demo cahier de textes for class 4ème.
--  Applied only with the `demo` Spring profile (flyway location db/seed).
--  Permission grants for the `coursebook` module are handled by V13 (all profiles).
-- ============================================================================

INSERT INTO coursebook_entry
 (school_id, class_name, subject_code, entry_date, content, homework, due_date) VALUES
 ('11111111-1111-1111-1111-111111111111','4ème','MATH','2024-11-18',
  'Théorème de Pythagore : énoncé, réciproque et applications au calcul de longueurs dans un triangle rectangle.',
  'Exercices 12 à 15 page 84 sur le calcul de l''hypoténuse.','2024-11-21'),
 ('11111111-1111-1111-1111-111111111111','4ème','FR','2024-11-19',
  'Étude du texte argumentatif : repérage de la thèse, des arguments et des connecteurs logiques.',
  'Rédiger un paragraphe argumenté de 10 lignes sur l''utilité de la lecture.','2024-11-22'),
 ('11111111-1111-1111-1111-111111111111','4ème','PC','2024-11-20',
  'Les circuits électriques : montage en série et en dérivation, mesure de l''intensité avec l''ampèremètre.',
  'Schématiser un circuit en dérivation et répondre aux questions 1 à 4 page 56.','2024-11-25'),
 ('11111111-1111-1111-1111-111111111111','4ème','MATH','2024-11-21',
  'Correction des exercices sur le théorème de Pythagore et introduction à la trigonométrie (cosinus).',
  NULL, NULL);
