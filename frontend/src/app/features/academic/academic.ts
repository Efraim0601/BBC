import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { StudentApi } from '../students/students.api';
import { AcademicApi, BulletinView, PvView } from './academic.api';
import { AuthService } from '../../core/auth.service';
import { ScopeService } from '../../core/scope.service';
import { I18nService } from '../../core/i18n.service';
import { Student } from '../../core/models';
import { ApcBulletinComponent } from './apc-bulletin';
import {
  IconComponent, CardComponent, PageHeaderComponent, EmptyComponent,
  AvatarComponent, TabsComponent, ChipFilterComponent,
} from '../../core/ui';

type Mode = 'bulletin' | 'pv';

const appreciation = (avg: number, fr: boolean): string => {
  if (avg >= 16) return fr ? 'Excellent' : 'Excellent';
  if (avg >= 14) return fr ? 'Très bien' : 'Very good';
  if (avg >= 12) return fr ? 'Bien' : 'Good';
  if (avg >= 10) return fr ? 'Assez bien' : 'Fair';
  if (avg >= 8) return fr ? 'Passable' : 'Pass';
  return fr ? 'Insuffisant' : 'Insufficient';
};

@Component({
  selector: 'bbc-academic',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule, IconComponent, CardComponent, PageHeaderComponent,
    EmptyComponent, AvatarComponent, TabsComponent, ChipFilterComponent, ApcBulletinComponent,
  ],
  template: `
    <div class="fade-in max-w-6xl mx-auto">
      <bbc-page-header [title]="i18n.t('academic')"
        [subtitle]="fr() ? 'Saisie des notes, bulletins, procès-verbaux' : 'Grade entry, report cards, master sheets'">
        <div right class="flex items-center gap-2">
          @if (mode() === 'bulletin' && bulletin()) {
            <button (click)="print()"
              class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
              <bbc-icon name="printer" [s]="16" /> {{ fr() ? 'Imprimer' : 'Print' }}
            </button>
          }
          <button (click)="print()"
            class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white">
            <bbc-icon name="download" [s]="16" /> {{ fr() ? 'Exporter' : 'Export' }}
          </button>
        </div>
      </bbc-page-header>

      <!-- Mode tabs -->
      <bbc-tabs [tabs]="tabs()" [value]="mode()" (change)="setMode($any($event))" />

      <!-- Top toolbar -->
      <bbc-card className="mb-5 print:hidden">
        <div class="flex items-start gap-4 flex-wrap">
          <div class="flex-1 min-w-[280px]">
            <div class="text-xs font-semibold text-mute uppercase mb-2">
              @if (mode() === 'bulletin') {
                {{ fr() ? 'Élève' : 'Student' }}
              } @else {
                {{ fr() ? 'Classe' : 'Class' }}
              }
            </div>
            @if (mode() === 'bulletin') {
              <div class="relative">
                <select [ngModel]="selectedStudentId()" (ngModelChange)="onStudentChange($event)"
                  class="w-full h-10 px-3 rounded-lg border border-slate-200 text-sm bg-white text-ink focus:outline-none focus:border-brand-400">
                  <option value="">{{ fr() ? '— Choisir un élève —' : '— Choose a student —' }}</option>
                  @for (s of students(); track s.id) {
                    <option [value]="s.id">{{ s.name + ' — ' + s.className }}</option>
                  }
                </select>
              </div>
            } @else {
              <input [ngModel]="pvClassName()" (ngModelChange)="pvClassName.set($event)"
                [placeholder]="fr() ? 'Classe (ex: 6ème)' : 'Class (e.g. Form 1)'"
                class="w-full h-10 px-3 rounded-lg border border-slate-200 text-sm bg-white text-ink focus:outline-none focus:border-brand-400" />
            }
          </div>
          <div class="flex flex-col gap-2 shrink-0">
            <div class="text-xs font-semibold text-mute uppercase">{{ fr() ? 'Séquence' : 'Sequence' }}</div>
            <bbc-chip-filter allLabel="—" [value]="String(sequence())" [options]="seqOptions()"
              (change)="onSequenceChip($event)" />
          </div>
          @if (mode() === 'pv') {
            <div class="flex flex-col justify-end shrink-0">
              <div class="text-xs font-semibold text-transparent uppercase mb-2">.</div>
              <button (click)="loadPv()"
                class="inline-flex items-center gap-2 h-10 px-4 bg-brand-600 hover:bg-brand-700 text-white rounded-lg text-sm font-semibold">
                <bbc-icon name="doc" [s]="16" /> {{ fr() ? 'Charger le PV' : 'Load master sheet' }}
              </button>
            </div>
          }
        </div>
      </bbc-card>

      <!-- ============ BULLETIN ============ -->
      @if (mode() === 'bulletin') {
        @if (bulletin(); as b) {
          @if (isApc()) {
            <!-- Maternelle / Primaire — competency-based (APC) report card -->
            <bbc-card className="overflow-x-auto"><bbc-apc-bulletin [view]="b" /></bbc-card>
          } @else {
          <bbc-card className="overflow-hidden">
            <div class="bg-white rounded-xl2 overflow-hidden -m-5 print:m-0">
              <!-- Navy header -->
              <div class="bg-gradient-to-br from-brand-700 to-brand-800 text-white p-6 relative overflow-hidden">
                <div class="absolute -top-8 -right-8 w-40 h-40 rounded-full bg-gold-400/15 blur-2xl"></div>
                <div class="flex items-start gap-4 relative">
                  <div class="w-14 h-14 bg-white/95 rounded-lg flex items-center justify-center shrink-0 text-brand-700">
                    <bbc-icon name="cap" [s]="30" />
                  </div>
                  <div class="flex-1 min-w-0">
                    <div class="text-[10px] uppercase tracking-wider text-gold-200 font-semibold">
                      République du Cameroun · {{ fr() ? 'MINESEC' : 'MoSEd' }}
                    </div>
                    <div class="font-display text-xl font-bold leading-tight">Bayo Bilingual Complex</div>
                    <div class="text-sm text-brand-100 mt-0.5">Maroua</div>
                    <div class="text-xs text-gold-200 mt-2 font-semibold">
                      {{ (fr() ? 'BULLETIN' : 'REPORT CARD') }} — {{ (fr() ? 'SÉQUENCE ' : 'SEQ. ') + b.sequence }}
                    </div>
                  </div>
                  <div class="flex flex-col items-end gap-2">
                    @if (b.validated) {
                      <div class="bg-emerald-500 text-white text-[10px] font-bold uppercase px-2 py-1 rounded flex items-center gap-1">
                        <bbc-icon name="check" [s]="12" [sw]="3" /> {{ fr() ? 'Validé' : 'Validated' }}
                      </div>
                    } @else {
                      <div class="bg-amber-400 text-amber-900 text-[10px] font-bold uppercase px-2 py-1 rounded">
                        {{ fr() ? 'En attente' : 'Awaiting' }}
                      </div>
                    }
                    @if (b.financiallyBlocked) {
                      <div class="bg-rose-500 text-white text-[10px] font-bold uppercase px-2 py-1 rounded flex items-center gap-1">
                        <bbc-icon name="alertTri" [s]="12" /> {{ fr() ? 'Bloqué' : 'Blocked' }}
                      </div>
                    }
                  </div>
                </div>
                <div class="flex items-center gap-5 mt-5 relative">
                  <bbc-avatar [name]="b.studentName" [hue]="210" [size]="56" />
                  <div class="flex-1 min-w-0">
                    <div class="text-[10px] uppercase tracking-wider text-gold-200">{{ fr() ? 'Nom' : 'Name' }}</div>
                    <div class="text-lg font-bold truncate">{{ b.studentName }}</div>
                    <div class="text-xs text-brand-100">{{ b.className }}</div>
                  </div>
                  <div class="text-right">
                    <div class="text-[10px] uppercase tracking-wider text-gold-200">{{ fr() ? 'Rang' : 'Rank' }}</div>
                    <div class="font-bold text-2xl text-gold-300">{{ b.rank }}<span class="text-sm text-brand-100">/{{ b.classSize }}</span></div>
                  </div>
                </div>
              </div>

              <!-- Body -->
              <div class="p-6">
                @if (b.financiallyBlocked) {
                  <div class="mb-5 flex items-center gap-3 px-4 py-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 text-sm font-semibold">
                    <bbc-icon name="alertTri" [s]="18" />
                    {{ fr() ? 'Bulletin verrouillé — solde de frais impayé' : 'Report card locked — outstanding fee balance' }}
                  </div>
                }

                <table class="w-full text-sm">
                  <thead>
                    <tr class="border-b-2 border-brand-600 text-[11px] uppercase text-brand-700 font-bold">
                      <th class="text-left py-2">{{ fr() ? 'Matière' : 'Subject' }}</th>
                      <th class="text-center py-2 w-16">{{ fr() ? 'Coef' : 'Coef' }}</th>
                      <th class="text-center py-2 w-20">{{ fr() ? 'Note' : 'Mark' }}</th>
                      <th class="text-center py-2 w-24">{{ fr() ? 'Pondéré' : 'Weighted' }}</th>
                      <th class="text-left py-2 min-w-[160px]">{{ fr() ? 'Appréciation' : 'Appreciation' }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (l of b.lines; track l.subjectCode) {
                      <tr class="border-b border-slate-100">
                        <td class="py-2.5 font-semibold text-ink">{{ l.subjectLabel }}</td>
                        <td class="py-2.5 text-center text-mute">{{ l.coef }}</td>
                        <td class="py-2.5 text-center font-bold"
                          [class]="l.mark < 10 ? 'text-rose-700' : l.mark < 14 ? 'text-ink' : 'text-emerald-700'">{{ l.mark }}</td>
                        <td class="py-2.5 text-center font-mono text-ink">{{ l.weighted }}</td>
                        <td class="py-2.5 pr-2 text-xs italic text-mute">{{ appr(l.mark) }}</td>
                      </tr>
                    } @empty {
                      <tr><td colspan="5" class="py-10"><bbc-empty icon="doc" [label]="fr() ? 'Aucune note' : 'No marks'" /></td></tr>
                    }
                  </tbody>
                </table>

                <!-- Summary boxes -->
                <div class="grid grid-cols-3 gap-3 mt-5">
                  <div class="rounded-lg px-3 py-2.5 ring-2 ring-gold-300"
                    [class]="b.average < 10 ? 'bg-rose-50 text-rose-700' : b.average < 14 ? 'bg-slate-50 text-ink' : 'bg-emerald-50 text-emerald-700'">
                    <div class="text-[10px] uppercase tracking-wide text-mute font-semibold">{{ fr() ? 'Moyenne' : 'Average' }}</div>
                    <div class="text-lg font-bold">{{ b.average }}/20</div>
                  </div>
                  <div class="rounded-lg px-3 py-2.5 bg-slate-50 text-ink">
                    <div class="text-[10px] uppercase tracking-wide text-mute font-semibold">{{ fr() ? 'Rang' : 'Rank' }}</div>
                    <div class="text-lg font-bold">{{ b.rank }}/{{ b.classSize }}</div>
                  </div>
                  <div class="rounded-lg px-3 py-2.5 bg-slate-50 text-ink">
                    <div class="text-[10px] uppercase tracking-wide text-mute font-semibold">{{ fr() ? 'Moy. classe' : 'Class avg' }}</div>
                    <div class="text-lg font-bold">{{ b.classAverage }}/20</div>
                  </div>
                </div>

                <!-- Appreciation / signature -->
                <div class="mt-5 grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div class="border border-slate-200 rounded-lg p-4">
                    <div class="flex items-center justify-between mb-2">
                      <div class="text-[10px] uppercase tracking-wider text-mute font-semibold">
                        {{ fr() ? 'Appréciation générale' : 'General appreciation' }}
                      </div>
                      @if (canWrite && !b.validated && !b.financiallyBlocked) {
                        <bbc-icon name="edit" [s]="12" />
                      }
                    </div>
                    @if (canWrite && !b.validated && !b.financiallyBlocked) {
                      <textarea [ngModel]="appreciationDraft()" (ngModelChange)="appreciationDraft.set($event)"
                        rows="3" [placeholder]="fr() ? 'Saisissez l’appréciation générale…' : 'Enter overall appreciation…'"
                        class="w-full p-2 text-sm rounded border border-slate-200 italic resize-none focus:outline-none focus:border-brand-400 print:hidden"></textarea>
                    } @else {
                      <div class="text-sm text-ink italic">
                        "{{ b.generalAppreciation || (appr(b.average) + (fr() ? '. Continue ainsi.' : '. Keep it up.')) }}"
                      </div>
                    }
                  </div>
                  <div class="border border-slate-200 rounded-lg p-4">
                    <div class="text-[10px] uppercase tracking-wider text-mute font-semibold">
                      {{ fr() ? 'Visa du Principal' : 'Principal signature' }}
                    </div>
                    <div class="text-sm text-ink italic mt-1.5">
                      {{ b.validated ? (fr() ? 'Bulletin validé. Encouragements.' : 'Validated. Encouragements.') : (fr() ? 'En attente de validation.' : 'Awaiting validation.') }}
                    </div>
                    <div class="text-[11px] text-mute mt-3">— {{ fr() ? 'Cachet' : 'Seal' }}</div>
                  </div>
                </div>

                <!-- Actions -->
                <div class="mt-5 flex items-center gap-2 pt-4 border-t border-slate-100 print:hidden">
                  @if (b.financiallyBlocked) {
                    <div class="flex-1 flex items-center gap-2 text-xs text-rose-700">
                      <bbc-icon name="alertTri" [s]="16" />
                      {{ fr() ? 'Bulletin bloqué — frais impayés' : 'Blocked — outstanding fees' }}
                    </div>
                  } @else {
                    <div class="flex-1"></div>
                  }

                  @if (canWrite && !b.validated && !b.financiallyBlocked) {
                    <button (click)="validate(b)"
                      class="inline-flex items-center gap-2 h-10 px-4 bg-gold-500 hover:bg-gold-600 text-white rounded-lg text-sm font-semibold">
                      <bbc-icon name="check" [s]="16" [sw]="2.5" /> {{ fr() ? 'Valider le bulletin' : 'Validate report card' }}
                    </button>
                  }
                  <button (click)="print()" [disabled]="b.financiallyBlocked"
                    class="inline-flex items-center gap-2 h-10 px-4 bg-white border border-slate-200 text-ink hover:bg-slate-50 rounded-lg text-sm font-semibold disabled:opacity-40">
                    <bbc-icon name="printer" [s]="16" /> {{ fr() ? 'Imprimer' : 'Print' }}
                  </button>
                </div>
              </div>
            </div>
          </bbc-card>
          }
        } @else {
          <bbc-card>
            <bbc-empty icon="doc" [label]="fr() ? 'Choisissez un élève et une séquence.' : 'Choose a student and a sequence.'" />
          </bbc-card>
        }
      }

      <!-- ============ PV / MASTER SHEET ============ -->
      @if (mode() === 'pv') {
        @if (pv(); as p) {
          <bbc-card [title]="(fr() ? 'Procès-verbal' : 'Master sheet') + ' — ' + p.className"
            [subtitle]="p.rows.length + (fr() ? ' élèves · Séquence ' : ' students · Sequence ') + p.sequence">
            <div action class="text-right">
              <div class="text-[10px] uppercase tracking-wide text-mute font-semibold">{{ fr() ? 'Moy. classe' : 'Class avg' }}</div>
              <div class="text-lg font-bold text-brand-600">{{ p.classAverage }}/20</div>
            </div>
            @if (p.rows.length === 0) {
              <bbc-empty icon="users" [label]="fr() ? 'Aucun élève' : 'No students'" />
            } @else {
              <div class="overflow-x-auto -mx-5">
                <table class="min-w-full text-sm">
                  <thead class="border-y-2 border-brand-600 bg-brand-50">
                    <tr class="text-brand-700 font-bold uppercase text-[11px]">
                      <th class="text-left py-2 pl-5 w-12">#</th>
                      <th class="text-left py-2">{{ fr() ? 'Élève' : 'Student' }}</th>
                      <th class="text-right py-2 pr-5">{{ fr() ? 'Moyenne' : 'Average' }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (r of p.rows; track r.studentId) {
                      <tr class="border-b border-slate-100 hover:bg-slate-50/50">
                        <td class="py-2 pl-5 text-mute font-mono">{{ r.rank }}</td>
                        <td class="py-2">
                          <div class="flex items-center gap-2.5">
                            <bbc-avatar [name]="r.studentName" [hue]="200" [size]="28" />
                            <span class="font-semibold text-ink">{{ r.studentName }}</span>
                          </div>
                        </td>
                        <td class="py-2 pr-5 text-right">
                          <span class="inline-block px-2 py-0.5 rounded font-bold"
                            [class]="r.average < 10 ? 'bg-rose-100 text-rose-700' : r.average < 14 ? 'bg-slate-100 text-ink' : 'bg-emerald-100 text-emerald-700'">
                            {{ r.average }}/20
                          </span>
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </bbc-card>
        } @else {
          <bbc-card>
            <bbc-empty icon="doc"
              [label]="fr() ? 'Saisissez une classe et une séquence, puis chargez le PV.' : 'Enter a class and sequence, then load the master sheet.'" />
          </bbc-card>
        }
      }
    </div>
  `,
})
export class AcademicComponent {
  protected i18n = inject(I18nService);
  private studentApi = inject(StudentApi);
  private api = inject(AcademicApi);
  private auth = inject(AuthService);
  private scope = inject(ScopeService);

