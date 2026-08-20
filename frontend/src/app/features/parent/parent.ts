import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { I18nService } from '../../core/i18n.service';
import { SchoolService } from '../../core/school.service';
import { AuthService } from '../../core/auth.service';
import {
  IconComponent, CardComponent, KpiComponent, EmptyComponent,
  AvatarComponent, TabsComponent, StatusPillComponent,
} from '../../core/ui';
import {
  ParentApi,
  ChildView,
  GradeView,
  SuggestionView,
  SuggestionRequest,
  ClassResourceView,
  StudentFeeStatementView,
  PaymentChannelView,
  PublishedBulletinView,
  ParentJourneyEventView,
  ParentAttendanceView,
  ParentDisciplineView,
  ParentHealthView,
  ParentEventView,
  ParentNoticeView,
  ResourceView,
} from './parent.api';
import { openBlob, fmtBytes } from '../library/library.api';

interface CategoryOption {
  value: string;
  labelFr: string;
  labelEn: string;
  icon: string;
  pill: string;
}

const fmtMoney = (n: number) => `${Math.round(n).toLocaleString('fr-FR')} FCFA`;

@Component({
  selector: 'bbc-parent',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule, DecimalPipe, IconComponent, CardComponent, KpiComponent, EmptyComponent,
    AvatarComponent, TabsComponent, StatusPillComponent,
  ],
  template: `
    <div class="fade-in max-w-6xl mx-auto">
      <!-- Hero -->
      <div class="relative bg-gradient-to-br from-brand-700 via-brand-600 to-brand-800 text-white rounded-xl2 px-7 py-6 mb-6 overflow-hidden shadow-card">
        <div class="absolute -top-16 -right-12 w-72 h-72 rounded-full bg-gold-400/20 blur-3xl"></div>
        <div class="relative flex items-center gap-4">
          <div class="w-12 h-12 bg-white/15 rounded-xl flex items-center justify-center shrink-0 text-white">
            <bbc-icon name="users" [s]="26" />
          </div>
          <div class="flex-1 min-w-0">
            <div class="text-[11px] uppercase tracking-[0.18em] text-gold-200 font-bold">
              {{ fr() ? 'Espace parent' : 'Parent space' }}
            </div>
            <div class="font-display text-2xl font-bold leading-tight">
              {{ (fr() ? 'Bonjour' : 'Hello') + (parentName() ? ' ' + parentName() : '') }}
            </div>
            <div class="text-sm text-white/80">
              {{ fr() ? 'Suivez la scolarité de vos enfants en un coup d’œil.' : 'Follow your children’s school life at a glance.' }}
            </div>
          </div>
          <div class="hidden sm:block text-right">
            <div class="text-3xl font-bold leading-none">{{ children().length }}</div>
            <div class="text-[11px] uppercase tracking-wide text-white/70">
              {{ fr() ? 'enfant(s)' : 'child(ren)' }}
            </div>
          </div>
        </div>
      </div>

      <!-- Child selector -->
      @if (children().length > 0) {
        <div class="flex items-center gap-2 mb-5 flex-wrap">
          <span class="text-xs font-semibold text-mute uppercase">
            {{ fr() ? 'Mes enfants' : 'My children' }}:
          </span>
          @for (c of children(); track c.studentId) {
            <button (click)="select(c)"
              class="flex items-center gap-2 pl-1.5 pr-3 py-1.5 rounded-full border transition"
              [class]="selected()?.studentId === c.studentId
                ? 'border-gold-400 bg-gold-50'
                : 'border-slate-200 hover:border-gold-300 bg-white'">
              <bbc-avatar [name]="c.name" [hue]="hueFor(c.studentId)" [size]="26" />
              <span class="text-sm font-semibold text-ink">{{ c.name }}</span>
              <span class="text-[11px] text-mute">{{ c.className }}</span>
            </button>
          }
        </div>
      }

      @if (selected(); as sel) {
        <!-- Child header card -->
        <bbc-card className="mb-5">
          <div class="flex items-center gap-4">
            <bbc-avatar [name]="sel.name" [hue]="hueFor(sel.studentId)" [size]="56" />
            <div class="flex-1 min-w-0">
              <div class="text-lg font-bold text-ink">{{ sel.name }}</div>
              <div class="text-sm text-mute">{{ sel.matricule }} · {{ sel.className }}</div>
            </div>
            @if (sel.financeVisible) {
              <bbc-status-pill [status]="pillStatus(sel.feeStatus ?? '')" [label]="feeStatusLabel(sel.feeStatus ?? '')" />
            } @else {
              <span class="text-xs text-white/70">{{ fr() ? 'Frais non partagés' : 'Fees not shared' }}</span>
            }
          </div>
        </bbc-card>

        <bbc-tabs [tabs]="tabs()" [value]="tab()" (change)="tab.set($any($event))" />

        @switch (tab()) {
          @case ('overview') {
            <div class="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-5">
              <bbc-kpi tone="ok" icon="fingerprint"
                [label]="fr() ? 'Taux de présence' : 'Attendance rate'"
                [value]="sel.attendanceVisible ? (sel.attendanceRate + ' %') : (fr() ? 'Non partagé' : 'Not shared')" />
              <bbc-kpi [tone]="sel.financeVisible && sel.balance > 0 ? 'warn' : 'ok'" icon="wallet"
                [label]="fr() ? 'Solde de frais' : 'Fee balance'"
                [value]="sel.financeVisible ? money(sel.balance) : (fr() ? 'Non partagé' : 'Not shared')"
                [sub]="sel.financeVisible ? (sel.balance > 0 ? (fr() ? 'à régler' : 'outstanding') : (fr() ? 'à jour' : 'up to date')) : ''" />
              <bbc-kpi tone="neutral" icon="cash"
                [label]="fr() ? 'Statut frais' : 'Fee status'"
                [value]="sel.financeVisible ? (sel.feeStatus ?? '') : (fr() ? 'Non partagé' : 'Not shared')" />
              <bbc-kpi tone="gold" icon="book"
                [label]="fr() ? 'Notes' : 'Grades'"
                [value]="publishedBulletin()?.lines?.length ?? 0"
                [sub]="fr() ? 'évaluation(s)' : 'assessment(s)'" />
            </div>

            @if (contacts().length) {
              <bbc-card [title]="fr() ? 'Coordonnées école' : 'School contacts'"
                [subtitle]="school.profile()?.name ?? ''">
                <div class="space-y-2.5">
                  @for (c of contacts(); track c.label) {
                    <div class="flex items-center gap-3 p-2.5 rounded-lg bg-slate-50">
                      <div class="w-8 h-8 rounded-md bg-white flex items-center justify-center text-brand-600">
                        <bbc-icon [name]="c.icon" [s]="15" />
                      </div>
                      <div class="min-w-0">
                        <div class="text-[10px] uppercase text-mute font-semibold">{{ c.label }}</div>
                        <div class="text-sm font-semibold text-ink truncate">{{ c.value }}</div>
                      </div>
                    </div>
                  }
                </div>
              </bbc-card>
            }
          }

          @case ('journey') {
            <bbc-card [title]="fr() ? 'Parcours officiel' : 'Official journey'" [subtitle]="fr() ? 'Résultats publiés et décisions visibles pour la famille.' : 'Published results and family-visible decisions.'">
              @if (journeyEvents().length === 0) { <bbc-empty icon="route" [label]="fr() ? 'Aucun événement officiel publié.' : 'No published official event yet.'" /> }
              @else { <div class="space-y-2">@for (event of journeyEvents(); track event.id) { <div class="rounded-lg border border-slate-200 p-3"><div class="flex items-center justify-between gap-2"><span class="font-semibold text-ink">{{ eventLabel(event.eventType) }}</span><span class="text-xs text-mute">{{ event.occurredAt ? fmtDate(event.occurredAt) : '—' }}</span></div><div class="text-xs text-mute mt-1">{{ event.sessionLabel || '—' }} @if (event.className) { · {{ event.className }} }</div>@if (event.average != null) { <div class="text-sm font-bold text-brand-700 mt-1">{{ event.average }}/20</div> } @if (event.decision) { <div class="text-sm text-ink mt-1">{{ event.decision }}</div> }</div> } </div> }
            </bbc-card>
          }

          @case ('school') {
            <div class="grid grid-cols-12 gap-4">
              <bbc-card className="col-span-12 lg:col-span-6"
                [title]="fr() ? 'Presence' : 'Attendance'"
                [subtitle]="attendance() ? (attendance()!.attendanceRate + '%') : 'Loading...'">
                @if (attendance(); as a) {
                  <div class="grid grid-cols-4 gap-2 mb-4 text-center">
                    <div class="rounded-lg bg-emerald-50 p-2"><div class="text-lg font-bold text-emerald-700">{{ a.present }}</div><div class="text-[10px] text-mute">Present</div></div>
                    <div class="rounded-lg bg-rose-50 p-2"><div class="text-lg font-bold text-rose-700">{{ a.absent }}</div><div class="text-[10px] text-mute">Absent</div></div>
                    <div class="rounded-lg bg-amber-50 p-2"><div class="text-lg font-bold text-amber-700">{{ a.late }}</div><div class="text-[10px] text-mute">Late</div></div>
                    <div class="rounded-lg bg-slate-50 p-2"><div class="text-lg font-bold text-ink">{{ a.excused }}</div><div class="text-[10px] text-mute">Excused</div></div>
                  </div>
                  @if (a.records.length === 0) { <bbc-empty icon="check" label="No finalized attendance to show." /> }
                  @else { <div class="space-y-2">@for (r of a.records; track r.id) { <div class="flex items-center justify-between rounded-lg border border-slate-100 px-3 py-2 text-sm"><span class="font-semibold text-ink">{{ r.date }}</span><span class="text-mute">{{ r.status }} @if (r.lateMinutes) { · {{ r.lateMinutes }} min }</span></div> }</div> }
                }
              </bbc-card>

              <bbc-card className="col-span-12 lg:col-span-6" title="Discipline"
                [subtitle]="discipline().length + ' visible incident(s)'">
                @if (discipline().length === 0) { <bbc-empty icon="shield" label="No parent-visible incident." /> }
                @else { <div class="space-y-2">@for (d of discipline(); track d.id) { <div class="rounded-lg border border-slate-100 p-3"><div class="flex justify-between gap-2"><b class="text-ink">{{ d.type }}</b><span class="text-xs text-mute">{{ d.incidentDate }}</span></div><div class="text-sm text-mute mt-1">{{ d.description }}</div>@if (d.sanction) { <div class="text-xs text-brand-700 mt-1">{{ d.sanction }}</div> }</div> }</div> }
              </bbc-card>

              <bbc-card className="col-span-12 lg:col-span-6" title="Health — parent-safe visits"
                subtitle="Confidential records are never exposed.">
                @if (health(); as h) {
                  @if (h.visits.length === 0) { <bbc-empty icon="heart" label="No visit to show." /> }
                  @else { <div class="space-y-2">@for (v of h.visits; track v.id) { <div class="rounded-lg border border-slate-100 p-3"><div class="flex justify-between gap-2"><b class="text-ink">{{ v.reason }}</b><span class="text-xs text-mute">{{ v.visitDate }}</span></div><div class="text-sm text-mute mt-1">{{ v.treatment }}</div></div> }</div> }
                }
              </bbc-card>

              <bbc-card className="col-span-12 lg:col-span-6" title="School life"
                [subtitle]="events().length + ' event(s)'">
                @if (events().length === 0) { <bbc-empty icon="calendar" label="No event for this child." /> }
                @else { <div class="space-y-2">@for (e of events(); track e.id) { <div class="rounded-lg border border-slate-100 p-3"><div class="flex justify-between gap-2"><b class="text-ink">{{ e.title }}</b><span class="text-xs text-mute">{{ e.eventDate }}</span></div><div class="text-xs text-mute mt-1">{{ e.type }}</div><div class="text-sm text-mute mt-1">{{ e.description }}</div></div> }</div> }
              </bbc-card>

              <bbc-card className="col-span-12" title="Correspondence"
                [subtitle]="notices().length + ' notice(s)'">
                @if (notices().length === 0) { <bbc-empty icon="mail" label="No correspondence." /> }
                @else { <div class="space-y-2">@for (n of notices(); track n.id) { <div class="rounded-lg border border-slate-100 p-3"><div class="flex flex-wrap items-center justify-between gap-2"><b class="text-ink">{{ n.subject }}</b><span class="text-xs text-mute">{{ fmtDate(n.createdAt) }}</span></div><div class="text-sm text-mute mt-1">{{ n.body }}</div><div class="flex items-center justify-between gap-2 mt-2"><span class="text-xs text-mute">{{ n.senderName }}</span>@if (n.requiresAck) { <button type="button" (click)="ackNotice(n)" [disabled]="n.acknowledged" class="rounded-lg px-3 py-1.5 text-xs font-semibold" [class]="n.acknowledged ? 'bg-emerald-50 text-emerald-700' : 'bg-brand-600 text-white hover:bg-brand-700'">{{ n.acknowledged ? 'Acknowledged' : 'Acknowledge' }}</button> }</div></div> }</div> }
              </bbc-card>
            </div>
          }

          @case ('fees') {
            @if (statement(); as st) {
              <!-- Où en est la scolarité : total, réglé, reste dû -->
              <div class="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-5">
                <bbc-kpi tone="neutral" icon="wallet"
                  [label]="fr() ? 'Frais de la classe' : 'Class fees'"
                  [value]="money(st.total)"
                  [sub]="st.className + (st.gridSource === 'class' ? (fr() ? ' · grille de classe' : ' · class grid') : '')" />
                <bbc-kpi tone="ok" icon="cash"
                  [label]="fr() ? 'Déjà réglé' : 'Already paid'"
                  [value]="money(st.paid)" [sub]="st.progressPct + '%'" />
                <bbc-kpi [tone]="st.balance > 0 ? 'warn' : 'ok'" icon="receipt"
                  [label]="fr() ? 'Reste à payer' : 'Outstanding'"
                  [value]="money(st.balance)" />
                @if (nextTranche(); as nt) {
                  <bbc-kpi [tone]="nt.overdue ? 'bad' : 'gold'" icon="clock"
                    [label]="fr() ? 'Prochaine tranche' : 'Next installment'"
                    [value]="money(nt.remaining)"
                    [sub]="nt.label + (nt.dueOn ? (fr() ? ' · avant le ' : ' · by ') + nt.dueOn : '')" />
                } @else {
                  <bbc-kpi tone="ok" icon="check"
                    [label]="fr() ? 'Prochaine tranche' : 'Next installment'"
                    [value]="fr() ? 'Aucune' : 'None'" [sub]="fr() ? 'scolarité à jour' : 'fees up to date'" />
                }
              </div>

              <!-- Échéancier de la classe -->
              <bbc-card className="mb-5"
                [title]="fr() ? 'Échéancier' : 'Payment schedule'"
                [subtitle]="fr() ? 'Tranches de la classe ' + st.className : 'Installments for class ' + st.className">
                @if (st.tranches.length === 0) {
                  <bbc-empty icon="wallet"
                    [label]="fr() ? 'L’école n’a pas encore publié d’échéancier pour cette classe.'
                                  : 'The school has not published a schedule for this class yet.'" />
                } @else {
                  <div class="space-y-2">
                    @for (t of st.tranches; track t.index) {
                      <div class="flex flex-wrap items-center gap-3 rounded-xl2 border p-3" [class]="trancheTone(t)">
                        <div class="w-9 h-9 rounded-lg bg-white/70 flex items-center justify-center font-bold text-sm text-ink shrink-0">
                          {{ t.index }}
                        </div>
                        <div class="min-w-0 flex-1">
                          <div class="font-semibold text-ink">{{ t.label }}</div>
                          <div class="text-[11px] text-mute">
                            @if (t.dueOn) {
                              {{ fr() ? 'À payer avant le' : 'Due by' }} {{ t.dueOn }}
                            } @else {
                              {{ fr() ? 'Sans date limite' : 'No due date' }}
                            }
                          </div>
                        </div>
                        <div class="text-right">
                          <div class="font-bold text-ink">{{ money(t.amount) }}</div>
                          @if (t.remaining > 0 && t.paid > 0) {
                            <div class="text-[11px] text-mute">{{ fr() ? 'reste' : 'left' }} {{ money(t.remaining) }}</div>
                          }
                        </div>
                        <span class="text-[11px] font-bold px-2 py-1 rounded-lg"
                          [class]="t.status === 'paid' ? 'bg-emerald-100 text-emerald-700'
                                   : t.overdue ? 'bg-rose-100 text-rose-700'
                                   : t.status === 'partial' ? 'bg-amber-100 text-amber-700'
                                   : 'bg-slate-100 text-slate-600'">
                          {{ trancheStatusLabel(t) }}
                        </span>
                      </div>
                    }
                  </div>
                }
              </bbc-card>

              <!-- Comment régler -->
              @if (channels().length) {
                <bbc-card className="mb-5"
                  [title]="fr() ? 'Comment payer' : 'How to pay'"
                  [subtitle]="fr() ? 'Moyens acceptés par l’école — conservez la référence de la transaction'
                                   : 'Methods accepted by the school — keep the transaction reference'">
                  <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
                    @for (c of channels(); track c.code) {
                      <div class="rounded-xl2 border border-slate-200 p-4">
                        <div class="font-semibold text-ink">{{ channelLabel(c) }}</div>
                        @if (c.accountRef) {
                          <div class="mt-1 text-sm">
                            <span class="text-mute">{{ fr() ? 'Compte' : 'Account' }} :</span>
                            <b class="font-mono text-ink">{{ c.accountRef }}</b>
                            @if (c.accountName) { <span class="text-mute"> · {{ c.accountName }}</span> }
                          </div>
                        }
                        @if (channelInstructions(c); as ins) {
                          <p class="text-[12px] text-mute mt-2 leading-relaxed">{{ ins }}</p>
                        }
                        @if (c.requiresReference) {
                          <div class="text-[11px] text-amber-700 mt-2 font-semibold">
                            {{ fr() ? 'Communiquez la référence de la transaction à l’économat.'
                                    : 'Give the transaction reference to the bursary.' }}
                          </div>
                        }
                      </div>
                    }
                  </div>
                </bbc-card>
              }

              <!-- Reçus -->
              <bbc-card [title]="fr() ? 'Mes versements' : 'My payments'"
                [subtitle]="st.payments.length + (fr() ? ' reçu(s)' : ' receipt(s)')">
                @if (st.payments.length === 0) {
                  <bbc-empty icon="receipt" [label]="fr() ? 'Aucun versement enregistré' : 'No payment recorded'" />
                } @else {
                  <div class="overflow-x-auto">
                    <table class="w-full text-sm">
                      <thead class="border-b border-slate-100">
                        <tr class="text-[11px] uppercase text-mute">
                          <th class="text-left font-semibold py-2">{{ fr() ? 'N° reçu' : 'Receipt N°' }}</th>
                          <th class="text-left font-semibold py-2">{{ fr() ? 'Date' : 'Date' }}</th>
                          <th class="text-left font-semibold py-2">{{ fr() ? 'Moyen' : 'Method' }}</th>
                          <th class="text-right font-semibold py-2">{{ fr() ? 'Montant' : 'Amount' }}</th>
                        </tr>
                      </thead>
                      <tbody>
                        @for (p of st.payments; track p.receiptNo) {
                          <tr class="border-b border-slate-50 last:border-0">
                            <td class="py-2.5 font-mono text-xs text-brand-600 font-semibold">{{ p.receiptNo }}</td>
                            <td class="py-2.5 text-mute">{{ p.paidOn }}</td>
                            <td class="py-2.5">
                              <div class="text-ink">{{ fr() ? p.methodLabelFr : p.methodLabelEn }}</div>
                              @if (p.reference) {
                                <div class="text-[11px] text-mute font-mono">{{ p.reference }}</div>
                              }
                            </td>
                            <td class="py-2.5 text-right font-bold text-emerald-700">{{ money(p.amount) }}</td>
                          </tr>
                        }
                      </tbody>
                    </table>
                  </div>
                }
              </bbc-card>
            } @else {
              <bbc-card>
                <bbc-empty icon="wallet" [label]="fr() ? 'Chargement de la situation…' : 'Loading fee statement…'" />
              </bbc-card>
            }
          }

          @case ('grades') {
            @if (publishedBulletin(); as b) {
              <bbc-card className="mb-5" [title]="(fr() ? 'Dernier bulletin publié' : 'Latest published report card')" [subtitle]="b.reportingPeriodCode + ' · ' + b.reportingPeriodLabel">
                <div class="flex flex-wrap items-center gap-4 mb-4">
                  <div class="rounded-lg bg-emerald-50 border border-emerald-200 px-4 py-2"><div class="text-[10px] uppercase text-emerald-700 font-semibold">{{ fr() ? 'Moyenne' : 'Average' }}</div><div class="text-xl font-bold text-emerald-700">{{ b.average }}/20</div></div>
                  <div class="rounded-lg bg-slate-50 px-4 py-2"><div class="text-[10px] uppercase text-mute font-semibold">{{ fr() ? 'Rang' : 'Rank' }}</div><div class="text-xl font-bold text-ink">{{ b.rank ?? '—' }}/{{ b.classSize }}</div></div>
                  @if (b.attendance) { <div class="rounded-lg bg-slate-50 px-4 py-2"><div class="text-[10px] uppercase text-mute font-semibold">{{ fr() ? 'Absences' : 'Absences' }}</div><div class="text-xl font-bold text-ink">{{ b.attendance.absentCount }}</div></div> }
                </div>
                <div class="overflow-x-auto"><table class="w-full text-sm"><thead class="border-b border-slate-100"><tr class="text-[11px] uppercase text-mute"><th class="text-left font-semibold py-2">{{ fr() ? 'Matière' : 'Subject' }}</th><th class="text-center font-semibold py-2">Coef</th><th class="text-right font-semibold py-2">{{ fr() ? 'Note' : 'Mark' }}</th><th class="text-left font-semibold py-2">{{ fr() ? 'Remarque' : 'Remark' }}</th></tr></thead><tbody>@for (l of b.lines; track l.subjectLabel) {<tr class="border-b border-slate-50"><td class="py-2.5 font-semibold text-ink">{{ l.subjectLabel }}</td><td class="py-2.5 text-center text-mute">{{ l.coefficient }}</td><td class="py-2.5 text-right font-bold">{{ l.mark }}/20</td><td class="py-2.5 pl-3 text-xs italic text-mute">{{ l.teacherRemark || l.appreciation }}</td></tr>}</tbody></table></div>
              </bbc-card>
            }
            <bbc-card
              [title]="(fr() ? 'Dernières notes' : 'Latest grades')"
              [subtitle]="sel.className">
              @if (grades().length === 0) {
                <bbc-empty icon="book" [label]="fr() ? 'Aucune note' : 'No grades'" />
              } @else {
                <table class="w-full text-sm">
                  <thead class="border-b border-slate-100">
                    <tr class="text-[11px] uppercase text-mute">
                      <th class="text-left font-semibold py-2">{{ fr() ? 'Matière' : 'Subject' }}</th>
                      <th class="text-center font-semibold py-2">{{ fr() ? 'Coef' : 'Coef' }}</th>
                      <th class="text-center font-semibold py-2">{{ fr() ? 'Séquence' : 'Sequence' }}</th>
                      <th class="text-right font-semibold py-2">{{ fr() ? 'Note/20' : 'Mark/20' }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (g of grades(); track $index) {
                      <tr class="border-b border-slate-50 last:border-0 hover:bg-slate-50/50">
                        <td class="py-2.5 font-semibold text-ink">{{ subjectLabel(g) }}</td>
                        <td class="py-2.5 text-center text-mute">{{ g.coef }}</td>
                        <td class="py-2.5 text-center text-mute">{{ g.sequence }}</td>
                        <td class="py-2.5 text-right font-bold"
                          [class]="g.mark < 10 ? 'text-rose-700' : g.mark < 14 ? 'text-ink' : 'text-emerald-700'">
                          {{ g.mark }}/20
                        </td>
                      </tr>
                    }
                    <tr class="bg-brand-50 font-bold border-t-2 border-brand-600">
                      <td class="py-2.5 text-brand-700" colspan="3">
                        {{ fr() ? 'Moyenne pondérée' : 'Weighted average' }}
                      </td>
                      <td class="py-2.5 text-right"
                        [class]="gradeAvg() < 10 ? 'text-rose-700' : 'text-brand-700'">
                        {{ gradeAvg().toFixed(2) }}/20
                      </td>
                    </tr>
                  </tbody>
                </table>
              }
            </bbc-card>
          }

          @case ('resources') {
            <div class="grid grid-cols-12 gap-4">
              <bbc-card className="col-span-12 lg:col-span-6"
                [title]="fr() ? 'Fournitures' : 'Supplies'" [subtitle]="sel.className">
                @if (supplies()?.published && supplies()!.items.length) {
                  <ul class="divide-y divide-slate-100">
                    @for (it of supplies()!.items; track it.id) {
                      <li class="flex items-center gap-3 py-2.5">
                        <bbc-icon name="check" [s]="15" [sw]="2.5" />
                        <span class="flex-1 text-sm font-semibold text-ink">{{ it.label }}</span>
                        @if (it.quantity) { <span class="text-xs text-mute">× {{ it.quantity }}</span> }
                        @if (it.note) { <span class="text-[11px] text-mute italic">{{ it.note }}</span> }
                      </li>
                    }
                  </ul>
                } @else {
                  <bbc-empty icon="book" [label]="fr() ? 'Aucune liste de fournitures publiée.' : 'No published supply list.'" />
                }
              </bbc-card>

              <bbc-card className="col-span-12 lg:col-span-6"
                [title]="fr() ? 'Manuels scolaires' : 'School textbooks'" [subtitle]="sel.className">
                @if (books()?.published && books()!.items.length) {
                  <table class="w-full text-sm">
                    <tbody>
                      @for (it of books()!.items; track it.id) {
                        <tr class="border-b border-slate-50 last:border-0">
                          <td class="py-2.5">
                            <div class="font-semibold text-ink">
                              {{ it.label }}
                              @if (it.mandatory === false) {
                                <span class="ml-1.5 text-[10px] font-semibold px-1.5 py-0.5 rounded bg-slate-100 text-slate-500">{{ fr() ? 'optionnel' : 'optional' }}</span>
                              }
                            </div>
                            @if (it.author) { <div class="text-[11px] text-mute">{{ it.author }}</div> }
                          </td>
                          <td class="py-2.5 text-right text-mute align-top whitespace-nowrap">{{ it.price != null ? (it.price | number) + ' FCFA' : '—' }}</td>
                        </tr>
                      }
                      @if (booksTotal() > 0) {
                        <tr class="bg-brand-50 font-bold border-t-2 border-brand-600">
                          <td class="py-2.5 text-brand-700">{{ fr() ? 'Total' : 'Total' }}</td>
                          <td class="py-2.5 text-right text-brand-700">{{ booksTotal() | number }} FCFA</td>
                        </tr>
                      }
                    </tbody>
                  </table>
                } @else {
                  <bbc-empty icon="book" [label]="fr() ? 'Aucune liste de manuels publiée.' : 'No published textbook list.'" />
                }
              </bbc-card>
            </div>
          }

          @case ('library') {
            <bbc-card [title]="fr() ? 'Documents de l’école' : 'School documents'"
              [subtitle]="fr() ? 'Mis à disposition des familles par la direction'
                               : 'Made available to families by the administration'">
              @if (!sharedResources().length) {
                <bbc-empty icon="doc"
                  [label]="fr() ? 'Aucun document pour le moment' : 'No document yet'" />
              } @else {
                <div class="space-y-2">
                  @for (r of sharedResources(); track r.id) {
                    <button type="button" (click)="openResource(r)"
                      class="w-full flex items-start gap-3 p-3 rounded-lg border border-slate-100 hover:bg-slate-50/60 hover:border-brand-200 transition text-left">
                      <span class="w-10 h-10 rounded-lg bg-brand-50 text-brand-600 flex items-center justify-center shrink-0">
                        <bbc-icon name="doc" [s]="18" />
                      </span>
                      <span class="flex-1 min-w-0">
                        <span class="block font-semibold text-ink">{{ r.title }}</span>
                        @if (r.description) {
                          <span class="block text-[12px] text-mute mt-0.5">{{ r.description }}</span>
                        }
                        <span class="block text-[11px] text-mute mt-1">
                          {{ r.fileName }} · {{ resourceSize(r.byteSize) }} · {{ fmtDate(r.publishedAt ?? r.createdAt) }}
                        </span>
                      </span>
                      <span class="w-8 h-8 rounded-lg text-mute flex items-center justify-center self-center shrink-0">
                        <bbc-icon name="download" [s]="16" />
                      </span>
                    </button>
                  }
                </div>
              }
            </bbc-card>
          }

          @case ('suggest') {
            <div class="grid grid-cols-12 gap-4">
              <bbc-card className="col-span-12 lg:col-span-6"
                [title]="fr() ? 'Boîte à suggestions' : 'Suggestion box'"
                [subtitle]="fr() ? 'Adressez un message à l’école' : 'Send a message to the school'">
                <div class="space-y-4">
                  <div>
                    <label class="block text-xs font-semibold text-mute uppercase tracking-wide mb-2">
                      {{ fr() ? 'Catégorie' : 'Category' }}
                    </label>
                    <div class="grid grid-cols-2 gap-2">
                      @for (o of categories; track o.value) {
                        <button type="button" (click)="draft.category = o.value"
                          class="flex items-center gap-2 px-3 py-2.5 rounded-lg border text-sm font-semibold transition"
                          [class]="draft.category === o.value
                            ? 'border-gold-400 bg-gold-50 text-gold-600'
                            : 'border-slate-200 text-mute hover:border-gold-300'">
                          <bbc-icon [name]="o.icon" [s]="14" /> {{ fr() ? o.labelFr : o.labelEn }}
                        </button>
                      }
                    </div>
                  </div>
                  <div>
                    <label class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">
                      {{ fr() ? 'Votre message' : 'Your message' }}
                    </label>
                    <textarea
                      [(ngModel)]="draft.message"
                      rows="5"
                      [placeholder]="fr() ? 'Au sujet de ' + sel.name + '…' : 'About ' + sel.name + '…'"
                      class="w-full p-3 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-gold-400 resize-none"></textarea>
                    <div class="text-[11px] text-mute mt-1">
                      {{ draft.message.length }} {{ fr() ? 'caractères' : 'chars' }}
                    </div>
                  </div>
                  <button type="button" (click)="send()"
                    [disabled]="!draft.message.trim()"
                    class="w-full inline-flex items-center justify-center gap-2 h-11 px-4 bg-gold-500 hover:bg-gold-600 disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm font-semibold rounded-lg transition">
                    <bbc-icon name="send" [s]="16" /> {{ fr() ? 'Envoyer le message' : 'Send message' }}
                  </button>
                </div>
              </bbc-card>

              <bbc-card className="col-span-12 lg:col-span-6"
                [title]="fr() ? 'Mes messages' : 'My messages'"
                [subtitle]="suggestions().length + (fr() ? ' message(s)' : ' message(s)')">
                @if (suggestions().length === 0) {
                  <bbc-empty icon="mail" [label]="fr() ? 'Aucun message envoyé' : 'No message sent'" />
                } @else {
                  <div class="space-y-2">
                    @for (s of suggestions(); track s.id) {
                      <div class="p-3 rounded-lg border border-slate-100">
                        <div class="flex items-center justify-between mb-1.5">
                          <span class="text-[10px] font-bold uppercase tracking-wide px-2 py-0.5 rounded"
                            [class]="categoryPill(s.category)">
                            {{ categoryLabel(s.category) }}
                          </span>
                          <span class="text-[11px] text-mute">{{ fmtDate(s.createdAt) }}</span>
                        </div>
                        <div class="text-sm text-ink">{{ s.message }}</div>
                        <div class="flex items-center gap-1.5 mt-2 text-[11px] font-semibold"
                          [class]="s.status === 'new' ? 'text-mute' : 'text-emerald-600'">
                          <bbc-icon [name]="s.status === 'new' ? 'clock' : 'check'" [s]="12" [sw]="2.5" />
                          {{ suggestionStatusLabel(s.status) }}
                        </div>
                      </div>
                    }
                  </div>
                }
              </bbc-card>
            </div>
          }
        }
      } @else {
        <bbc-card>
          <bbc-empty icon="users"
            [label]="fr() ? 'Aucun enfant rattaché à ce compte' : 'No child linked to this account'" />
        </bbc-card>
      }
    </div>
  `,
})
export class ParentComponent {
  protected i18n = inject(I18nService);
  protected school = inject(SchoolService);
  private auth = inject(AuthService);
  private api = inject(ParentApi);

