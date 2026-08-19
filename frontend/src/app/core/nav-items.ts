/** Shared module navigation catalogue — single source for the apps-home grid and the persistent sidebar. */

export interface Mod {
  id: string;
  route: string;
  iconBg: string;
  color: string;
  svg: string;
  subFr: string;
  subEn: string;
}
export interface Group { key: string; labelFr: string; labelEn: string; mods: Mod[]; }

export const NAV_ICONS = {
  users: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>',
  building: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><rect x="4" y="2" width="16" height="20" rx="2"/><path d="M9 22v-4h6v4M9 6h.01M15 6h.01M9 10h.01M15 10h.01M9 14h.01M15 14h.01"/></svg>',
  book: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>',
  fingerprint: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M12 11v3M7 8a8 8 0 0 1 10 0M5 12a10 10 0 0 1 14 0M9 14.5a4 4 0 0 1 6 0M9.5 19a8 8 0 0 0 5 0"/></svg>',
  shield: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>',
  wallet: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M20 12V8H6a2 2 0 0 1 0-4h12v4"/><path d="M4 6v12a2 2 0 0 0 2 2h14v-4"/><path d="M18 12a2 2 0 0 0 0 4h4v-4z"/></svg>',
  calendar: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/></svg>',
  bell: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>',
  home: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><path d="M9 22V12h6v10"/></svg>',
  chart: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M3 3v18h18"/><path d="m19 9-5 5-4-4-3 3"/></svg>',
  settings: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>',
  route: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><circle cx="6" cy="19" r="3"/><circle cx="18" cy="5" r="3"/><path d="M9 19h5.5a3.5 3.5 0 0 0 0-7h-4a3.5 3.5 0 0 1 0-7H15"/></svg>',
  alert: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><path d="M12 9v4M12 17h.01"/></svg>',
  mail: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><rect x="2" y="4" width="20" height="16" rx="2"/><path d="m22 7-10 6L2 7"/></svg>',
  clipboard: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><rect x="8" y="2" width="8" height="4" rx="1"/><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/><path d="M9 12h6M9 16h6"/></svg>',
  heart: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.29 1.5 4.04 3 5.5l7 7Z"/></svg>',
  folder: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M4 20h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.93a2 2 0 0 1-1.66-.9l-.82-1.2A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z"/></svg>',
  cap: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M2 9l10-5 10 5-10 5z"/><path d="M6 11v5c0 1.5 3 3 6 3s6-1.5 6-3v-5"/><path d="M22 9v5"/></svg>',
  library: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M4 3h3v18H4z"/><path d="M9 3h3v18H9z"/><path d="m15.5 4.2 2.9.8-4.2 15.4-2.9-.8z"/></svg>',
  kit: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="m7.5 4.27 9 5.15M21 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/><path d="m3.3 7 8.7 5 8.7-5M12 22V12"/></svg>',
};

