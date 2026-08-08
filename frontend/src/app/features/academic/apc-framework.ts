/**
 * Référentiel APC (Approche Par Compétences) du primaire, transcrit fidèlement des
 * modèles officiels de l'établissement — `documents/Bulletin francophones.xlsx` et
 * `documents/FINAL FINAL REPORT CARD.xlsx`.
 *
 * <p>Chaque sous-système a DEUX barèmes, et non un seul : le nombre de points d'une
 * compétence change entre les petites classes et les grandes. Le référentiel se
 * choisit donc sur la classe de l'élève, pas sur le seul parcours actif.
 *
 * | Sous-système | Barème            | Classes                    | Total |
 * |--------------|-------------------|----------------------------|-------|
 * | Francophone  | Niveau I          | SIL, CP                    |  280  |
 * | Francophone  | Niveaux II et III | CE1, CE2, CM1, CM2         |  300  |
 * | Anglophone   | Level 1           | Class 1, Class 2           |  280  |
 * | Anglophone   | Levels 2 et 3     | Class 3 → Class 6          |  360  |
 *
 * <p>Deux règles de lecture des modèles, appliquées ici :
 * <ul>
 *   <li>le maximum d'une sous-compétence est la SOMME de ses types d'évaluation —
 *       c'est ce que l'enseignant saisit réellement ; deux en-têtes des classeurs
 *       sont fautifs et ont été corrigés en conséquence (voir README des documents) ;</li>
 *   <li>les activités sportives « aptes » et « inaptes » sont ALTERNATIVES : un élève
 *       relève de l'une ou de l'autre, aussi une seule des deux compte dans le total —
 *       c'est ce qui donne 40 points à la compétence 6 et non 60.</li>
 * </ul>
 */

/** Un type d'évaluation d'une sous-compétence (orale, écrite, pratique, savoir-être). */
export interface ApcEval { label: string; max: number; }

/**
 * Une sous-compétence.
 * @param alternative sous-compétence de remplacement (apprenants inaptes) : elle
 *                    ne s'ajoute pas au total de la compétence.
 */
export interface ApcSub { code: string; title: string; max: number; alternative?: boolean; evals: ApcEval[]; }

export interface ApcCompetency { code: string; title: string; max: number; subs: ApcSub[]; }

/** Une période du modèle : un trimestre et les colonnes de saisie qu'il porte. */
export interface ApcTerm {
  label: { fr: string; en: string };
  /** Colonnes d'évaluation du trimestre : UA1…UA8 au francophone, mois 1…8 à l'anglophone. */
  slots: string[];
  /** Le modèle anglophone totalise chaque trimestre ; le francophone non. */
  total: boolean;
}

/** Le découpage en colonnes, différent d'un sous-système à l'autre. */
export interface ApcSlots {
  /** Intitulé de la ligne d'en-tête des colonnes. */
  header: { fr: string; en: string };
  terms: ApcTerm[];
  /** Le francophone cote chaque évaluation (Notes / Cote) ; l'anglophone porte une note seule. */
  gradePerSlot: boolean;
  /** Colonne de total annuel (anglophone). */
  annualTotal: boolean;
  /** Échelle d'appréciation annuelle imprimée en fin de ligne (anglophone). */
  scale: { code: string; label: string; title: string }[];
}

export interface ApcFramework {
  id: string;
  subsystem: 'FR' | 'EN';
  level: 'maternelle' | 'primary';
  label: string;
  /** Classes couvertes par ce barème. */
  classes: string[];
  slots: ApcSlots;
  grandTotal: number;
  competencies: ApcCompetency[];
}

/**
 * Francophone : huit unités d'apprentissage réparties sur trois trimestres, chacune
 * portant une note ET une cote.
 */
const FR_SLOTS: ApcSlots = {
  header: { fr: "Unité d'apprentissage", en: 'Learning unit' },
  terms: [
    { label: { fr: 'Premier', en: 'First' }, slots: ['UA1', 'UA2', 'UA3'], total: false },
    { label: { fr: 'Deuxième', en: 'Second' }, slots: ['UA4', 'UA5', 'UA6'], total: false },
    { label: { fr: 'Troisième', en: 'Third' }, slots: ['UA7', 'UA8'], total: false },
  ],
  gradePerSlot: true,
  annualTotal: false,
  scale: [],
};

/**
 * Anglophone : huit évaluations mensuelles réparties sur trois trimestres, chaque
 * trimestre totalisé, puis un total annuel et l'échelle d'appréciation officielle.
 */
