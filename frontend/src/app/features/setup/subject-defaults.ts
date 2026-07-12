/**
 * Default subject catalogues per subsystem, transcribed from the school's official
 * "MATIERE EXCEL" master list. Used by the one-click import in Academic Setup so an
 * admin can seed the Francophone / Anglophone subject lists, then adjust coefficients.
 * Codes are short, stable identifiers; coef defaults to 1 (admins tune afterwards).
 */
export interface SubjectSeed { code: string; fr: string; en: string; }

/** Section francophone — matières standard. */
export const SUBJECTS_FR: SubjectSeed[] = [
  { code: 'FRANC', fr: 'Français', en: 'French' },
  { code: 'ANGL', fr: 'Anglais', en: 'English' },
  { code: 'ETXT', fr: 'Étude de texte', en: 'Text study' },
  { code: 'EXPE', fr: 'Expression écrite', en: 'Written expression' },
  { code: 'EXPO', fr: 'Expression orale', en: 'Oral expression' },
  { code: 'ORTH', fr: 'Orthographe', en: 'Spelling' },
  { code: 'MATH', fr: 'Mathématiques', en: 'Mathematics' },
  { code: 'HIST', fr: 'Histoire', en: 'History' },
  { code: 'GEO', fr: 'Géographie', en: 'Geography' },
  { code: 'ECM', fr: 'ECM', en: 'Civics & morals' },
  { code: 'SVT', fr: 'Sciences de la vie et de la terre', en: 'Life & earth sciences' },
  { code: 'SVTEEHB', fr: 'SVTEEHB', en: 'SVTEEHB' },
  { code: 'SCI', fr: 'Sciences', en: 'Science' },
  { code: 'PHY', fr: 'Physique', en: 'Physics' },
  { code: 'CHIM', fr: 'Chimie', en: 'Chemistry' },
  { code: 'INFO', fr: 'Informatique', en: 'Computer science' },
  { code: 'TMAN', fr: 'Travail manuel', en: 'Manual labour' },
  { code: 'ESF', fr: 'Économie sociale et familiale', en: 'Home economics' },
  { code: 'AGRI', fr: 'Agriculture', en: 'Agriculture' },
  { code: 'ELEV', fr: 'Élevage', en: 'Animal husbandry' },
  { code: 'PHILO', fr: 'Philosophie', en: 'Philosophy' },
  { code: 'EPS', fr: 'Éducation physique et sportive', en: 'Physical education' },
  { code: 'LCN', fr: 'Langue et culture nationales', en: 'National language & culture' },
  { code: 'ALL', fr: 'Allemand', en: 'German' },
  { code: 'ESP', fr: 'Espagnol', en: 'Spanish' },
  { code: 'ARAB', fr: 'Arabe', en: 'Arabic' },
  { code: 'CHIN', fr: 'Chinois', en: 'Chinese' },
  { code: 'ORCO', fr: 'Orientation conseil', en: 'Guidance counselling' },
];

/** Anglophone section — standard subjects. */
export const SUBJECTS_EN: SubjectSeed[] = [
  { code: 'ENG', fr: 'Anglais', en: 'English' },
  { code: 'FREN', fr: 'Français', en: 'French' },
  { code: 'MATHS', fr: 'Mathématiques', en: 'Mathematics' },
  { code: 'BIO', fr: 'Biologie', en: 'Biology' },
  { code: 'HBIO', fr: 'Biologie humaine', en: 'Human biology' },
  { code: 'CHEM', fr: 'Chimie', en: 'Chemistry' },
  { code: 'PHYS', fr: 'Physique', en: 'Physics' },
  { code: 'CSC', fr: 'Informatique', en: 'Computer science' },
  { code: 'ECON', fr: 'Économie', en: 'Economics' },
  { code: 'HECO', fr: 'Économie domestique', en: 'Home economics' },
  { code: 'HIST', fr: 'Histoire', en: 'History' },
  { code: 'GEO', fr: 'Géographie', en: 'Geography' },
  { code: 'CIT', fr: 'Citoyenneté', en: 'Citizenship' },
  { code: 'LIT', fr: 'Littérature', en: 'Literature' },
  { code: 'LOGIC', fr: 'Logique', en: 'Logic' },
  { code: 'PHIL', fr: 'Philosophie', en: 'Philosophy' },
  { code: 'ARTC', fr: 'Art et culture', en: 'Art & culture' },
  { code: 'NATC', fr: 'Culture nationale', en: 'National culture' },
  { code: 'MLAB', fr: 'Travail manuel', en: 'Manual labour' },
  { code: 'SPE', fr: 'Éducation physique et sportive', en: 'Sport & physical education' },
  { code: 'CLUB', fr: 'Activités de club', en: 'Club activities' },
];

export function defaultSubjects(subsystem: 'FR' | 'EN'): SubjectSeed[] {
  return subsystem === 'EN' ? SUBJECTS_EN : SUBJECTS_FR;
}