export const NAV_GROUPS: Group[] = [
  { key: 'community', labelFr: 'Communauté', labelEn: 'Community', mods: [
    { id: 'students', route: '/students', iconBg: 'bg-rose-100 text-rose-700', color: 'from-rose-500 to-rose-700', svg: NAV_ICONS.users, subFr: 'Élèves, parents, familles', subEn: 'Students, parents, families' },
    { id: 'journey', route: '/journey', iconBg: 'bg-teal-100 text-teal-700', color: 'from-teal-500 to-teal-700', svg: NAV_ICONS.route, subFr: 'Parcours scolaire pluriannuel', subEn: 'Multi-year school journey' },
    { id: 'health', route: '/health', iconBg: 'bg-red-100 text-red-700', color: 'from-red-500 to-red-700', svg: NAV_ICONS.heart, subFr: 'Carnet médical, infirmerie, clubs', subEn: 'Medical record, infirmary, clubs' },
    { id: 'documents', route: '/documents', iconBg: 'bg-stone-100 text-stone-700', color: 'from-stone-500 to-stone-700', svg: NAV_ICONS.folder, subFr: 'Dossier, pièces & orientation', subEn: 'Dossier, files & guidance' },
    { id: 'hr', route: '/staff', iconBg: 'bg-violet-100 text-violet-700', color: 'from-violet-500 to-violet-700', svg: NAV_ICONS.building, subFr: 'Personnel & ressources humaines', subEn: 'Staff & human resources' },
  ]},
  { key: 'education', labelFr: 'Pédagogie', labelEn: 'Education', mods: [
    { id: 'academic', route: '/academic', iconBg: 'bg-emerald-100 text-emerald-700', color: 'from-emerald-500 to-emerald-700', svg: NAV_ICONS.book, subFr: 'Notes, bulletins, procès-verbaux', subEn: 'Grades, report cards, master sheets' },
    { id: 'presence', route: '/presence', iconBg: 'bg-amber-100 text-amber-700', color: 'from-amber-500 to-amber-700', svg: NAV_ICONS.fingerprint, subFr: 'Empreinte digitale, SMS auto', subEn: 'Biometric, auto SMS' },
    { id: 'discipline', route: '/discipline', iconBg: 'bg-orange-100 text-orange-700', color: 'from-orange-500 to-orange-700', svg: NAV_ICONS.shield, subFr: 'Incidents, SMS parents', subEn: 'Incidents, parent SMS' },
    { id: 'coursebook', route: '/coursebook', iconBg: 'bg-lime-100 text-lime-700', color: 'from-lime-500 to-lime-700', svg: NAV_ICONS.clipboard, subFr: 'Cahier de textes & devoirs', subEn: 'Class log & homework' },
    { id: 'promotion', route: '/promotion', iconBg: 'bg-teal-100 text-teal-700', color: 'from-teal-500 to-teal-700', svg: NAV_ICONS.cap, subFr: 'Fin d’année, admis & redoublants', subEn: 'End of year, promotions & repeats' },
  ]},
  { key: 'operations', labelFr: 'Opérations', labelEn: 'Operations', mods: [
    { id: 'finance', route: '/finance', iconBg: 'bg-gold-50 text-gold-600', color: 'from-gold-400 to-gold-600', svg: NAV_ICONS.wallet, subFr: 'Recettes, dépenses, débiteurs', subEn: 'Revenue, expenses, debtors' },
    { id: 'timetable', route: '/timetable', iconBg: 'bg-cyan-100 text-cyan-700', color: 'from-cyan-500 to-cyan-700', svg: NAV_ICONS.calendar, subFr: 'Grilles, créneaux, conflits', subEn: 'Grids, slots, conflicts' },
    { id: 'events', route: '/events', iconBg: 'bg-pink-100 text-pink-700', color: 'from-pink-500 to-pink-700', svg: NAV_ICONS.bell, subFr: 'Annonces & notifications parents', subEn: 'Announcements & parent alerts' },
    { id: 'messages', route: '/messages', iconBg: 'bg-sky-100 text-sky-700', color: 'from-sky-500 to-sky-700', svg: NAV_ICONS.mail, subFr: 'Carnet de correspondance parents', subEn: 'Parent correspondence book' },
    { id: 'library', route: '/library', iconBg: 'bg-indigo-100 text-indigo-700', color: 'from-indigo-500 to-indigo-700', svg: NAV_ICONS.library, subFr: 'Documents partagés · personnel & parents', subEn: 'Shared documents · staff & parents' },
    { id: 'classkit', route: '/classkit', iconBg: 'bg-fuchsia-100 text-fuchsia-700', color: 'from-fuchsia-500 to-fuchsia-700', svg: NAV_ICONS.kit, subFr: 'Fournitures & livres par classe', subEn: 'Supplies & books per class' },
  ]},
  { key: 'steering', labelFr: 'Pilotage', labelEn: 'Steering', mods: [
    { id: 'dashboard', route: '/dashboard', iconBg: 'bg-brand-100 text-brand-700', color: 'from-brand-500 to-brand-700', svg: NAV_ICONS.home, subFr: "Vue d'ensemble · KPIs", subEn: 'Overview · KPIs' },
    { id: 'alerts', route: '/alerts', iconBg: 'bg-amber-100 text-amber-700', color: 'from-amber-500 to-amber-700', svg: NAV_ICONS.alert, subFr: 'Élèves à risque · alertes auto', subEn: 'At-risk students · auto alerts' },
    { id: 'reports', route: '/reports', iconBg: 'bg-indigo-100 text-indigo-700', color: 'from-indigo-500 to-indigo-700', svg: NAV_ICONS.chart, subFr: 'Analytique école entière', subEn: 'School-wide analytics' },
    { id: 'settings', route: '/settings', iconBg: 'bg-slate-100 text-slate-700', color: 'from-slate-500 to-slate-700', svg: NAV_ICONS.settings, subFr: 'Configuration, rôles, lecteur', subEn: 'Config, roles, reader' },
  ]},
];

/** Flat lookup of every module, keyed by id. */
export const MOD_BY_ID: Record<string, Mod> = Object.fromEntries(
  NAV_GROUPS.flatMap((g) => g.mods).map((m) => [m.id, m]),
);

/** localStorage key holding the most-recently opened module ids (most recent first). */
export const RECENT_MODS_KEY = 'bbc.recent.mods';