  protected children = signal<ChildView[]>([]);
  protected selected = signal<ChildView | null>(null);
  protected grades = signal<GradeView[]>([]);
  protected publishedBulletin = signal<PublishedBulletinView | null>(null);
  protected suggestions = signal<SuggestionView[]>([]);
  protected supplies = signal<ClassResourceView | null>(null);
  protected books = signal<ClassResourceView | null>(null);
  protected tab = signal<'overview' | 'journey' | 'school' | 'fees' | 'grades' | 'resources' | 'library' | 'suggest'>('overview');
  /** Documents publiés par la direction à l'intention des familles. */
  protected sharedResources = signal<ResourceView[]>([]);
  /** Situation de scolarité de l'enfant sélectionné (grille de sa classe). */
  protected statement = signal<StudentFeeStatementView | null>(null);
  protected channels = signal<PaymentChannelView[]>([]);
  protected journeyEvents = signal<ParentJourneyEventView[]>([]);
  protected attendance = signal<ParentAttendanceView | null>(null);
  protected discipline = signal<ParentDisciplineView[]>([]);
  protected health = signal<ParentHealthView | null>(null);
  protected events = signal<ParentEventView[]>([]);
  protected notices = signal<ParentNoticeView[]>([]);

  protected fr = () => this.i18n.lang() === 'fr';
  protected money = fmtMoney;