const EN_SLOTS: ApcSlots = {
  header: { fr: 'Mois', en: 'Month' },
  terms: [
    { label: { fr: 'Premier', en: 'First' }, slots: ['1', '2', '3'], total: true },
    { label: { fr: 'Deuxième', en: 'Second' }, slots: ['4', '5', '6'], total: true },
    { label: { fr: 'Troisième', en: 'Third' }, slots: ['7', '8'], total: true },
  ],
  gradePerSlot: false,
  annualTotal: true,
  scale: [
    { code: 'C', label: 'SNA', title: 'Skills not acquired' },
    { code: 'B', label: 'SPA', title: 'Skills partially acquired' },
    { code: 'A', label: 'SA', title: 'Skills acquired' },
    { code: 'A+', label: 'Exp.', title: 'Expert' },
  ],
};

/** Francophone — Niveau I (SIL, CP) — total 280 points. */
export const APC_FR_N1: ApcFramework = {
  id: 'APC_FR_N1', subsystem: 'FR', level: 'primary',
  label: 'Francophone — Niveau I (SIL, CP)',
  classes: ['SIL','CP'],
  slots: FR_SLOTS,
  grandTotal: 280,
  competencies: [
    { code: 'C1', max: 100,
      title: 'Communiquer en français et anglais et pratiquer au moins une langue nationale',
      subs: [
        { code: '1A', max: 40,
          title: 'Communiquer en Français',
          evals: [{ label: 'Orale', max: 20 }, { label: 'Écrite', max: 15 }, { label: 'Savoir-être', max: 5 }] },
        { code: '1B', max: 40,
          title: 'Communicate in English',
          evals: [{ label: 'Oral', max: 20 }, { label: 'Written', max: 15 }, { label: 'Attitude', max: 5 }] },
        { code: '1C', max: 20,
          title: 'Pratiquer une langue nationale',
          evals: [{ label: 'Orale', max: 10 }, { label: 'Écrite', max: 5 }, { label: 'Pratique', max: 3 }, { label: 'Savoir-être', max: 2 }] },
      ] },
    { code: 'C2', max: 60,
      title: 'Utiliser les notions de base en mathématiques, Sciences et Technologies',
      subs: [
        { code: '2A', max: 30,
          title: 'Utiliser les notions de base en mathematiques',
          evals: [{ label: 'Orale', max: 5 }, { label: 'Écrite', max: 20 }, { label: 'Savoir-être', max: 5 }] },
        { code: '2B', max: 30,
          title: 'Utiliser les notions de base en Sciences et Technologies',
          evals: [{ label: 'Orale', max: 5 }, { label: 'Écrite', max: 5 }, { label: 'Pratique', max: 15 }, { label: 'Savoir-être', max: 5 }] },
      ] },
    { code: 'C3', max: 40,
      title: 'Pratiquer les valeurs sociales et citoyennes',
      subs: [
        { code: '3A', max: 20,
          title: 'Pratiquer les valeurs sociales',
          evals: [{ label: 'Orale', max: 3 }, { label: 'Écrite', max: 3 }, { label: 'Pratique', max: 10 }, { label: 'Savoir-être', max: 4 }] },
        { code: '3B', max: 20,
          title: 'Pratiquer les valeurs citoyennes',
          evals: [{ label: 'Orale', max: 5 }, { label: 'Écrite', max: 5 }, { label: 'Pratique', max: 8 }, { label: 'Savoir-être', max: 2 }] },
      ] },
    { code: 'C4', max: 20,
      title: 'Démontrer l\'autonomie, l\'esprit d\'initiative, de créativité et d\'entrepreneuriat',
      subs: [
        { code: '4A', max: 20,
          title: 'Démontrer l\'autonomie, l\'esprit d\'initiative, de créativité et d\'entrepreuneuriat',
          evals: [{ label: 'Orale', max: 5 }, { label: 'Écrite', max: 3 }, { label: 'Pratique', max: 10 }, { label: 'Savoir-être', max: 2 }] },
      ] },
    { code: 'C5', max: 20,
      title: 'Utiliser les concepts de base et les outils de technologies de l\'information et de la communication',
      subs: [
        { code: '5A', max: 20,
          title: 'Utiliser les concepts de base et les outils des TIC',
          evals: [{ label: 'Orale', max: 3 }, { label: 'Écrite', max: 3 }, { label: 'Pratique', max: 10 }, { label: 'Savoir-être', max: 4 }] },
      ] },
    { code: 'C6', max: 40,
      title: 'Pratiquer les activités physiques, sportives et artistiques',
      subs: [
        { code: '6A1', max: 20,
          title: 'Pratiquer les activités physique, sportives pour les apprenants aptes',
          evals: [{ label: 'Orale', max: 3 }, { label: 'Écrite', max: 3 }, { label: 'Pratique', max: 10 }, { label: 'Savoir-être', max: 4 }] },
        { code: '6A2', max: 20, alternative: true,
          title: 'Pratiquer les activités physiques, sportives pour les apprenants inaptes',
          evals: [{ label: 'Orale', max: 8 }, { label: 'Écrite', max: 10 }, { label: 'Savoir-être', max: 2 }] },
        { code: '6B', max: 20,
          title: 'Pratiquer les activités artistiques',
          evals: [{ label: 'Orale', max: 4 }, { label: 'Écrite', max: 3 }, { label: 'Pratique', max: 10 }, { label: 'Savoir-être', max: 3 }] },
      ] },
  ],
};

