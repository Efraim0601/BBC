import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { AttendanceApi } from './attendance.api';
import { I18nService } from '../../core/i18n.service';
import { AuthService } from '../../core/auth.service';
import { FoundationApi, AcademicSessionView } from '../../core/foundation.api';
import { AttendanceAnalytics, AttendanceClass, AttendanceDevice, AttendancePolicy,
  AttendanceRoster, AttendanceRosterMark, AttendanceSessionSummary, DeviceReconciliation, RollStatus } from '../../core/models';
import { CardComponent, EmptyComponent, PageHeaderComponent } from '../../core/ui';

type Tab = 'roll' | 'analytics' | 'devices' | 'settings';

@Component({
  selector: 'bbc-attendance',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CardComponent, EmptyComponent, PageHeaderComponent],
  styles: [`
    .field { width:100%; min-height:40px; padding:.55rem .75rem; border:1px solid #cbd5e1; border-radius:.6rem; background:#fff; color:#172033; outline:none; }
    .field:hover { border-color:#94a3b8; }
    .field:focus { border-color:#2563eb; box-shadow:0 0 0 3px rgba(37,99,235,.12); }
    .field.invalid { border-color:#e11d48; box-shadow:0 0 0 3px rgba(225,29,72,.1); }
    .label { display:block; font-size:.7rem; font-weight:700; text-transform:uppercase; letter-spacing:.05em; color:#64748b; margin-bottom:.35rem; }
    .btn { min-height:38px; border-radius:.6rem; padding:.5rem .85rem; font-size:.8rem; font-weight:700; border:1px solid #cbd5e1; background:white; color:#334155; }
    .btn:hover { background:#f8fafc; border-color:#94a3b8; }
    .btn.primary { background:#1d4ed8; border-color:#1d4ed8; color:white; }
    .btn.danger { background:#be123c; border-color:#be123c; color:white; }
    .btn:disabled { opacity:.45; cursor:not-allowed; }
    .status-btn { min-width:74px; padding:.42rem .6rem; border:1px solid #cbd5e1; border-radius:.55rem; font-size:.72rem; font-weight:700; background:white; }
    .status-btn.active-present { color:#047857; background:#ecfdf5; border-color:#34d399; }
    .status-btn.active-absent { color:#be123c; background:#fff1f2; border-color:#fb7185; }
    .status-btn.active-late { color:#a16207; background:#fffbeb; border-color:#fbbf24; }
    .status-btn.active-excused { color:#6d28d9; background:#f5f3ff; border-color:#a78bfa; }
  `],
  template: `
    <div class="fade-in max-w-7xl mx-auto">
      <bbc-page-header [title]="fr() ? 'Présences' : 'Attendance'"
        [subtitle]="fr() ? 'Appel quotidien ou par période, analyses et contrôle des pointages' : 'Daily or period roll call, analytics and device control'" />

      <div class="flex gap-2 mb-5 overflow-x-auto pb-1">
        @for (item of tabs; track item.key) {
          <button class="btn whitespace-nowrap" [class.primary]="tab() === item.key" (click)="selectTab(item.key)">
            {{ fr() ? item.fr : item.en }}
          </button>
        }
      </div>

      @if (notice()) {
        <div class="mb-4 px-4 py-3 rounded-lg border text-sm font-medium"
          [class.bg-rose-50]="noticeType() === 'error'" [class.border-rose-200]="noticeType() === 'error'"
          [class.text-rose-800]="noticeType() === 'error'" [class.bg-emerald-50]="noticeType() !== 'error'"
          [class.border-emerald-200]="noticeType() !== 'error'" [class.text-emerald-800]="noticeType() !== 'error'">
          {{ notice() }}
        </div>
      }

      @if (tab() === 'roll') {
        <bbc-card [title]="fr() ? 'Ouvrir une liste d’appel' : 'Open a class roster'"
          [subtitle]="fr() ? 'Les classes du primaire utilisent DAILY; le secondaire suit les périodes publiées.' : 'Primary classes use DAILY; secondary classes follow published timetable periods.'">
          <div class="grid md:grid-cols-3 gap-4">
            <label><span class="label">{{ fr() ? 'Date' : 'Date' }} <b class="text-rose-600">*</b></span>
              <input class="field" type="date" [value]="date()" (change)="setDate($any($event.target).value)" />
            </label>
            <label><span class="label">{{ fr() ? 'Classe' : 'Class' }} <b class="text-rose-600">*</b></span>
              <select class="field" [value]="classId()" (change)="selectClass($any($event.target).value)">
                <option value="">{{ fr() ? 'Sélectionner une classe' : 'Select a class' }}</option>
                @for (c of classes(); track c.id) { <option [value]="c.id">{{ c.name }} · {{ c.model }} · {{ c.enrolledCount }} {{ fr() ? 'élève(s)' : 'student(s)' }}</option> }
              </select>
            </label>
            @if (selectedClass()?.model === 'PERIOD') {
              <label><span class="label">{{ fr() ? 'Période / matière' : 'Period / subject' }} <b class="text-rose-600">*</b></span>
                <select class="field" [value]="periodKey()" (change)="selectPeriod($any($event.target).value)">
                  <option value="">{{ fr() ? 'Sélectionner une période' : 'Select a period' }}</option>
                  @for (s of sessionOptions(); track s.id) {
                    <option [value]="s.periodKey">{{ s.periodKey }} · {{ s.subjectCode || '—' }} · {{ sessionStatus(s.status) }}</option>
                  }
                </select>
              </label>
            }
          </div>
          @if (activeSession(); as session) {
            <div class="mt-4 rounded-lg border border-blue-200 bg-blue-50 px-3 py-2 text-sm text-blue-900">
              {{ fr() ? 'Année active' : 'Active session' }}: <b>{{ session.label }}</b> ({{ session.startDate }} → {{ session.endDate }}).
              @if (!dateInActiveSession()) { {{ fr() ? ' La date choisie est hors de cette année : choisissez une date comprise dans cette plage.' : ' The selected date is outside this session: choose a date within this range.' }} }
            </div>
          }
          @if (selectedClass(); as selected) {
            <div class="mt-3 text-sm" [class.text-amber-700]="selected.enrolledCount === 0" [class.text-slate-600]="selected.enrolledCount > 0">
              @if (selected.enrolledCount === 0) { {{ fr() ? 'Cette classe ne compte aucun élève activement inscrit dans l’année active.' : 'This class has no actively enrolled students in the active session.' }} }
              @else { {{ selected.enrolledCount }} {{ fr() ? 'élève(s) actif(s) dans cette classe.' : 'active student(s) in this class.' }} }
            </div>
          }
          @if (selectedClass()?.model === 'PERIOD' && sessionOptions().length === 0 && dateInActiveSession()) {
            <div class="mt-3 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">{{ fr() ? 'Aucune période publiée pour cette classe et cette date. Créez et publiez un créneau dans Emploi du temps avant de faire l’appel.' : 'No published period exists for this class and date. Create and publish a timetable slot before taking attendance.' }}</div>
          }
        </bbc-card>

        @if (roster(); as r) {
          <div class="mt-5">
            <bbc-card [title]="r.session.className + ' · ' + (r.session.subjectCode || (fr() ? 'Appel quotidien' : 'Daily roll call'))"
              [subtitle]="r.session.date + ' · ' + r.session.periodKey + ' · ' + sessionStatus(r.session.status)">
              <div action class="flex gap-2 flex-wrap">
                @if (r.session.status !== 'FINALIZED') {
                  <button class="btn" (click)="markAllPresent()">{{ fr() ? 'Tous présents' : 'All present' }}</button>
                  <button class="btn primary" (click)="save()" [disabled]="busy()">{{ fr() ? 'Enregistrer' : 'Save' }}</button>
                  <button class="btn" (click)="finalize()" [disabled]="busy()">{{ fr() ? 'Finaliser' : 'Finalize' }}</button>
                } @else {
                  @if (canReopen) { <button class="btn" (click)="openReopen()">{{ fr() ? 'Rouvrir avec motif' : 'Reopen with reason' }}</button> }
                }
              </div>

              <div class="grid grid-cols-2 md:grid-cols-5 gap-3 mb-4">
                <div class="rounded-lg bg-slate-50 border border-slate-200 p-3"><div class="label">{{ fr() ? 'Effectif' : 'Roster' }}</div><b class="text-xl">{{ r.marks.length }}</b></div>
                @for (status of ['present','absent','late','excused']; track status) {
                  <div class="rounded-lg bg-slate-50 border border-slate-200 p-3"><div class="label">{{ statusLabel($any(status)) }}</div><b class="text-xl">{{ count($any(status)) }}</b></div>
                }
              </div>

              <div class="overflow-x-auto -mx-5">
                <table class="w-full text-sm min-w-[1050px]">
                  <thead class="bg-slate-50 border-y border-slate-200"><tr class="text-left text-[11px] uppercase tracking-wide text-slate-500">
                    <th class="py-3 pl-5">{{ fr() ? 'Élève' : 'Student' }}</th><th>{{ fr() ? 'Statut' : 'Status' }}</th>
                    <th>{{ fr() ? 'Motif' : 'Reason' }}</th><th>{{ fr() ? 'Note' : 'Note' }}</th><th class="pr-5">{{ fr() ? 'Source' : 'Source' }}</th>
                  </tr></thead>
                  <tbody>
                    @for (m of r.marks; track m.studentId; let i = $index) {
                      <tr class="border-b border-slate-100 align-top">
                        <td class="py-3 pl-5 pr-4"><div class="font-semibold text-slate-900">{{ m.studentName }}</div><div class="text-xs text-slate-500 font-mono">{{ m.matricule }}</div></td>
                        <td class="py-3 pr-4"><div class="flex gap-1.5 flex-wrap">
                          @for (s of statuses; track s) {
                            <button class="status-btn" [class]="statusClass(m.status, s)" (click)="setStatus(i, s)" [disabled]="r.session.status === 'FINALIZED'">{{ statusLabel(s) }}</button>
                          }
                        </div></td>
                        <td class="py-3 pr-4 min-w-52">
                          <input class="field" [class.invalid]="invalidReason(m)" [value]="m.reason || ''"
                            (input)="setText(i, 'reason', $any($event.target).value)" [disabled]="r.session.status === 'FINALIZED'"
                            [placeholder]="fr() ? 'Motif de l’absence' : 'Absence reason'" />
                          @if (invalidReason(m)) { <div class="text-xs text-rose-600 mt-1">{{ fr() ? 'Le motif est obligatoire.' : 'A reason is required.' }}</div> }
                        </td>
                        <td class="py-3 pr-4 min-w-52"><input class="field" [value]="m.note || ''" (input)="setText(i, 'note', $any($event.target).value)" [disabled]="r.session.status === 'FINALIZED'" [placeholder]="fr() ? 'Observation facultative' : 'Optional note'" /></td>
                        <td class="py-4 pr-5 text-xs text-slate-500 uppercase">{{ m.source }}</td>
                      </tr>
                    } @empty { <tr><td colspan="5"><bbc-empty icon="users" [label]="fr() ? 'Aucun élève inscrit dans cette classe pour cette année.' : 'No enrolled student in this class for this session.'" /></td></tr> }
                  </tbody>
                </table>
              </div>

              @if (r.events.length) {
                <div class="mt-5 border-t border-slate-200 pt-4"><div class="label">{{ fr() ? 'Historique de l’appel' : 'Roll-call history' }}</div>
                  @for (event of r.events; track event.occurredAt) {
                    <div class="text-xs text-slate-600 py-1"><b>{{ event.action }}</b> · {{ event.actor }} · {{ event.occurredAt }} @if(event.reason){ · {{ event.reason }} }</div>
                  }
                </div>
              }
            </bbc-card>
          </div>
        } @else if (classId() && !busy()) {
          <div class="mt-5"><bbc-card><bbc-empty icon="users" [label]="!dateInActiveSession() ? (fr() ? 'Choisissez une date dans l’année scolaire active.' : 'Choose a date within the active academic session.') : selectedClass()?.enrolledCount === 0 ? (fr() ? 'Cette classe ne compte aucun élève actif pour cette année.' : 'This class has no active student for this session.') : selectedClass()?.model === 'PERIOD' ? (fr() ? 'Sélectionnez une période publiée dans l’emploi du temps.' : 'Select a published timetable period.') : (fr() ? 'Aucune séance disponible pour cette date.' : 'No session available for this date.')" /></bbc-card></div>
        }
      }

      @if (tab() === 'analytics') {
        <bbc-card [title]="fr() ? 'Analyse des présences' : 'Attendance analytics'"
          [subtitle]="fr() ? 'Le dénominateur inclut toutes les séances attendues, même non marquées.' : 'The denominator includes every expected session, including unmarked ones.'">
          <div class="grid md:grid-cols-4 gap-4 mb-4">
            <label><span class="label">{{ fr() ? 'Du' : 'From' }} <b class="text-rose-600">*</b></span><input class="field" type="date" [value]="from()" (change)="from.set($any($event.target).value)" /></label>
            <label><span class="label">{{ fr() ? 'Au' : 'To' }} <b class="text-rose-600">*</b></span><input class="field" type="date" [value]="to()" (change)="to.set($any($event.target).value)" /></label>
            <label><span class="label">{{ fr() ? 'Classe' : 'Class' }}</span><select class="field" [value]="analyticsClassId()" (change)="analyticsClassId.set($any($event.target).value)"><option value="">{{ fr() ? 'Toutes les classes' : 'All classes' }}</option>@for(c of classes();track c.id){<option [value]="c.id">{{ c.name }}</option>}</select></label>
            <div class="flex items-end gap-2"><button class="btn primary flex-1" (click)="loadAnalytics()">{{ fr() ? 'Calculer' : 'Calculate' }}</button><button class="btn" (click)="scanAlerts()">{{ fr() ? 'Créer alertes' : 'Create alerts' }}</button></div>
          </div>
          @if (analytics(); as a) {
            <div class="grid grid-cols-2 md:grid-cols-5 gap-3 mb-5">
              <div class="rounded-lg border border-blue-200 bg-blue-50 p-3"><div class="label">{{ fr() ? 'Taux' : 'Rate' }}</div><b class="text-2xl text-blue-800">{{ a.attendancePercent }}%</b></div>
              <div class="rounded-lg border border-slate-200 p-3"><div class="label">{{ fr() ? 'Attendues' : 'Expected' }}</div><b class="text-xl">{{ a.expected }}</b></div>
              <div class="rounded-lg border border-emerald-200 p-3"><div class="label">{{ fr() ? 'Présents' : 'Present' }}</div><b class="text-xl text-emerald-700">{{ a.present }}</b></div>
              <div class="rounded-lg border border-rose-200 p-3"><div class="label">{{ fr() ? 'Absents' : 'Absent' }}</div><b class="text-xl text-rose-700">{{ a.absent }}</b></div>
              <div class="rounded-lg border border-amber-200 p-3"><div class="label">{{ fr() ? 'Non marqués' : 'Unmarked' }}</div><b class="text-xl text-amber-700">{{ a.unmarked }}</b></div>
            </div>
            <div class="overflow-x-auto -mx-5"><table class="w-full text-sm"><thead class="bg-slate-50 border-y border-slate-200"><tr class="text-left text-[11px] uppercase text-slate-500"><th class="pl-5 py-3">{{ fr() ? 'Élève' : 'Student' }}</th><th>{{ fr() ? 'Classe' : 'Class' }}</th><th>{{ fr() ? 'Attendues' : 'Expected' }}</th><th>{{ fr() ? 'Abs.' : 'Abs.' }}</th><th>{{ fr() ? 'Retards' : 'Late' }}</th><th>{{ fr() ? 'Non marqués' : 'Unmarked' }}</th><th class="pr-5">%</th></tr></thead><tbody>
              @for(row of a.students;track row.studentId){<tr class="border-b border-slate-100"><td class="pl-5 py-3"><b>{{ row.studentName }}</b><div class="text-xs text-slate-500">{{ row.matricule }}</div></td><td>{{ row.className }}</td><td>{{ row.expected }}</td><td>{{ row.absent }}</td><td>{{ row.late }}</td><td>{{ row.unmarked }}</td><td class="pr-5 font-bold" [class.text-rose-700]="row.attendancePercent < 80">{{ row.attendancePercent }}%</td></tr>}
            </tbody></table></div>
          }
        </bbc-card>
      }

      @if (tab() === 'devices') {
        <div class="grid lg:grid-cols-3 gap-5">
          <bbc-card className="lg:col-span-1" [title]="fr() ? 'Lecteurs' : 'Readers'">
            @for(d of devices();track d.id){<div class="border border-slate-200 rounded-lg p-3 mb-2"><div class="flex justify-between"><b>{{ d.label }}</b><span class="text-xs font-bold" [class.text-emerald-700]="d.online" [class.text-rose-700]="!d.online">{{ d.online ? (fr()?'EN LIGNE':'ONLINE') : (fr()?'HORS LIGNE':'OFFLINE') }}</span></div><div class="text-xs text-slate-500 mt-1">{{ d.location }} · {{ d.model }}</div><div class="text-xs text-slate-500">{{ fr() ? 'Dernier contact' : 'Last seen' }}: {{ d.lastSeenAt || '—' }}</div></div>}
          </bbc-card>
          <bbc-card className="lg:col-span-2" [title]="fr() ? 'Réconciliation des pointages' : 'Device reconciliation'" [subtitle]="fr() ? 'Un scan reste une preuve distincte jusqu’à son association à une séance.' : 'A scan remains separate evidence until linked to a roll-call session.'">
            <div action><input class="field" type="date" [value]="deviceDate()" (change)="deviceDate.set($any($event.target).value); loadDevices()" /></div>
            @for(d of reconciliation();track d.deviceRecordId){<div class="flex items-center gap-3 border-b border-slate-100 py-3"><div class="flex-1"><b>{{ d.studentName }}</b><div class="text-xs text-slate-500">{{ d.className }} · {{ d.checkInTime }} · {{ d.status }}</div></div>@if(d.reconciled){<span class="text-xs font-bold text-emerald-700">{{ fr() ? 'RÉCONCILIÉ' : 'RECONCILED' }}</span>}@else{<button class="btn" [disabled]="!roster()" (click)="reconcile(d)">{{ roster() ? (fr()?'Associer à la séance ouverte':'Link to open session') : (fr()?'Ouvrez d’abord la séance':'Open a session first') }}</button>}</div>} @empty {<bbc-empty icon="fingerprint" [label]="fr() ? 'Aucun pointage lecteur pour cette date.' : 'No reader scans for this date.'" />}
          </bbc-card>
        </div>
      }

      @if (tab() === 'settings') {
        <div class="grid lg:grid-cols-2 gap-5">
          <bbc-card [title]="fr() ? 'Politiques par niveau' : 'Policies by level'" [subtitle]="fr() ? 'Le modèle est imposé pour éviter des données incompatibles.' : 'The model is enforced to prevent incompatible data.'">
            @for(p of policies();track p.level;let i=$index){<div class="border border-slate-200 rounded-xl p-4 mb-3"><div class="flex justify-between mb-3"><b class="capitalize">{{ p.level }}</b><span class="text-xs font-bold bg-slate-100 px-2 py-1 rounded">{{ p.model }}</span></div><div class="grid grid-cols-2 gap-3"><label><span class="label">{{ fr() ? 'Retard après (min)' : 'Late after (min)' }}</span><input class="field" type="number" min="0" [value]="p.lateAfterMinutes" (input)="policyNumber(i,'lateAfterMinutes',$any($event.target).value)" /></label><label><span class="label">{{ fr() ? 'Alerte absence (%)' : 'Absence alert (%)' }}</span><input class="field" type="number" min="0" max="100" [value]="p.chronicAbsencePercent" (input)="policyNumber(i,'chronicAbsencePercent',$any($event.target).value)" /></label></div><label class="flex gap-2 items-center mt-3 text-sm"><input type="checkbox" [checked]="p.requireAbsenceReason" (change)="policyBoolean(i,$any($event.target).checked)" />{{ fr() ? 'Motif obligatoire pour absent/excusé' : 'Require reason for absent/excused' }}</label><button class="btn primary mt-3" (click)="savePolicy(p)">{{ fr() ? 'Enregistrer cette politique' : 'Save this policy' }}</button></div>}
          </bbc-card>
          <bbc-card [title]="fr() ? 'Génération des séances' : 'Session generation'" [subtitle]="fr() ? 'La prévisualisation ne modifie rien. Générer synchronise les séances attendues avec le calendrier et l’emploi du temps.' : 'Preview changes nothing. Generate synchronizes expected sessions with the calendar and timetable.'">
            <div class="grid grid-cols-2 gap-3"><label><span class="label">{{ fr() ? 'Du' : 'From' }} <b class="text-rose-600">*</b></span><input class="field" type="date" [value]="generateFrom()" (change)="generateFrom.set($any($event.target).value)" /></label><label><span class="label">{{ fr() ? 'Au' : 'To' }} <b class="text-rose-600">*</b></span><input class="field" type="date" [value]="generateTo()" (change)="generateTo.set($any($event.target).value)" /></label></div><div class="flex gap-2 mt-4"><button class="btn" (click)="generate(true)">{{ fr() ? 'Prévisualiser' : 'Preview' }}</button><button class="btn primary" (click)="openGenerate()">{{ fr() ? 'Générer les séances' : 'Generate sessions' }}</button></div>
            @if(generationResult();as g){<div class="mt-4 border border-blue-200 bg-blue-50 rounded-lg p-4 text-sm text-blue-900"><b>{{ g.expectedSessions }} {{ fr() ? 'séances attendues' : 'expected sessions' }}</b><div class="mt-1">{{ g.preview ? (fr()?'Prévisualisation uniquement : aucune donnée modifiée.':'Preview only: no data changed.') : (g.synchronizedSessions + (fr()?' séances synchronisées.':' sessions synchronized.')) }}</div></div>}
          </bbc-card>
        </div>
      }
    </div>

    @if (modal()) {
      <div class="fixed inset-0 z-50 bg-slate-950/45 flex items-center justify-center p-4" (click)="closeModal()">
        <div class="bg-white rounded-xl shadow-2xl max-w-md w-full p-5" (click)="$event.stopPropagation()">
          <h3 class="text-lg font-bold text-slate-900">{{ modal() === 'reopen' ? (fr()?'Rouvrir l’appel finalisé ?':'Reopen finalized roll call?') : (fr()?'Générer les séances attendues ?':'Generate expected sessions?') }}</h3>
          <p class="text-sm text-slate-600 mt-2">{{ modal() === 'reopen' ? (fr()?'Les marques pourront être modifiées. Le motif et toutes les corrections resteront dans l’historique.':'Marks will become editable. The reason and all corrections remain in the audit history.') : (fr()?'Cette action crée ou synchronise les séances à partir du calendrier et de l’emploi du temps. Elle ne marque aucun élève automatiquement.':'This creates or synchronizes sessions from the calendar and timetable. It does not mark any student automatically.') }}</p>
          @if(modal() === 'reopen'){<label class="block mt-4"><span class="label">{{ fr() ? 'Motif' : 'Reason' }} <b class="text-rose-600">*</b></span><textarea class="field min-h-24" [class.invalid]="modalAttempted() && !modalReason().trim()" [value]="modalReason()" (input)="modalReason.set($any($event.target).value)"></textarea>@if(modalAttempted()&&!modalReason().trim()){<div class="text-xs text-rose-600 mt-1">{{ fr()?'Le motif est obligatoire.':'A reason is required.' }}</div>}</label>}
          <div class="flex justify-end gap-2 mt-5"><button class="btn" (click)="closeModal()">{{ fr()?'Annuler':'Cancel' }}</button><button class="btn" [class.danger]="modal()==='reopen'" [class.primary]="modal()==='generate'" (click)="confirmModal()">{{ modal()==='reopen' ? (fr()?'Rouvrir':'Reopen') : (fr()?'Générer':'Generate') }}</button></div>
        </div>
      </div>
    }
  `,
})
export class AttendanceComponent {
  private api = inject(AttendanceApi);
  private auth = inject(AuthService);
  private foundation = inject(FoundationApi);
  protected i18n = inject(I18nService);
  protected fr = () => this.i18n.lang() === 'fr';
  protected canConfigure = this.auth.can('settings', 'write');
  protected canReopen = ['principal','prefect'].includes(this.auth.user()?.role || '');
  protected tabs: {key: Tab; fr: string; en: string}[] = ([
    {key:'roll',fr:'Liste d’appel',en:'Roll call'}, {key:'analytics',fr:'Analyses',en:'Analytics'},
    {key:'devices',fr:'Lecteurs & rapprochement',en:'Devices & reconciliation'}, {key:'settings',fr:'Configuration',en:'Settings'},
  ] as {key: Tab; fr: string; en: string}[]).filter(x => !['devices','settings'].includes(x.key) || this.canConfigure);
  protected statuses: RollStatus[] = ['present','absent','late','excused'];
  protected tab = signal<Tab>('roll');
  protected today = new Date().toISOString().slice(0,10);
  protected date = signal(this.today); protected classId = signal(''); protected periodKey = signal('');
  protected classes = signal<AttendanceClass[]>([]); protected sessionOptions = signal<AttendanceSessionSummary[]>([]);
  protected roster = signal<AttendanceRoster|null>(null); protected policies = signal<AttendancePolicy[]>([]);
  protected busy = signal(false); protected attempted = signal(false); protected notice = signal('');
  protected noticeType = signal<'ok'|'error'>('ok');
  protected from = signal(this.today.slice(0,8)+'01'); protected to = signal(this.today); protected analyticsClassId = signal('');
  protected analytics = signal<AttendanceAnalytics|null>(null);
  protected devices = signal<AttendanceDevice[]>([]); protected reconciliation = signal<DeviceReconciliation[]>([]); protected deviceDate = signal(this.today);
  protected generateFrom = signal(this.today); protected generateTo = signal(this.today); protected generationResult = signal<any>(null);
  protected modal = signal<'reopen'|'generate'|null>(null); protected modalReason = signal(''); protected modalAttempted = signal(false);
  protected selectedClass = computed(() => this.classes().find(c => c.id === this.classId()) || null);
  protected activeSession = signal<AcademicSessionView|null>(null);
  protected dateInActiveSession = computed(() => { const s=this.activeSession(); return !!s && this.date() >= s.startDate && this.date() <= s.endDate; });
  protected currentPolicy = computed(() => this.policies().find(p => p.level === this.selectedClass()?.level));