  protected tabs = computed(() => [
    { id: 'overview', label: this.fr() ? 'Vue d’ensemble' : 'Overview' },
    { id: 'journey', label: this.fr() ? 'Parcours officiel' : 'Official journey' },
    { id: 'school', label: 'School life' },
    { id: 'fees', label: this.fr() ? 'Frais & paiements' : 'Fees & payments' },
    { id: 'grades', label: this.fr() ? 'Notes' : 'Grades' },
    { id: 'resources', label: this.fr() ? 'Fournitures & manuels' : 'Supplies & textbooks' },
    { id: 'library', label: this.fr() ? 'Documents de l’école' : 'School documents' },
    { id: 'suggest', label: this.fr() ? 'Boîte à suggestions' : 'Suggestion box' },
  ]);

  /** Prochaine tranche à régler : la première qui n'est pas soldée. */
  protected nextTranche = computed(() =>
    this.statement()?.tranches.find((t) => t.remaining > 0) ?? null);

  protected channelLabel(c: PaymentChannelView): string {
    return this.fr() ? c.labelFr : c.labelEn;
  }

  protected channelInstructions(c: PaymentChannelView): string | null {
    return (this.fr() ? c.instructionsFr : c.instructionsEn) ?? c.instructionsFr ?? null;
  }

