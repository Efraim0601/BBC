import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  HealthApi, StudentHealth, HealthRecordUpsert, VisitView, VisitUpsert, ActivityView, ActivityUpsert,
} from './health.api';
import { StudentApi } from '../students/students.api';
import { Student } from '../../core/models';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import {
  IconComponent, CardComponent, PageHeaderComponent, EmptyComponent, AvatarComponent, KpiComponent,
} from '../../core/ui';

interface CatMeta { fr: string; en: string; badge: string; }

@Component({
  selector: 'bbc-health',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule, IconComponent, CardComponent, PageHeaderComponent, EmptyComponent,
    AvatarComponent, KpiComponent,
  ],
  template: `
    <div class="fade-in max-w-6xl mx-auto">
      <bbc-page-header [title]="fr() ? 'Santé & vie scolaire' : 'Health & school life'"
        [subtitle]="fr() ? 'Dossier médical, infirmerie et activités' : 'Medical record, infirmary and activities'" />

      <div class="grid grid-cols-12 gap-4">
        <!-- Student picker -->
        <bbc-card className="col-span-12 lg:col-span-4"
          [title]="fr() ? 'Élèves' : 'Students'"
          [subtitle]="students().length + (fr() ? ' inscrits' : ' enrolled')">
          <input [ngModel]="query()" (ngModelChange)="query.set($event)"
            [placeholder]="fr() ? 'Rechercher un élève…' : 'Search a student…'"
            class="w-full h-9 px-3 mb-2 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
          <div class="space-y-1 max-h-[28rem] overflow-y-auto pr-1">
            @for (s of filtered(); track s.id) {
              <button (click)="select(s)"
                class="w-full flex items-center gap-2.5 p-2 rounded-lg text-left transition"
                [class]="selectedId() === s.id ? 'bg-brand-50 border border-brand-200' : 'hover:bg-slate-50 border border-transparent'">
                <bbc-avatar [name]="s.name" [hue]="s.photoHue" />
                <div class="flex-1 min-w-0">
                  <div class="text-sm font-semibold text-ink truncate">{{ s.name }}</div>
                  <div class="text-[11px] text-mute">{{ s.matricule }} · {{ s.className }}</div>
                </div>
              </button>
            } @empty {
              <bbc-empty icon="users" [label]="fr() ? 'Aucun élève' : 'No student'" />
            }
          </div>
        </bbc-card>

        <!-- Health detail -->
        <div class="col-span-12 lg:col-span-8 space-y-4">
          @if (!health()) {
            <bbc-card>
              <bbc-empty icon="spark" [label]="fr() ? 'Sélectionnez un élève pour voir sa santé & vie scolaire' : 'Select a student to see their health & school life'" />
            </bbc-card>
          } @else {
            <!-- Header + KPIs -->
            <bbc-card>
              <div class="flex items-center gap-3 mb-4">
                <bbc-avatar [name]="health()!.studentName" [hue]="selectedHue()" [size]="44" />
                <div class="flex-1">
                  <div class="font-display text-lg font-bold text-ink">{{ health()!.studentName }}</div>
                  <div class="text-xs text-mute">{{ health()!.matricule }} · {{ health()!.className }}</div>
                </div>
              </div>
              <div class="grid grid-cols-3 gap-3">
                <bbc-kpi [label]="fr() ? 'Groupe sanguin' : 'Blood group'" [value]="health()!.record?.bloodGroup || '—'" icon="spark" />
                <bbc-kpi [label]="fr() ? 'Passages infirmerie' : 'Infirmary visits'" [value]="health()!.visits.length.toString()" icon="shield" />
                <bbc-kpi [label]="fr() ? 'Activités' : 'Activities'" [value]="health()!.activities.length.toString()" icon="star" />
              </div>
            </bbc-card>

            <!-- Medical record -->
            <bbc-card [title]="fr() ? 'Dossier médical' : 'Medical record'"
              [subtitle]="fr() ? 'Informations de santé de l’élève' : 'Student health information'">
              <div class="grid grid-cols-1 md:grid-cols-2 gap-2.5">
                <label class="block">
                  <span class="text-[11px] font-semibold text-mute">{{ fr() ? 'Groupe sanguin' : 'Blood group' }}</span>
                  <input [(ngModel)]="record.bloodGroup" [disabled]="!canWrite" maxlength="4"
                    [placeholder]="fr() ? 'ex: O+' : 'e.g. O+'"
                    class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400 disabled:bg-slate-50" />
                </label>
                <div class="grid grid-cols-2 gap-2.5">
                  <label class="block">
                    <span class="text-[11px] font-semibold text-mute">{{ fr() ? 'Taille (cm)' : 'Height (cm)' }}</span>
                    <input [(ngModel)]="record.heightCm" type="number" min="0" [disabled]="!canWrite"
                      class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400 disabled:bg-slate-50" />
                  </label>
                  <label class="block">
                    <span class="text-[11px] font-semibold text-mute">{{ fr() ? 'Poids (kg)' : 'Weight (kg)' }}</span>
                    <input [(ngModel)]="record.weightKg" type="number" min="0" [disabled]="!canWrite"
                      class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400 disabled:bg-slate-50" />
                  </label>
                </div>
                <label class="block md:col-span-2">
                  <span class="text-[11px] font-semibold text-mute">{{ fr() ? 'Allergies' : 'Allergies' }}</span>
                  <textarea [(ngModel)]="record.allergies" [disabled]="!canWrite" rows="2"
                    [placeholder]="fr() ? 'Allergies connues…' : 'Known allergies…'"
                    class="mt-1 w-full px-3 py-2 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400 disabled:bg-slate-50"></textarea>
                </label>
                <label class="block md:col-span-2">
                  <span class="text-[11px] font-semibold text-mute">{{ fr() ? 'Pathologies / conditions' : 'Conditions' }}</span>
                  <textarea [(ngModel)]="record.conditions" [disabled]="!canWrite" rows="2"
                    [placeholder]="fr() ? 'Pathologies chroniques…' : 'Chronic conditions…'"
                    class="mt-1 w-full px-3 py-2 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400 disabled:bg-slate-50"></textarea>
                </label>
                <label class="block md:col-span-2">
                  <span class="text-[11px] font-semibold text-mute">{{ fr() ? 'Vaccinations' : 'Vaccinations' }}</span>
                  <textarea [(ngModel)]="record.vaccinations" [disabled]="!canWrite" rows="2"
                    [placeholder]="fr() ? 'Statut vaccinal…' : 'Vaccination status…'"
                    class="mt-1 w-full px-3 py-2 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400 disabled:bg-slate-50"></textarea>
                </label>
                <label class="block">
                  <span class="text-[11px] font-semibold text-mute">{{ fr() ? 'Médecin traitant' : 'Attending doctor' }}</span>
                  <input [(ngModel)]="record.doctorName" [disabled]="!canWrite"
                    [placeholder]="fr() ? 'Nom du médecin' : 'Doctor name'"
                    class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400 disabled:bg-slate-50" />
                </label>
                <label class="block">
                  <span class="text-[11px] font-semibold text-mute">{{ fr() ? 'Téléphone médecin' : 'Doctor phone' }}</span>
                  <input [(ngModel)]="record.doctorPhone" [disabled]="!canWrite"
                    [placeholder]="fr() ? 'Téléphone' : 'Phone'"
                    class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400 disabled:bg-slate-50" />
                </label>
                @if (canWrite) {
                  <div class="md:col-span-2 flex items-center justify-end">
                    <button (click)="saveRecord()"
                      class="inline-flex items-center gap-1.5 h-9 px-4 text-sm font-semibold rounded-lg bg-emerald-600 text-white hover:bg-emerald-700">
                      <bbc-icon name="check" [s]="16" /> {{ fr() ? 'Enregistrer le dossier' : 'Save record' }}
                    </button>
                  </div>
                }
              </div>
            </bbc-card>

            <!-- Infirmary visits -->
            <bbc-card [title]="fr() ? 'Passages à l’infirmerie' : 'Infirmary visits'"
              [subtitle]="health()!.visits.length + (fr() ? ' enregistrés' : ' recorded')">
              @if (canWrite) {
                <div class="grid grid-cols-1 md:grid-cols-4 gap-2.5 mb-3 p-3 rounded-lg bg-slate-50/60 border border-slate-100">
                  <input [(ngModel)]="visitDraft.visitDate" type="date"
                    class="h-10 px-3 rounded-lg border border-slate-200 text-sm bg-white focus:outline-none focus:border-brand-400" />
                  <input [(ngModel)]="visitDraft.reason" [placeholder]="fr() ? 'Motif' : 'Reason'"
                    class="md:col-span-2 h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
                  <button (click)="addVisit()"
                    class="inline-flex items-center justify-center gap-1.5 h-10 px-3 text-sm font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700">
                    <bbc-icon name="plus" [s]="16" /> {{ fr() ? 'Ajouter' : 'Add' }}
                  </button>
                  <input [(ngModel)]="visitDraft.treatment" [placeholder]="fr() ? 'Traitement / soins prodigués…' : 'Treatment given…'"
                    class="md:col-span-4 h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
                </div>
              }
              @if (health()!.visits.length === 0) {
                <bbc-empty icon="shield" [label]="fr() ? 'Aucun passage enregistré' : 'No visit recorded'" />
              } @else {
                <div class="space-y-1.5">
                  @for (v of health()!.visits; track v.id) {
                    <div class="flex items-start gap-3 p-3 rounded-lg border border-slate-100 hover:bg-slate-50/50 group">
                      <div class="flex-1 min-w-0">
                        <div class="flex items-center gap-2 flex-wrap">
                          <span class="inline-flex items-center gap-1 text-[11px] text-mute"><bbc-icon name="calendar" [s]="12" /> {{ v.visitDate }}</span>
                          <span class="font-semibold text-ink">{{ v.reason }}</span>
                        </div>
                        @if (v.treatment) { <div class="text-sm text-mute mt-0.5">{{ v.treatment }}</div> }
                      </div>
                      @if (canWrite) {
                        <button (click)="removeVisit(v)"
                          class="w-7 h-7 self-center rounded text-mute hover:text-rose-600 hover:bg-rose-50 flex items-center justify-center opacity-0 group-hover:opacity-100 transition"
                          [title]="fr() ? 'Supprimer' : 'Delete'">
                          <bbc-icon name="trash" [s]="14" />
                        </button>
                      }
                    </div>
                  }
                </div>
              }
            </bbc-card>

            <!-- Activities -->
            <bbc-card [title]="fr() ? 'Activités extrascolaires' : 'Extracurricular activities'"
              [subtitle]="health()!.activities.length + (fr() ? ' activités' : ' activities')">
              @if (canWrite) {
                <div class="grid grid-cols-1 md:grid-cols-5 gap-2.5 mb-3 p-3 rounded-lg bg-slate-50/60 border border-slate-100">
                  <input [(ngModel)]="activityDraft.name" [placeholder]="fr() ? 'Nom (ex: Football)' : 'Name (e.g. Football)'"
                    class="md:col-span-2 h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
                  <select [(ngModel)]="activityDraft.category"
                    class="h-10 px-3 rounded-lg border border-slate-200 text-sm bg-white focus:outline-none focus:border-brand-400">
                    @for (c of catKeys; track c) {
                      <option [value]="c">{{ fr() ? CAT[c].fr : CAT[c].en }}</option>
                    }
                  </select>
                  <input [(ngModel)]="activityDraft.role" [placeholder]="fr() ? 'Rôle' : 'Role'"
                    class="h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
                  <button (click)="addActivity()"
                    class="inline-flex items-center justify-center gap-1.5 h-10 px-3 text-sm font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700">
                    <bbc-icon name="plus" [s]="16" /> {{ fr() ? 'Ajouter' : 'Add' }}
                  </button>
                </div>
              }
              @if (health()!.activities.length === 0) {
                <bbc-empty icon="star" [label]="fr() ? 'Aucune activité' : 'No activity'" />
              } @else {
                <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  @for (a of health()!.activities; track a.id) {
                    <div class="flex items-start gap-3 p-3 rounded-lg border border-slate-100 hover:bg-slate-50/50 group">
                      <div class="flex-1 min-w-0">
                        <div class="flex items-center gap-2 flex-wrap">
                          <span class="font-semibold text-ink">{{ a.name }}</span>
                          <span class="text-[10px] font-bold uppercase px-1.5 py-0.5 rounded" [class]="CAT[a.category]?.badge ?? 'bg-slate-100 text-slate-700'">
                            {{ fr() ? (CAT[a.category]?.fr ?? a.category) : (CAT[a.category]?.en ?? a.category) }}
                          </span>
                        </div>
                        <div class="text-[11px] text-mute mt-0.5">
                          @if (a.role) { <span>{{ a.role }}</span> }
                          @if (a.role && a.season) { <span> · </span> }
                          @if (a.season) { <span>{{ a.season }}</span> }
                        </div>
                      </div>
                      @if (canWrite) {
                        <button (click)="removeActivity(a)"
                          class="w-7 h-7 self-center rounded text-mute hover:text-rose-600 hover:bg-rose-50 flex items-center justify-center opacity-0 group-hover:opacity-100 transition"
                          [title]="fr() ? 'Supprimer' : 'Delete'">
                          <bbc-icon name="trash" [s]="14" />
                        </button>
                      }
                    </div>
                  }
                </div>
              }
            </bbc-card>
          }
        </div>
      </div>
    </div>
  `,
})
export class HealthComponent {
  protected i18n = inject(I18nService);
  private api = inject(HealthApi);
  private studentApi = inject(StudentApi);
  private auth = inject(AuthService);

