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
} from './parent.api';

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
            <bbc-status-pill [status]="pillStatus(sel.feeStatus)" [label]="feeStatusLabel(sel.feeStatus)" />
          </div>
        </bbc-card>

        <bbc-tabs [tabs]="tabs()" [value]="tab()" (change)="tab.set($any($event))" />

        @switch (tab()) {
          @case ('overview') {
            <div class="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-5">
              <bbc-kpi tone="ok" icon="fingerprint"
                [label]="fr() ? 'Taux de présence' : 'Attendance rate'"
                [value]="sel.attendanceRate + ' %'" />
              <bbc-kpi [tone]="sel.balance > 0 ? 'warn' : 'ok'" icon="wallet"
                [label]="fr() ? 'Solde de frais' : 'Fee balance'"
                [value]="money(sel.balance)"
                [sub]="sel.balance > 0 ? (fr() ? 'à régler' : 'outstanding') : (fr() ? 'à jour' : 'up to date')" />
              <bbc-kpi tone="neutral" icon="cash"
                [label]="fr() ? 'Statut frais' : 'Fee status'"
                [value]="sel.feeStatus" />
              <bbc-kpi tone="gold" icon="book"
                [label]="fr() ? 'Notes' : 'Grades'"
                [value]="grades().length"
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

          @case ('grades') {
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
  protected suggestions = signal<SuggestionView[]>([]);
  protected supplies = signal<ClassResourceView | null>(null);
  protected books = signal<ClassResourceView | null>(null);
  protected tab = signal<'overview' | 'grades' | 'resources' | 'suggest'>('overview');

  protected fr = () => this.i18n.lang() === 'fr';
  protected money = fmtMoney;

  protected tabs = computed(() => [
    { id: 'overview', label: this.fr() ? 'Vue d’ensemble' : 'Overview' },
    { id: 'grades', label: this.fr() ? 'Notes' : 'Grades' },
    { id: 'resources', label: this.fr() ? 'Fournitures & manuels' : 'Supplies & textbooks' },
    { id: 'suggest', label: this.fr() ? 'Boîte à suggestions' : 'Suggestion box' },
  ]);

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

  constructor() {
    this.school.ensureLoaded();
    this.api.children().subscribe((cs) => {
      this.children.set(cs);
      const first = cs[0];
      if (first) {
        this.select(first);
      }
    });
    this.reloadSuggestions();
  }

  protected select(child: ChildView): void {
    this.selected.set(child);
    this.grades.set([]);
    this.supplies.set(null);
    this.books.set(null);
    this.api.grades(child.studentId).subscribe((g) => this.grades.set(g));
    this.api.resources(child.studentId, 'supplies').subscribe((r) => this.supplies.set(r));
    this.api.resources(child.studentId, 'books').subscribe((r) => this.books.set(r));
  }

  protected send(): void {
    if (!this.draft.category || !this.draft.message.trim()) return;
    this.api.addSuggestion(this.draft).subscribe(() => {
      this.draft = this.blank();
      this.reloadSuggestions();
    });
  }

  private reloadSuggestions(): void {
    this.api.mySuggestions().subscribe((s) => this.suggestions.set(s));
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