/** Francophone — Niveaux II et III (CE1 → CM2) — total 300 points. */
export const APC_FR_N23: ApcFramework = {
  id: 'APC_FR_N23', subsystem: 'FR', level: 'primary',
  label: 'Francophone — Niveaux II et III (CE1 → CM2)',
  classes: ['CE1','CE2','CM1','CM2'],
  slots: FR_SLOTS,
  grandTotal: 300,
  competencies: [
    { code: 'C1', max: 80,
      title: 'Communiquer en français et anglais et pratiquer au moins une langue nationale',
      subs: [
        { code: '1A', max: 30,
          title: 'Communiquer en Français',
          evals: [{ label: 'Orale', max: 12 }, { label: 'Écrite', max: 15 }, { label: 'Savoir-être', max: 3 }] },
        { code: '1B', max: 30,
          title: 'Communicate in English',
          evals: [{ label: 'Oral', max: 12 }, { label: 'Written', max: 15 }, { label: 'Attitude', max: 3 }] },
        { code: '1C', max: 20,
          title: 'Pratiquer une langue nationale',
          evals: [{ label: 'Orale', max: 10 }, { label: 'Écrite', max: 6 }, { label: 'Pratique', max: 2 }, { label: 'Savoir-être', max: 2 }] },
      ] },
    { code: 'C2', max: 80,
      title: 'Utiliser les notions de base en mathématiques, Sciences et Technologies',
      subs: [
        { code: '2A', max: 40,
          title: 'Utiliser les notions de base en mathematiques',
          evals: [{ label: 'Orale', max: 8 }, { label: 'Écrite', max: 28 }, { label: 'Savoir-être', max: 4 }] },
        { code: '2B', max: 40,
          title: 'Utiliser les notions de base en Sciences et Technologies',
          evals: [{ label: 'Orale', max: 6 }, { label: 'Écrite', max: 7 }, { label: 'Pratique', max: 20 }, { label: 'Savoir-être', max: 7 }] },
      ] },
    { code: 'C3', max: 40,
      title: 'Pratiquer les valeurs sociales et citoyennes',
      subs: [
        { code: '3A', max: 20,
          title: 'Pratiquer les valeurs sociales',
          evals: [{ label: 'Orale', max: 3 }, { label: 'Écrite', max: 8 }, { label: 'Pratique', max: 5 }, { label: 'Savoir-être', max: 4 }] },
        { code: '3B', max: 20,
          title: 'Pratiquer les valeurs citoyennes',
          evals: [{ label: 'Orale', max: 3 }, { label: 'Écrite', max: 9 }, { label: 'Pratique', max: 5 }, { label: 'Savoir-être', max: 3 }] },
      ] },
    { code: 'C4', max: 20,
      title: 'Démontrer l\'autonomie, l\'esprit d\'initiative, de créativité et d\'entrepreneuriat',
      subs: [
        { code: '4', max: 20,
          title: 'Démontrer l\'autonomie, l\'esprit d\'initiative, de créativité et d\'entrepreuneuriat',
          evals: [{ label: 'Orale', max: 5 }, { label: 'Écrite', max: 2 }, { label: 'Pratique', max: 11 }, { label: 'Savoir-être', max: 2 }] },
      ] },
    { code: 'C5', max: 40,
      title: 'Utiliser les concepts de base et les outils de technologies de l\'information et de la communication',
      subs: [
        { code: '5', max: 40,
          title: 'Utiliser les concepts de base et les outils des TIC',
          evals: [{ label: 'Orale', max: 4 }, { label: 'Écrite', max: 10 }, { label: 'Pratique', max: 20 }, { label: 'Savoir-être', max: 6 }] },
      ] },
    { code: 'C6', max: 40,
      title: 'Pratiquer les activités physiques, sportives et artistiques',
      subs: [
        { code: '6A1', max: 20,
          title: 'Pratiquer les activités physique, sportives pour les apprenants aptes',
          evals: [{ label: 'Orale', max: 2 }, { label: 'Écrite', max: 2 }, { label: 'Pratique', max: 12 }, { label: 'Savoir-être', max: 4 }] },
        { code: '6A2', max: 20, alternative: true,
          title: 'Pratiquer les activités physiques, sportives pour les apprenants inaptes',
          evals: [{ label: 'Orale', max: 3 }, { label: 'Écrite', max: 15 }, { label: 'Savoir-être', max: 2 }] },
        { code: '6B', max: 20,
          title: 'Pratiquer les activités artistiques',
          evals: [{ label: 'Orale', max: 2 }, { label: 'Écrite', max: 4 }, { label: 'Pratique', max: 12 }, { label: 'Savoir-être', max: 2 }] },
      ] },
  ],
};

