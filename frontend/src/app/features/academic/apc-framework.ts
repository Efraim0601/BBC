/**
 * APC (Approche Par Compétences) report-card framework for Maternelle & Primaire,
 * transcribed from the official BBC templates (`bulletins templates/`). Two faithful
 * variants — Francophone and Anglophone — each with the six national competencies,
 * their sub-competencies and evaluation types with their max marks. Used to render a
 * conformant, printable bulletin; marks are filled by hand or in a later iteration.
 */
export interface ApcEval { label: string; max: number; }
export interface ApcSub { code: string; title: string; max: number; evals: ApcEval[]; }
export interface ApcCompetency { code: string; title: string; max: number; subs: ApcSub[]; }
export interface ApcFramework {
  subsystem: 'FR' | 'EN';
  terms: string[];          // the three trimesters
  grandTotal: number;       // printed denominator (e.g. /280)
  competencies: ApcCompetency[];
}

/** Anglophone primary framework (grand total /280). */
export const APC_EN: ApcFramework = {
  subsystem: 'EN',
  terms: ['First', 'Second', 'Third'],
  grandTotal: 280,
  competencies: [
    { code: 'C1', title: 'Communicate in English, French and one national language', max: 100, subs: [
      { code: '1A', title: 'Communication in English', max: 40, evals: [
        { label: 'Oral', max: 20 }, { label: 'Written', max: 15 }, { label: 'Attitude', max: 5 } ] },
      { code: '1B', title: 'Communicate in French', max: 40, evals: [
        { label: 'Oral', max: 20 }, { label: 'Written', max: 15 }, { label: 'Attitude', max: 5 } ] },
      { code: '1C', title: 'Communicate in one national language', max: 20, evals: [
        { label: 'Oral', max: 15 }, { label: 'Attitude', max: 5 } ] },
    ]},
    { code: 'C2', title: 'Use basic notions in mathematics, science and technology', max: 60, subs: [
      { code: '2A', title: 'Mathematics', max: 30, evals: [
        { label: 'Oral', max: 10 }, { label: 'Written', max: 15 }, { label: 'Practical', max: 3 }, { label: 'Attitude', max: 2 } ] },
      { code: '2B', title: 'Science and technology', max: 30, evals: [
        { label: 'Oral', max: 5 }, { label: 'Written', max: 15 }, { label: 'Practical', max: 5 }, { label: 'Attitude', max: 5 } ] },
    ]},
    { code: 'C3', title: 'Practice social and citizenship values', max: 30, subs: [
      { code: '3', title: 'Social and citizenship values', max: 30, evals: [
        { label: 'Oral', max: 10 }, { label: 'Written', max: 5 }, { label: 'Practical', max: 5 }, { label: 'Attitude', max: 10 } ] },
    ]},
    { code: 'C4', title: 'Demonstrate autonomy, initiative, creativity & entrepreneurship', max: 30, subs: [
      { code: '4A', title: 'Vocational studies', max: 15, evals: [
        { label: 'Oral', max: 2 }, { label: 'Written', max: 4 }, { label: 'Practical', max: 6 }, { label: 'Attitude', max: 3 } ] },
      { code: '4B', title: 'Entrepreneurship', max: 15, evals: [
        { label: 'Oral', max: 2 }, { label: 'Written', max: 4 }, { label: 'Practical', max: 6 }, { label: 'Attitude', max: 3 } ] },
    ]},
    { code: 'C5', title: 'Use basic concepts & tools of ICT', max: 20, subs: [
      { code: '5', title: 'Information & communication technology', max: 20, evals: [
        { label: 'Oral', max: 3 }, { label: 'Written', max: 4 }, { label: 'Practical', max: 10 }, { label: 'Attitude', max: 3 } ] },
    ]},
    { code: 'C6', title: 'Practice physical, sport and artistic activities', max: 40, subs: [
      { code: '6A', title: 'Physical and sports activities', max: 20, evals: [
        { label: 'Oral', max: 3 }, { label: 'Written', max: 4 }, { label: 'Practical', max: 10 }, { label: 'Attitude', max: 3 } ] },
      { code: '6B', title: 'Artistic activities', max: 20, evals: [
        { label: 'Oral', max: 12 }, { label: 'Practical', max: 8 } ] },
    ]},
    { code: 'REL', title: 'Religious studies', max: 20, subs: [
      { code: 'REL', title: 'Religious studies', max: 20, evals: [
        { label: 'Oral', max: 4 }, { label: 'Written', max: 2 }, { label: 'Practical', max: 10 }, { label: 'Attitude', max: 4 } ] },
    ]},
  ],
};