  protected readonly sequences = [1, 2, 3, 4, 5, 6] as const;
  protected canWrite = this.auth.can('academic', 'write');

  /** Maternelle & Primaire use the competency-based (APC) report card; Secondaire uses marks/20. */
  protected isApc = computed(() => {
    const lvl = this.scope.scope()?.level;
    return lvl === 'maternelle' || lvl === 'primary';
  });

  protected students = signal<Student[]>([]);
  protected selectedStudentId = signal<string>('');
  protected sequence = signal<number>(1);
  protected mode = signal<Mode>('bulletin');
  protected bulletin = signal<BulletinView | null>(null);
  protected pv = signal<PvView | null>(null);
  protected appreciationDraft = signal<string>('');
  protected pvClassName = signal<string>('');

  protected fr = () => this.i18n.lang() === 'fr';
  protected String = String;

  protected tabs = computed(() => [
    { id: 'bulletin', label: this.fr() ? 'Bulletin' : 'Report card' },
    { id: 'pv', label: this.fr() ? 'Procès-verbal' : 'Master sheet' },
  ]);

  protected seqOptions = computed(() =>
    this.sequences.map((n) => ({ value: String(n), label: (this.fr() ? 'Séq. ' : 'Seq ') + n })),
  );

  constructor() {
    this.studentApi.list().subscribe({ next: (r) => this.students.set(r), error: () => {} });
  }