/** Anglophone — Level 1 (Class 1, Class 2) — total 280 points. */
export const APC_EN_L1: ApcFramework = {
  id: 'APC_EN_L1', subsystem: 'EN', level: 'primary',
  label: 'Anglophone — Level 1 (Class 1, Class 2)',
  classes: ['Class 1','Class 2'],
  slots: EN_SLOTS,
  grandTotal: 280,
  competencies: [
    { code: 'C1', max: 100,
      title: 'Communicate in english, french and one national language',
      subs: [
        { code: '1A', max: 40,
          title: 'Communicate in English',
          evals: [{ label: 'Oral', max: 20 }, { label: 'Written', max: 15 }, { label: 'Attitude', max: 5 }] },
        { code: '1B', max: 40,
          title: 'Communicate in French',
          evals: [{ label: 'Oral', max: 20 }, { label: 'Écrit', max: 15 }, { label: 'Savoir-être', max: 5 }] },
        { code: '1C', max: 20,
          title: 'Communicate in one national language',
          evals: [{ label: 'Oral', max: 15 }, { label: 'Attitude', max: 5 }] },
      ] },
    { code: 'C2', max: 60,
      title: 'Use basic notions in mathematics, science and technology',
      subs: [
        { code: '2A', max: 30,
          title: 'Use basic notions in Mathematics',
          evals: [{ label: 'Oral', max: 10 }, { label: 'Written', max: 15 }, { label: 'Practical', max: 3 }, { label: 'Attitude', max: 2 }] },
        { code: '2B', max: 30,
          title: 'Use basic notions in science and technology',
          evals: [{ label: 'Oral', max: 5 }, { label: 'Written', max: 15 }, { label: 'Practical', max: 5 }, { label: 'Attitude', max: 5 }] },
      ] },
    { code: 'C3', max: 30,
      title: 'Practise social and citizenship values',
      subs: [
        { code: '3A', max: 30,
          title: 'Practise Citizenship value',
          evals: [{ label: 'Oral', max: 10 }, { label: 'Written', max: 5 }, { label: 'Practical', max: 5 }, { label: 'Attitude', max: 10 }] },
      ] },
    { code: 'C4', max: 30,
      title: 'Demonstrate autonomy, spirit of initiative, creativity and entrepreneurship',
      subs: [
        { code: '4A', max: 15,
          title: 'Demonstrate autonomy, spirit of initiative creativity and entrepreneurship in vocational studies',
          evals: [{ label: 'Oral', max: 2 }, { label: 'Written', max: 4 }, { label: 'Practical', max: 6 }, { label: 'Attitude', max: 3 }] },
        { code: '4B', max: 15,
          title: 'Demonstrate autonomy, spirit of initiative creativity and entrepreneurship',
          evals: [{ label: 'Oral', max: 2 }, { label: 'Written', max: 4 }, { label: 'Practical', max: 6 }, { label: 'Attitude', max: 3 }] },
      ] },
    { code: 'C5', max: 20,
      title: 'Use basic concepts and tools of information and communication technology',
      subs: [
        { code: '5A', max: 20,
          title: 'Use basic concepts and tools of information and communication technologys',
          evals: [{ label: 'Oral', max: 3 }, { label: 'Written', max: 4 }, { label: 'Practical', max: 10 }, { label: 'Attitude', max: 3 }] },
      ] },
    { code: 'C6', max: 40,
      title: 'Practice physical, sports and artistic activities',
      subs: [
        { code: '6A1', max: 20,
          title: 'Practise physical and sports actiovities',
          evals: [{ label: 'Oral', max: 3 }, { label: 'Written', max: 4 }, { label: 'Practical', max: 10 }, { label: 'Attitude', max: 3 }] },
        { code: '6A2', max: 20, alternative: true,
          title: 'Practice physical sport, for the physically challenged',
          evals: [{ label: 'Oral', max: 12 }, { label: 'Written', max: 0 }, { label: 'Practical', max: 0 }, { label: 'Attitude', max: 8 }] },
        { code: '6B', max: 20,
          title: 'Practice artistic activities',
          evals: [{ label: 'Oral', max: 4 }, { label: 'Written', max: 2 }, { label: 'Practical', max: 10 }, { label: 'Attitude', max: 4 }] },
      ] },
  ],
};