  protected trancheTone(t: { status: string; overdue: boolean }): string {
    if (t.status === 'paid') return 'border-emerald-200 bg-emerald-50';
    if (t.overdue) return 'border-rose-200 bg-rose-50';
    if (t.status === 'partial') return 'border-amber-200 bg-amber-50';
    return 'border-slate-200 bg-white';
  }

  protected trancheStatusLabel(t: { status: string; overdue: boolean }): string {
    if (t.status === 'paid') return this.fr() ? 'Réglée' : 'Settled';
    if (t.overdue) return this.fr() ? 'En retard' : 'Overdue';
    if (t.status === 'partial') return this.fr() ? 'Partielle' : 'Partial';
    return this.fr() ? 'À venir' : 'Upcoming';
  }

  protected booksTotal = computed(() =>
    (this.books()?.items ?? []).reduce((sum, it) => sum + (it.price ?? 0), 0));

  /** Greet the parent by name, as the apps grid already does for staff. */
  protected parentName = computed(() => this.auth.user()?.displayName ?? '');

  /** Only the contacts the school actually filled in — no invented placeholders. */
  protected contacts = computed(() => {
    const p = this.school.profile();
    if (!p) return [];
    const out: { icon: string; label: string; value: string }[] = [];
    if (p.email) out.push({ icon: 'mail', label: 'Email', value: p.email });
    if (p.phone) out.push({ icon: 'phone', label: this.fr() ? 'Secrétariat' : 'Front office', value: p.phone });
    if (p.address || p.city) {
      out.push({
        icon: 'building',
        label: this.fr() ? 'Adresse' : 'Address',
        value: [p.address, this.school.location()].filter(Boolean).join(' — '),
      });
    }
    if (p.website) out.push({ icon: 'chart', label: this.fr() ? 'Site web' : 'Website', value: p.website });
    return out;
  });