  constructor() {
    this.api.classes().subscribe({next:v=>this.classes.set(v),error:e=>this.fail(e)});
    this.api.policies().subscribe({next:v=>this.policies.set(v),error:e=>this.fail(e)});
    this.foundation.currentSession().subscribe({next:s=>{ this.activeSession.set(s); if (!this.dateInRange(this.date(), s)) this.applySuggestedDate(s); },error:e=>this.fail(e)});
  }
  protected selectTab(tab: Tab): void { this.tab.set(tab); this.clearNotice(); if(tab==='analytics') this.loadAnalytics(); if(tab==='devices') this.loadDevices(); }
  protected setDate(v:string):void { if(!v)return; this.date.set(v); this.periodKey.set(''); this.roster.set(null); if(this.classId() && this.dateInActiveSession()) this.loadSessionOptions(); }
  protected selectClass(id:string):void { this.classId.set(id); this.periodKey.set(''); this.roster.set(null); if(id && this.dateInActiveSession()) this.loadSessionOptions(); else this.sessionOptions.set([]); }
  private loadSessionOptions():void { this.busy.set(true); this.api.sessions(this.classId(),this.date()).subscribe({next:s=>{this.sessionOptions.set(s);this.busy.set(false);if(this.selectedClass()?.model==='DAILY')this.loadRoster();},error:e=>{this.busy.set(false);this.fail(e);}}); }
  protected selectPeriod(key:string):void { this.periodKey.set(key); if(key)this.loadRoster(); else this.roster.set(null); }
  private loadRoster():void { this.busy.set(true);this.api.roster(this.classId(),this.date(),this.periodKey()||undefined).subscribe({next:r=>{this.roster.set(r);this.busy.set(false);this.attempted.set(false);},error:e=>{this.busy.set(false);this.fail(e);}}); }
  protected setStatus(i:number,status:RollStatus):void { this.updateMark(i,{status,lateMinutes:status==='late'?Math.max(1,this.currentPolicy()?.lateAfterMinutes||1):0}); }
  protected setText(i:number,key:'reason'|'note',value:string):void { this.updateMark(i,{[key]:value}); }
  private updateMark(i:number,change:Partial<AttendanceRosterMark>):void { this.roster.update(r=>r?({...r,marks:r.marks.map((m,x)=>x===i?({...m,...change}):m)}):r); }
  protected markAllPresent():void { this.roster.update(r=>r?({...r,marks:r.marks.map(m=>({...m,status:'present',lateMinutes:0}))}):r); }
  protected invalidReason(m:AttendanceRosterMark):boolean { return this.attempted() && !!this.currentPolicy()?.requireAbsenceReason && ['absent','excused'].includes(m.status) && !m.reason?.trim(); }
  protected save():void { const r=this.roster();if(!r)return;this.attempted.set(true);if(r.marks.some(m=>this.invalidReason(m))){this.error(this.fr()?'Corrigez les motifs obligatoires indiqués en rouge.':'Complete the required reasons highlighted in red.');return;}this.busy.set(true);this.api.save(r).subscribe({next:v=>{this.roster.set(v);this.busy.set(false);this.ok(this.fr()?'Liste enregistrée.':'Roster saved.');},error:e=>{this.busy.set(false);this.fail(e);}}); }
  protected finalize():void { const r=this.roster();if(!r)return;if(r.marks.some(m=>m.status==='unmarked')){this.error(this.fr()?'Tous les élèves doivent être marqués avant la finalisation.':'Every student must be marked before finalization.');return;}this.busy.set(true);this.api.finalize(r.session.id,r.session.version).subscribe({next:v=>{this.roster.set(v);this.busy.set(false);this.ok(this.fr()?'Appel finalisé et verrouillé.':'Roll call finalized and locked.');},error:e=>{this.busy.set(false);this.fail(e);}}); }
  protected openReopen():void { this.modalReason.set('');this.modalAttempted.set(false);this.modal.set('reopen'); }
  protected openGenerate():void { this.modalAttempted.set(false);this.modal.set('generate'); }
  protected closeModal():void { this.modal.set(null); }
  protected confirmModal():void { if(this.modal()==='reopen'){this.modalAttempted.set(true);if(!this.modalReason().trim())return;const r=this.roster();if(!r)return;this.api.reopen(r.session.id,r.session.version,this.modalReason()).subscribe({next:v=>{this.roster.set(v);this.closeModal();this.ok(this.fr()?'Appel rouvert. Le motif est conservé dans l’historique.':'Roll call reopened. The reason is stored in history.');},error:e=>this.fail(e)});}else{this.closeModal();this.generate(false);} }
  protected count(status:RollStatus):number { return this.roster()?.marks.filter(m=>m.status===status).length||0; }
  protected statusLabel(s:RollStatus):string { const x:any={present:['Présent','Present'],absent:['Absent','Absent'],late:['Retard','Late'],excused:['Excusé','Excused'],unmarked:['Non marqué','Unmarked']};return x[s]?.[this.fr()?0:1]||s; }
  protected statusClass(current:RollStatus,target:RollStatus):string { return current===target?'status-btn active-'+target:'status-btn'; }
  protected sessionStatus(s:string):string { const x:any={DRAFT:['Brouillon','Draft'],FINALIZED:['Finalisé','Finalized'],REOPENED:['Rouvert','Reopened']};return x[s]?.[this.fr()?0:1]||s; }
  protected loadAnalytics():void { this.api.analytics(this.from(),this.to(),this.analyticsClassId()||undefined).subscribe({next:v=>this.analytics.set(v),error:e=>this.fail(e)}); }
  protected scanAlerts():void { this.api.scanAlerts(this.from(),this.to()).subscribe({next:v=>this.ok((this.fr()?'Alertes créées ou actualisées : ':'Alerts created or updated: ')+v.createdOrUpdated),error:e=>this.fail(e)}); }
  protected loadDevices():void { this.api.devices().subscribe({next:v=>this.devices.set(v),error:e=>this.fail(e)});this.api.reconciliation(this.deviceDate()).subscribe({next:v=>this.reconciliation.set(v),error:e=>this.fail(e)}); }
  protected reconcile(d:DeviceReconciliation):void { const r=this.roster();if(!r)return;this.api.reconcile(d.deviceRecordId,r.session.id).subscribe({next:v=>{this.roster.set(v);this.loadDevices();this.ok(this.fr()?'Pointage associé à la séance ouverte.':'Scan linked to the open session.');},error:e=>this.fail(e)}); }
  protected policyNumber(i:number,key:'lateAfterMinutes'|'chronicAbsencePercent',v:string):void { this.policies.update(p=>p.map((x,n)=>n===i?({...x,[key]:Number(v)}):x)); }
  protected policyBoolean(i:number,v:boolean):void { this.policies.update(p=>p.map((x,n)=>n===i?({...x,requireAbsenceReason:v}):x)); }
  protected savePolicy(p:AttendancePolicy):void { this.api.updatePolicy(p.level,p).subscribe({next:v=>{this.policies.update(all=>all.map(x=>x.level===v.level?v:x));this.ok(this.fr()?'Politique enregistrée.':'Policy saved.');},error:e=>this.fail(e)}); }
  protected generate(preview:boolean):void { if(!this.generateFrom()||!this.generateTo()){this.error(this.fr()?'Les deux dates sont obligatoires.':'Both dates are required.');return;}this.api.generate(this.generateFrom(),this.generateTo(),preview).subscribe({next:v=>{this.generationResult.set(v);this.ok(preview?(this.fr()?'Prévisualisation terminée : aucune donnée modifiée.':'Preview complete: no data changed.'):(this.fr()?'Séances synchronisées. Aucun élève n’a été marqué automatiquement.':'Sessions synchronized. No student was marked automatically.'));},error:e=>this.fail(e)}); }
  private dateInRange(value:string, session:AcademicSessionView):boolean { return value >= session.startDate && value <= session.endDate; }
  private applySuggestedDate(session:AcademicSessionView):void { const today=this.today; let suggested=this.dateInRange(today,session) ? today : session.startDate; const day=new Date(suggested+'T12:00:00').getDay(); if(day===0) suggested=this.addDays(suggested,1); else if(day===6) suggested=this.addDays(suggested,2); if(suggested > session.endDate) suggested=session.endDate; this.date.set(suggested); this.from.set(session.startDate); this.to.set(session.endDate); this.generateFrom.set(session.startDate); this.generateTo.set(session.endDate); }
  private addDays(value:string, days:number):string { const d=new Date(value+'T12:00:00'); d.setDate(d.getDate()+days); return d.toISOString().slice(0,10); }
  private fail(e:any):void { this.error(e?.error?.message||e?.error?.detail||e?.message||(this.fr()?'Une erreur est survenue.':'An error occurred.')); }
  private ok(m:string):void { this.noticeType.set('ok');this.notice.set(m); }
  private error(m:string):void { this.noticeType.set('error');this.notice.set(m); }
  private clearNotice():void { this.notice.set(''); }
}