/** Anglophone — Levels 2 and 3 (Class 3 → Class 6) — total 360 points. */
export const APC_EN_L23: ApcFramework = {
  id: 'APC_EN_L23', subsystem: 'EN', level: 'primary',
  label: 'Anglophone — Levels 2 and 3 (Class 3 → Class 6)',
  classes: ['Class 3','Class 4','Class 5','Class 6'],
  slots: EN_SLOTS,
  grandTotal: 360,
  competencies: [
    { code: 'C1', max: 100,
      title: 'Communicate in english, french and one national language',
      subs: [
        { code: '1A', max: 40,
          title: 'Communicate in English',
          evals: [{ label: 'Oral', max: 20 }, { label: 'Written', max: 15 }, { label: 'Attitude', max: 5 }] },
        { code: '1B', max: 40,
          title: 'Communicate in French',
          evals: [{ label: 'Oral', max: 20 }, { label: 'Écrit', max: 15 }, { label: 'Savoir-être', max: 5 }] },
        { code: '1C', max: 20,
          title: 'Communicate in one national language',
          evals: [{ label: 'Oral', max: 15 }, { label: 'Attitude', max: 5 }] },
      ] },
    { code: 'C2', max: 100,
      title: 'Use basic notions in mathematics, science and technology',
      subs: [
        { code: '2A', max: 50,
          title: 'Use basic notions in Mathematics',
          evals: [{ label: 'Oral', max: 10 }, { label: 'Written', max: 20 }, { label: 'Practical', max: 15 }, { label: 'Attitude', max: 5 }] },
        { code: '2B', max: 50,
          title: 'Use basic notions in science and technology',
          evals: [{ label: 'Oral', max: 10 }, { label: 'Written', max: 20 }, { label: 'Practical', max: 15 }, { label: 'Attitude', max: 5 }] },
      ] },
    { code: 'C3', max: 40,
      title: 'Practise social and citizenship values',
      subs: [
        { code: '3A', max: 20,
          title: 'Practise Social values',
          evals: [{ label: 'Oral', max: 6 }, { label: 'Written', max: 2 }, { label: 'Practical', max: 2 }, { label: 'Attitude', max: 10 }] },
        { code: '3B', max: 20,
          title: 'Practise Citizenship values',
          evals: [{ label: 'Oral', max: 6 }, { label: 'Written', max: 2 }, { label: 'Practical', max: 2 }, { label: 'Attitude', max: 10 }] },
      ] },
    { code: 'C4', max: 40,
      title: 'Demonstrate autonomy, spirit of initiative, creativity and entrepreneurship',
      subs: [
        { code: '4A', max: 20,
          title: 'Demonstrate autonomy, spirit of initiative creativity and entrepreneurship in vocational studies',
          evals: [{ label: 'Oral', max: 3 }, { label: 'Written', max: 5 }, { label: 'Practical', max: 10 }, { label: 'Attitude', max: 2 }] },
        { code: '4B', max: 20,
          title: 'Demonstrate autonomy, spirit of initiative creativity and entrepreneurship',
          evals: [{ label: 'Oral', max: 3 }, { label: 'Written', max: 5 }, { label: 'Practical', max: 10 }, { label: 'Attitude', max: 2 }] },
      ] },
    { code: 'C5', max: 40,
      title: 'Use basic concepts and tools of information and communication technology',
      subs: [
        { code: '5A', max: 40,
          title: 'Use basic concepts and tools of information and communication technologys',
          evals: [{ label: 'Oral', max: 5 }, { label: 'Written', max: 10 }, { label: 'Practical', max: 20 }, { label: 'Attitude', max: 5 }] },
      ] },
    { code: 'C6', max: 40,
      title: 'Practice physical, sports and artistic activities',
      subs: [
        { code: '6A1', max: 20,
          title: 'Practice physical and sports activities',
          evals: [{ label: 'Oral', max: 3 }, { label: 'Written', max: 4 }, { label: 'Practical', max: 10 }, { label: 'Attitude', max: 3 }] },
        { code: '6A2', max: 20, alternative: true,
          title: 'Practice Physical sport, for the physically challenged',
          evals: [{ label: 'Oral', max: 12 }, { label: 'Written', max: 0 }, { label: 'Attitude', max: 8 }] },
        { code: '6B', max: 20,
          title: 'Practice artistic activities',
          evals: [{ label: 'Oral', max: 4 }, { label: 'Written', max: 2 }, { label: 'Practical', max: 10 }, { label: 'Attitude', max: 4 }] },
      ] },
  ],
};