  /**
   * Weighted by subject coefficient — Σ(mark×coef)/Σ(coef) — the same formula the server
   * uses for the bulletin. A plain mean here quietly disagreed with the official report card.
   */
  protected gradeAvg = computed(() => {
    const gs = this.grades();
    const coefSum = gs.reduce((a, g) => a + (g.coef || 0), 0);
    if (!coefSum) return 0;
    return gs.reduce((a, g) => a + g.mark * (g.coef || 0), 0) / coefSum;
  });

  protected subjectLabel(g: GradeView): string {
    return (this.fr() ? g.subjectLabelFr : g.subjectLabelEn) || g.subjectCode;
  }

  protected feeStatusLabel(s: string): string {
    const map: Record<string, [string, string]> = {
      paid: ['À jour', 'Up to date'],
      partial: ['Partiel', 'Partial'],
      unpaid: ['Impayé', 'Unpaid'],
    };
    const m = map[s];
    return m ? (this.fr() ? m[0] : m[1]) : s;
  }

  protected suggestionStatusLabel(s: string): string {
    const map: Record<string, [string, string]> = {
      new: ['En attente de lecture', 'Awaiting review'],
      read: ['Lu par l’école', 'Read by the school'],
      answered: ['Traité', 'Answered'],
      closed: ['Clôturé', 'Closed'],
    };
    const m = map[s];
    return m ? (this.fr() ? m[0] : m[1]) : s;
  }

