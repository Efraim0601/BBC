-- ============================================================================
--  V12 (seed) — demo correspondence notices for a few students.
--  Applied only with the `demo` Spring profile (flyway location db/seed).
--  Permission grants for the `messages` module are handled by V11 (all profiles).
-- ============================================================================

-- Cédric FOTSO (BBC-1001) — a convocation that the parent has already signed.
INSERT INTO correspondence
 (school_id, student_id, category, subject, body, requires_ack, acknowledged_at, acknowledged_by, sender_name) VALUES
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000001','convocation',
  'Convocation — rendez-vous avec le professeur principal',
  'Cher parent, nous vous invitons à un entretien le vendredi à 15h afin d''évoquer les résultats du premier trimestre de Cédric. Merci de confirmer votre présence.',
  true, now() - interval '2 days', 'M. FOTSO Bernard', 'Mme NGONO (Prof. principal)');

-- Adèle NKENG (BBC-1002) — an absence notice still pending acknowledgement.
INSERT INTO correspondence
 (school_id, student_id, category, subject, body, requires_ack, sender_name) VALUES
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000002','absence',
  'Absence non justifiée du lundi',
  'Bonjour, votre fille Adèle a été absente lundi sans justificatif. Merci de nous faire parvenir un mot d''excuse ou de régulariser auprès de la vie scolaire.',
  true, 'M. ETOA (Surveillant général)');

-- Estelle MANGA (BBC-1004) — a congratulations note (no acknowledgement required).
INSERT INTO correspondence
 (school_id, student_id, category, subject, body, requires_ack, sender_name) VALUES
 ('11111111-1111-1111-1111-111111111111','cccccccc-0000-0000-0000-000000000004','congrats',
  'Félicitations — Tableau d''honneur',
  'Nous avons le plaisir de vous informer qu''Estelle figure au tableau d''honneur de la classe ce mois-ci. Toutes nos félicitations pour son travail !',
  false, 'La Direction');