  protected readonly catKeys = ['club', 'sport', 'art', 'other'];
  protected readonly CAT: Record<string, CatMeta> = {
    club:  { fr: 'Club',   en: 'Club',  badge: 'bg-sky-100 text-sky-700' },
    sport: { fr: 'Sport',  en: 'Sport', badge: 'bg-emerald-100 text-emerald-700' },
    art:   { fr: 'Art',    en: 'Art',   badge: 'bg-violet-100 text-violet-700' },
    other: { fr: 'Autre',  en: 'Other', badge: 'bg-slate-100 text-slate-700' },
  };

  protected students = signal<Student[]>([]);
  protected query = signal('');
  protected selectedId = signal<string | null>(null);
  protected health = signal<StudentHealth | null>(null);

  protected record: HealthRecordUpsert = this.blankRecord();
  protected visitDraft: VisitUpsert = this.blankVisit();
  protected activityDraft: ActivityUpsert = this.blankActivity();

  protected canWrite = this.auth.can('health', 'write');
  protected fr = () => this.i18n.lang() === 'fr';

  protected filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    const list = this.students();
    if (!q) return list;
    return list.filter((s) =>
      s.name.toLowerCase().includes(q) || s.matricule.toLowerCase().includes(q) || s.className.toLowerCase().includes(q));
  });

  protected selectedHue = computed(() =>
    this.students().find((s) => s.id === this.selectedId())?.photoHue ?? 210);

  constructor() {
    this.studentApi.list().subscribe((r) => this.students.set(r));
  }

  protected select(s: Student): void {
    this.selectedId.set(s.id);
    this.visitDraft = this.blankVisit();
    this.activityDraft = this.blankActivity();
    this.api.forStudent(s.id).subscribe((h) => this.apply(h));
  }

  private apply(h: StudentHealth): void {
    this.health.set(h);
    const r = h.record;
    this.record = {
      bloodGroup: r?.bloodGroup ?? '', allergies: r?.allergies ?? '', conditions: r?.conditions ?? '',
      vaccinations: r?.vaccinations ?? '', doctorName: r?.doctorName ?? '', doctorPhone: r?.doctorPhone ?? '',
      heightCm: r?.heightCm ?? null, weightKg: r?.weightKg ?? null,
    };
  }

  private reload(): void {
    const id = this.selectedId();
    if (id) this.api.forStudent(id).subscribe((h) => this.apply(h));
  }

  protected saveRecord(): void {
    const id = this.selectedId();
    if (!id) return;
    this.api.saveRecord(id, this.record).subscribe(() => this.reload());
  }

  protected addVisit(): void {
    const id = this.selectedId();
    if (!id || !this.visitDraft.visitDate || !this.visitDraft.reason) return;
    this.api.addVisit(id, this.visitDraft).subscribe(() => {
      this.visitDraft = this.blankVisit();
      this.reload();
    });
  }

  protected removeVisit(v: VisitView): void {
    this.api.removeVisit(v.id).subscribe(() => this.reload());
  }

  protected addActivity(): void {
    const id = this.selectedId();
    if (!id || !this.activityDraft.name || !this.activityDraft.category) return;
    this.api.addActivity(id, this.activityDraft).subscribe(() => {
      this.activityDraft = this.blankActivity();
      this.reload();
    });
  }

  protected removeActivity(a: ActivityView): void {
    this.api.removeActivity(a.id).subscribe(() => this.reload());
  }

  private blankRecord(): HealthRecordUpsert {
    return { bloodGroup: '', allergies: '', conditions: '', vaccinations: '',
      doctorName: '', doctorPhone: '', heightCm: null, weightKg: null };
  }
  private blankVisit(): VisitUpsert {
    return { visitDate: '', reason: '', treatment: '' };
  }
  private blankActivity(): ActivityUpsert {
    return { name: '', category: 'club', role: '', season: '' };
  }
}