  protected eventLabel(type: string): string {
    const labels: Record<string, [string, string]> = {
      PUBLISHED_RESULT: ['Résultat publié', 'Published result'],
      PROMOTION_ACTIVATED: ['Passage activé', 'Promotion activated'],
    };
    const value = labels[type];
    return value ? (this.fr() ? value[0] : value[1]) : (this.fr() ? 'Événement officiel' : 'Official event');
  }

  /** The API sends an ISO OffsetDateTime; showing it raw leaked "2026-07-16T09:12:33.14Z". */
  protected fmtDate(iso: string): string {
    const d = new Date(iso);
    if (isNaN(d.getTime())) return iso;
    return d.toLocaleDateString(this.fr() ? 'fr-FR' : 'en-GB', {
      day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
    });
  }

  protected readonly categories: readonly CategoryOption[] = [
    { value: 'suggestion', labelFr: 'Suggestion', labelEn: 'Suggestion', icon: 'spark', pill: 'bg-brand-100 text-brand-700' },
    { value: 'question', labelFr: 'Question', labelEn: 'Question', icon: 'mail', pill: 'bg-violet-100 text-violet-700' },
    { value: 'complaint', labelFr: 'Réclamation', labelEn: 'Complaint', icon: 'alertTri', pill: 'bg-rose-100 text-rose-700' },
    { value: 'thanks', labelFr: 'Remerciement', labelEn: 'Thanks', icon: 'star', pill: 'bg-emerald-100 text-emerald-700' },
  ];