  protected appr(mark: number): string {
    return appreciation(mark, this.fr());
  }

  protected setMode(m: Mode): void {
    this.mode.set(m);
  }

  protected onStudentChange(id: string): void {
    this.selectedStudentId.set(id);
    const s = this.students().find((x) => x.id === id);
    this.pvClassName.set(s?.className ?? '');
    this.loadBulletin();
  }

  protected onSequenceChip(value: string | null): void {
    if (!value) return;
    this.sequence.set(Number(value));
    if (this.mode() === 'bulletin') this.loadBulletin();
  }

  private loadBulletin(): void {
    const id = this.selectedStudentId();
    if (!id) {
      this.bulletin.set(null);
      return;
    }
    this.api.bulletin(id, this.sequence()).subscribe((b) => {
      this.bulletin.set(b);
      this.appreciationDraft.set(b.generalAppreciation ?? '');
    });
  }

  protected loadPv(): void {
    const cls = this.pvClassName().trim();
    if (!cls) {
      this.pv.set(null);
      return;
    }
    this.api.pv(cls, this.sequence()).subscribe((p) => this.pv.set(p));
  }

  protected validate(b: BulletinView): void {
    this.api.validate(b.studentId, b.sequence, this.appreciationDraft()).subscribe((res) => {
      this.bulletin.set(res);
      this.appreciationDraft.set(res.generalAppreciation ?? '');
    });
  }

  protected print(): void {
    window.print();
  }
}