/** Les quatre barèmes officiels, dans l'ordre de lecture des classeurs. */
export const APC_FRAMEWORKS: ApcFramework[] = [APC_FR_N1, APC_FR_N23, APC_EN_L1, APC_EN_L23];

/** Minuscules, sans accents ni ponctuation — pour reconnaître « 1ère », « CE 1 », « CLASS ONE ». */
function normalize(s: string): string {
  return (s || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
}

/** Classes anglophones écrites en toutes lettres dans les modèles. */
const EN_WORDS: Record<string, string> = {
  one: '1', two: '2', three: '3', four: '4', five: '5', six: '6',
};

/** Échelle francophone du primaire, dans l'ordre du cycle. */
const FR_LADDER = ['sil', 'cp', 'ce1', 'ce2', 'cm1', 'cm2'];

/** Ce qu'un libellé de classe révèle : son sous-système et son rang dans le cycle. */
export interface ApcClassMatch { subsystem: 'FR' | 'EN'; order: number; }

/**
 * Reconnaît une classe du primaire d'après son libellé : « CE2 A » → francophone,
 * rang 4 ; « CLASS ONE » → anglophone, rang 1. Renvoie null pour tout le reste —
 * une classe du secondaire, ou un libellé propre à l'établissement.
 *
 * <p>Le reste du libellé après le barreau doit être vide ou commencer par une
 * séparation, faute de quoi « CPA » se ferait passer pour un CP.
 */
export function apcClassMatch(className: string): ApcClassMatch | null {
  const n = normalize(className);
  if (!n) return null;
  for (let i = 0; i < FR_LADDER.length; i++) {
    const rung = FR_LADDER[i];
    const spaced = rung.replace(/(\d)$/, ' $1');            // « CE 1 » aussi bien que « CE1 »
    for (const form of [rung, spaced]) {
      if (n === form || n.startsWith(form + ' ')) return { subsystem: 'FR', order: i + 1 };
    }
  }
  // Le préfixe est exigé : une classe nommée « 6 » n'est pas une Class 6 anglophone.
  const m = /^(?:class|classe|level|niveau)\s*(\d|one|two|three|four|five|six)\b/.exec(n);
  if (m) return { subsystem: 'EN', order: Number(EN_WORDS[m[1]] ?? m[1]) };
  return null;
}

/** Rang de la classe dans son cycle : SIL=1 … CM2=6, Class 1=1 … Class 6=6 ; 0 si inconnu. */
export function apcGradeOrder(className: string): number {
  return apcClassMatch(className)?.order ?? 0;
}

/**
 * Le barème applicable à un élève.
 *
 * <p>La classe prime : elle porte à la fois le sous-système et le rang, et reste
 * donc juste en mode « Tous les parcours », où aucun parcours n'est actif. Le
 * parcours ne sert de repli que si le libellé n'est pas reconnu.
 *
 * <p>La maternelle n'a pas de modèle propre dans les documents de l'établissement :
 * elle reprend le barème de la première année du primaire, dans son sous-système.
 */
export function apcFramework(subsystem?: string | null, className?: string | null): ApcFramework {
  const match = apcClassMatch(className || '');
  const en = match ? match.subsystem === 'EN' : (subsystem || '').toUpperCase() === 'EN';
  const order = match?.order ?? 0;
  if (en) return order >= 3 ? APC_EN_L23 : APC_EN_L1;
  return order >= 3 ? APC_FR_N23 : APC_FR_N1;
}
