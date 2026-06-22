import { Injectable, signal } from '@angular/core';

export type Lang = 'fr' | 'en';

const DICT: Record<string, { fr: string; en: string }> = {
  appName: { fr: 'BBC SMS', en: 'BBC SMS' },
  login: { fr: 'Connexion', en: 'Sign in' },
  username: { fr: "Nom d'utilisateur", en: 'Username' },
  password: { fr: 'Mot de passe', en: 'Password' },
  signIn: { fr: 'Se connecter', en: 'Sign in' },
  signOut: { fr: 'Se déconnecter', en: 'Sign out' },
  apps: { fr: 'Applications', en: 'Apps' },
  backToApps: { fr: 'Applications', en: 'Apps' },
  dashboard: { fr: 'Tableau de bord', en: 'Dashboard' },
  presence: { fr: 'Présence', en: 'Attendance' },
  students: { fr: 'Élèves', en: 'Students' },
  journey: { fr: 'Parcours', en: 'Journey' },
  alerts: { fr: 'Alertes', en: 'Alerts' },
  messages: { fr: 'Correspondance', en: 'Correspondence' },
  coursebook: { fr: 'Cahier de textes', en: 'Coursebook' },
  health: { fr: 'Santé', en: 'Health' },
  documents: { fr: 'Documents', en: 'Documents' },
  hr: { fr: 'Personnel', en: 'Staff' },
  academic: { fr: 'Académique', en: 'Academic' },
  finance: { fr: 'Finance', en: 'Finance' },
  timetable: { fr: 'Emploi du temps', en: 'Timetable' },
  events: { fr: 'Événements', en: 'Events' },
  discipline: { fr: 'Discipline', en: 'Discipline' },
  reports: { fr: 'Rapports', en: 'Reports' },
  settings: { fr: 'Paramètres', en: 'Settings' },
  present: { fr: 'Présents', en: 'Present' },
  late: { fr: 'Retards', en: 'Late' },
  absent: { fr: 'Absents', en: 'Absent' },
  liveBoard: { fr: 'Présence en direct', en: 'Live attendance' },
  newStudent: { fr: 'Nouvel élève', en: 'New student' },
  revenue30: { fr: 'Revenus (30 j)', en: 'Revenue (30d)' },
  expense30: { fr: 'Dépenses (30 j)', en: 'Expenses (30d)' },
  balance30: { fr: 'Solde (30 j)', en: 'Balance (30d)' },
  save: { fr: 'Enregistrer', en: 'Save' },
  cancel: { fr: 'Annuler', en: 'Cancel' },
};

const MODULE_LABEL: Record<string, { fr: string; en: string }> = DICT;

@Injectable({ providedIn: 'root' })
export class I18nService {
  readonly lang = signal<Lang>((localStorage.getItem('bbc-lang') as Lang) || 'fr');

  setLang(l: Lang): void {
    this.lang.set(l);
    localStorage.setItem('bbc-lang', l);
  }

  t(key: string): string {
    const entry = DICT[key];
    return entry ? entry[this.lang()] : key;
  }

  moduleLabel(module: string): string {
    const entry = MODULE_LABEL[module];
    return entry ? entry[this.lang()] : module;
  }
}