  protected draft: SuggestionRequest = this.blank();

  /** Poids lisible du fichier — « 1,4 Mo » plutôt que 1468006. */
  protected resourceSize = (bytes: number) => fmtBytes(bytes, this.fr());

  /**
   * Ouvre un document de l'école. L'appel porte le jeton en en-tête, qu'un
   * simple lien ne transmettrait pas : on récupère donc les octets puis on
   * laisse le navigateur afficher ou enregistrer.
   */
  protected openResource(r: ResourceView): void {
    this.api.sharedResourceFile(r.id).subscribe((b) => openBlob(b, r.fileName));
  }

  constructor() {
    // School profile is a staff/settings capability. Parents still get the
    // portal without probing an endpoint they are not authorized to read.
    if (this.auth.canAction('SCHOOL_PROFILE_VIEW')) this.school.ensureLoaded();
    this.api.paymentChannels().subscribe({ next: (c) => this.channels.set(c), error: () => this.channels.set([]) });
    // Les documents de l'école ne dépendent pas de l'enfant sélectionné : ils
    // s'adressent aux familles, éventuellement bornés au cycle — c'est le
    // serveur qui applique ce filtre, à partir des enfants du compte.
    this.api.sharedResources().subscribe({
      next: (r) => this.sharedResources.set(r),
      error: () => this.sharedResources.set([]),
    });
    this.api.children().subscribe({
      next: (cs) => {
        this.children.set(cs);
        const first = cs[0];
        if (first) {
          this.select(first);
        }
      },
      error: () => this.children.set([]),
    });
    this.reloadSuggestions();
  }