/** Francophone primary framework (grand total /280). */
export const APC_FR: ApcFramework = {
  subsystem: 'FR',
  terms: ['Premier', 'Deuxième', 'Troisième'],
  grandTotal: 280,
  competencies: [
    { code: 'C1', title: 'Communiquer en français, anglais et une langue nationale', max: 100, subs: [
      { code: '1A', title: 'Communiquer en français', max: 40, evals: [
        { label: 'Orale', max: 20 }, { label: 'Écrite', max: 15 }, { label: 'Savoir-être', max: 5 } ] },
      { code: '1B', title: 'Communicate in English', max: 40, evals: [
        { label: 'Oral', max: 20 }, { label: 'Written', max: 15 }, { label: 'Attitude', max: 5 } ] },
      { code: '1C', title: 'Pratiquer une langue nationale', max: 20, evals: [
        { label: 'Orale', max: 10 }, { label: 'Écrite', max: 5 }, { label: 'Pratique', max: 3 }, { label: 'Savoir-être', max: 2 } ] },
    ]},
    { code: 'C2', title: 'Utiliser les notions de base en mathématiques, sciences et technologies', max: 60, subs: [
      { code: '2A', title: 'Mathématiques', max: 30, evals: [
        { label: 'Orale', max: 5 }, { label: 'Écrite', max: 20 }, { label: 'Savoir-être', max: 5 } ] },
      { code: '2B', title: 'Sciences et technologies', max: 30, evals: [
        { label: 'Orale', max: 5 }, { label: 'Écrite', max: 5 }, { label: 'Pratique', max: 15 }, { label: 'Savoir-être', max: 5 } ] },
    ]},
    { code: 'C3', title: 'Pratiquer les valeurs sociales et citoyennes', max: 40, subs: [
      { code: '3A', title: 'Valeurs sociales', max: 20, evals: [
        { label: 'Orale', max: 3 }, { label: 'Écrite', max: 3 }, { label: 'Pratique', max: 10 }, { label: 'Savoir-être', max: 4 } ] },
      { code: '3B', title: 'Valeurs citoyennes', max: 20, evals: [
        { label: 'Orale', max: 5 }, { label: 'Écrite', max: 5 }, { label: 'Pratique', max: 8 }, { label: 'Savoir-être', max: 2 } ] },
    ]},
    { code: 'C4', title: "Démontrer l'autonomie, l'initiative, la créativité et l'entrepreneuriat", max: 20, subs: [
      { code: '4A', title: "Autonomie & entrepreneuriat", max: 20, evals: [
        { label: 'Orale', max: 5 }, { label: 'Écrite', max: 3 }, { label: 'Pratique', max: 10 }, { label: 'Savoir-être', max: 2 } ] },
    ]},
    { code: 'C5', title: "Utiliser les concepts de base et les outils des TIC", max: 20, subs: [
      { code: '5A', title: 'TIC', max: 20, evals: [
        { label: 'Orale', max: 3 }, { label: 'Écrite', max: 3 }, { label: 'Pratique', max: 10 }, { label: 'Savoir-être', max: 4 } ] },
    ]},
    { code: 'C6', title: 'Pratiquer les activités physiques, sportives et artistiques', max: 40, subs: [
      { code: '6A', title: 'Activités physiques et sportives', max: 20, evals: [
        { label: 'Orale', max: 3 }, { label: 'Écrite', max: 3 }, { label: 'Pratique', max: 12 }, { label: 'Savoir-être', max: 2 } ] },
      { code: '6B', title: 'Activités artistiques', max: 20, evals: [
        { label: 'Orale', max: 12 }, { label: 'Pratique', max: 8 } ] },
    ]},
  ],
};

export function apcFramework(subsystem: string): ApcFramework {
  return (subsystem || '').toUpperCase() === 'FR' ? APC_FR : APC_EN;
}