  protected select(child: ChildView): void {
    this.selected.set(child);
    this.grades.set([]);
    this.publishedBulletin.set(null);
    this.supplies.set(null);
    this.books.set(null);
    this.statement.set(null);
    this.journeyEvents.set([]);
    this.attendance.set(null);
    this.discipline.set([]);
    this.health.set(null);
    this.events.set([]);
    this.notices.set([]);
    this.api.latestPublishedBulletin(child.studentId).subscribe({
      next: (b) => {
        this.publishedBulletin.set(b);
        // The parent portal derives this list from the immutable published
        // bulletin. It never loads raw grade rows.
        this.grades.set(b.lines.map((line) => ({
          subjectCode: line.subjectLabel, subjectLabelFr: line.subjectLabel, subjectLabelEn: line.subjectLabel,
          coef: line.coefficient, sequence: 0, mark: line.mark,
        })));
      },
      error: () => { this.publishedBulletin.set(null); this.grades.set([]); },
    });
    if (child.financeVisible) {
      this.api.fees(child.studentId).subscribe({
        next: (st) => this.statement.set(st),
        error: () => this.statement.set(null),
      });
    }
    this.api.resources(child.studentId, 'supplies').subscribe({
      next: (r) => this.supplies.set(r),
      error: () => this.supplies.set(null),
    });
    this.api.resources(child.studentId, 'books').subscribe({
      next: (r) => this.books.set(r),
      error: () => this.books.set(null),
    });
    this.api.journey(child.studentId).subscribe({
      next: (events) => this.journeyEvents.set(events),
      error: () => this.journeyEvents.set([]),
    });
    this.api.attendance(child.studentId).subscribe({
      next: (a) => this.attendance.set(a),
      error: () => this.attendance.set(null),
    });
    this.api.discipline(child.studentId).subscribe({
      next: (d) => this.discipline.set(d),
      error: () => this.discipline.set([]),
    });
    this.api.health(child.studentId).subscribe({
      next: (h) => this.health.set(h),
      error: () => this.health.set(null),
    });
    this.api.events(child.studentId).subscribe({
      next: (events) => this.events.set(events),
      error: () => this.events.set([]),
    });
    this.api.messages(child.studentId).subscribe({
      next: (messages) => this.notices.set(messages),
      error: () => this.notices.set([]),
    });
  }

  protected ackNotice(notice: ParentNoticeView): void {
    const child = this.selected();
    if (!child || notice.acknowledged) return;
    this.api.acknowledgeMessage(child.studentId, notice.id, this.parentName() || 'Parent').subscribe((updated) => {
      this.notices.update((items) => items.map((item) => item.id === updated.id ? updated : item));
    });
  }

  protected send(): void {
    if (!this.draft.category || !this.draft.message.trim()) return;
    this.api.addSuggestion(this.draft).subscribe(() => {
      this.draft = this.blank();
      this.reloadSuggestions();
    });
  }

  private reloadSuggestions(): void {
    this.api.mySuggestions().subscribe({
      next: (s) => this.suggestions.set(s),
      error: () => this.suggestions.set([]),
    });
  }

  protected categoryLabel(value: string): string {
    const c = this.categories.find((c) => c.value === value);
    return c ? (this.fr() ? c.labelFr : c.labelEn) : value;
  }

  protected categoryPill(value: string): string {
    return this.categories.find((c) => c.value === value)?.pill ?? 'bg-slate-100 text-slate-700';
  }

  protected pillStatus(feeStatus: string): string {
    const s = (feeStatus || '').toLowerCase();
    if (s.includes('paid') || s.includes('pay') || s.includes('jour')) return 'paid';
    if (s.includes('part')) return 'partial';
    if (s.includes('unpaid') || s.includes('impay')) return 'unpaid';
    return feeStatus;
  }

  protected hueFor(id: string): number {
    let h = 0;
    for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) % 360;
    return h;
  }

  private blank(): SuggestionRequest {
    return { category: 'suggestion', message: '' };
  }
}
