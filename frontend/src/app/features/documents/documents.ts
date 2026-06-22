import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  DocumentsApi, StudentDossier, DocumentView, DocumentUpsert, OrientationView, OrientationUpsert,
} from './documents.api';
import { StudentApi } from '../students/students.api';
import { Student } from '../../core/models';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import {
  IconComponent, CardComponent, PageHeaderComponent, EmptyComponent, AvatarComponent, KpiComponent,
} from '../../core/ui';

interface KindMeta { fr: string; en: string; badge: string; }

@Component({
  selector: 'bbc-documents',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule, IconComponent, CardComponent, PageHeaderComponent, EmptyComponent,
    AvatarComponent, KpiComponent,
  ],
  template: `
    <div class="fade-in max-w-6xl mx-auto">
      <bbc-page-header [title]="i18n.t('documents')"
        [subtitle]="fr() ? 'Dossier documentaire & décisions d’orientation' : 'Document register & orientation decisions'" />

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

        <!-- Dossier detail -->
        <div class="col-span-12 lg:col-span-8 space-y-4">
          @if (!dossier()) {
            <bbc-card>
              <bbc-empty icon="doc" [label]="fr() ? 'Sélectionnez un élève pour voir son dossier' : 'Select a student to see their file'" />
            </bbc-card>
          } @else {
            <!-- Header + KPIs -->
            <bbc-card>
              <div class="flex items-center gap-3 mb-4">
                <bbc-avatar [name]="dossier()!.studentName" [hue]="selectedHue()" [size]="44" />
                <div class="flex-1">
                  <div class="font-display text-lg font-bold text-ink">{{ dossier()!.studentName }}</div>
                  <div class="text-xs text-mute">{{ dossier()!.matricule }} · {{ dossier()!.className }}</div>
                </div>
              </div>
              <div class="grid grid-cols-2 gap-3">
                <bbc-kpi [label]="fr() ? 'Documents' : 'Documents'" [value]="dossier()!.documents.length.toString()" icon="doc" />
                <bbc-kpi [label]="fr() ? 'Décisions d’orientation' : 'Orientation decisions'" [value]="dossier()!.orientations.length.toString()" icon="cap" />
              </div>
            </bbc-card>

            <!-- Document register -->
            <bbc-card [title]="fr() ? 'Registre des documents' : 'Document register'"
              [subtitle]="dossier()!.documents.length + (fr() ? ' pièces' : ' items')">
              @if (canWrite) {
                <div class="flex justify-end mb-2">
                  <button (click)="toggleDocForm()"
                    class="inline-flex items-center gap-2 h-8 px-3 text-xs font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700">
                    <bbc-icon name="plus" [s]="14" /> {{ fr() ? 'Ajouter un document' : 'Add a document' }}
                  </button>
                </div>
              }
              @if (canWrite && showDocForm()) {
                <div class="grid grid-cols-1 md:grid-cols-2 gap-2.5 mb-3 p-3 rounded-lg bg-slate-50/70 border border-slate-100">
                  <select [(ngModel)]="docDraft.kind"
                    class="h-10 px-3 rounded-lg border border-slate-200 text-sm bg-white focus:outline-none focus:border-brand-400">
                    @for (k of kindKeys; track k) {
                      <option [value]="k">{{ fr() ? KIND[k].fr : KIND[k].en }}</option>
                    }
                  </select>
                  <input [(ngModel)]="docDraft.title" [placeholder]="fr() ? 'Titre du document' : 'Document title'"
                    class="h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
                  <input [(ngModel)]="docDraft.fileRef" [placeholder]="fr() ? 'Référence ou URL (facultatif)' : 'Reference or URL (optional)'"
                    class="md:col-span-2 h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
                  <input [(ngModel)]="docDraft.note" [placeholder]="fr() ? 'Note (facultatif)' : 'Note (optional)'"
                    class="md:col-span-2 h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
                  <div class="md:col-span-2 flex items-center justify-end gap-2">
                    <button (click)="cancelDoc()"
                      class="h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-mute hover:bg-slate-50">
                      {{ i18n.t('cancel') }}
                    </button>
                    <button (click)="saveDoc()"
                      class="inline-flex items-center gap-1.5 h-9 px-4 text-sm font-semibold rounded-lg bg-emerald-600 text-white hover:bg-emerald-700">
                      <bbc-icon name="check" [s]="16" /> {{ i18n.t('save') }}
                    </button>
                  </div>
                </div>
              }
              @if (dossier()!.documents.length === 0) {
                <bbc-empty icon="doc" [label]="fr() ? 'Aucun document' : 'No document'" />
              } @else {
                <div class="space-y-1.5">
                  @for (d of dossier()!.documents; track d.id) {
                    <div class="flex items-start gap-3 p-3 rounded-lg border border-slate-100 hover:bg-slate-50/50 group">
                      <div class="w-9 h-9 rounded-lg bg-brand-50 text-brand-600 flex items-center justify-center shrink-0">
                        <bbc-icon name="doc" [s]="18" />
                      </div>
                      <div class="flex-1 min-w-0">
                        <div class="flex items-center gap-2 flex-wrap">
                          <span class="font-semibold text-ink">{{ d.title }}</span>
                          <span class="text-[10px] font-bold uppercase px-1.5 py-0.5 rounded" [class]="KIND[d.kind]?.badge ?? 'bg-slate-100 text-slate-700'">
                            {{ fr() ? (KIND[d.kind]?.fr ?? d.kind) : (KIND[d.kind]?.en ?? d.kind) }}
                          </span>
                        </div>
                        @if (d.note) { <div class="text-[12px] text-mute mt-0.5">{{ d.note }}</div> }
                        @if (d.fileRef) {
                          @if (isUrl(d.fileRef)) {
                            <a [href]="d.fileRef" target="_blank" rel="noopener"
                              class="inline-flex items-center gap-1 text-[12px] text-brand-600 hover:underline mt-1">
                              <bbc-icon name="globe" [s]="12" /> {{ d.fileRef }}
                            </a>
                          } @else {
                            <div class="text-[12px] text-mute mt-1 inline-flex items-center gap-1">
                              <bbc-icon name="receipt" [s]="12" /> {{ d.fileRef }}
                            </div>
                          }
                        }
                      </div>
                      @if (canWrite) {
                        <button (click)="removeDoc(d)"
                          class="w-7 h-7 rounded text-mute hover:text-rose-600 hover:bg-rose-50 flex items-center justify-center self-center opacity-0 group-hover:opacity-100 transition"
                          [title]="fr() ? 'Supprimer' : 'Delete'">
                          <bbc-icon name="trash" [s]="14" />
                        </button>
                      }
                    </div>
                  }
                </div>
              }
            </bbc-card>

            <!-- Orientation decisions -->
            <bbc-card [title]="fr() ? 'Décisions d’orientation' : 'Orientation decisions'"
              [subtitle]="fr() ? 'Conseils de classe & orientation' : 'Class councils & orientation'">
              @if (canWrite) {
                <div class="flex justify-end mb-2">
                  <button (click)="toggleOriForm()"
                    class="inline-flex items-center gap-2 h-8 px-3 text-xs font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700">
                    <bbc-icon name="plus" [s]="14" /> {{ fr() ? 'Ajouter une décision' : 'Add a decision' }}
                  </button>
                </div>
              }
              @if (canWrite && showOriForm()) {
                <div class="grid grid-cols-1 md:grid-cols-2 gap-2.5 mb-3 p-3 rounded-lg bg-slate-50/70 border border-slate-100">
                  <input [(ngModel)]="oriDraft.academicYear" [placeholder]="fr() ? 'Année (ex: 2024-2025)' : 'Year (e.g. 2024-2025)'"
                    class="h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
                  <input [(ngModel)]="oriDraft.stage" [placeholder]="fr() ? 'Étape (ex: Orientation 3ème)' : 'Stage (e.g. Orientation Form 4)'"
                    class="h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
                  <input [(ngModel)]="oriDraft.councilDate" type="date"
                    [placeholder]="fr() ? 'Date du conseil' : 'Council date'"
                    class="h-10 px-3 rounded-lg border border-slate-200 text-sm bg-white focus:outline-none focus:border-brand-400" />
                  <input [(ngModel)]="oriDraft.recommendation" [placeholder]="fr() ? 'Recommandation' : 'Recommendation'"
                    class="h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
                  <input [(ngModel)]="oriDraft.decision" [placeholder]="fr() ? 'Décision finale' : 'Final decision'"
                    class="md:col-span-2 h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
                  <div class="md:col-span-2 flex items-center justify-end gap-2">
                    <button (click)="cancelOri()"
                      class="h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-mute hover:bg-slate-50">
                      {{ i18n.t('cancel') }}
                    </button>
                    <button (click)="saveOri()"
                      class="inline-flex items-center gap-1.5 h-9 px-4 text-sm font-semibold rounded-lg bg-emerald-600 text-white hover:bg-emerald-700">
                      <bbc-icon name="check" [s]="16" /> {{ i18n.t('save') }}
                    </button>
                  </div>
                </div>
              }
              @if (dossier()!.orientations.length === 0) {
                <bbc-empty icon="cap" [label]="fr() ? 'Aucune décision d’orientation' : 'No orientation decision'" />
              } @else {
                <div class="space-y-1.5">
                  @for (o of dossier()!.orientations; track o.id) {
                    <div class="flex items-start gap-3 p-3 rounded-lg border border-slate-100 hover:bg-slate-50/50 group">
                      <div class="w-9 h-9 rounded-lg bg-violet-50 text-violet-600 flex items-center justify-center shrink-0">
                        <bbc-icon name="cap" [s]="18" />
                      </div>
                      <div class="flex-1 min-w-0">
                        <div class="flex items-center gap-2 flex-wrap">
                          <span class="font-semibold text-ink">{{ o.stage }}</span>
                          <span class="text-[11px] text-mute">· {{ o.academicYear }}</span>
                          @if (o.councilDate) {
                            <span class="text-[11px] text-mute inline-flex items-center gap-1">
                              <bbc-icon name="calendar" [s]="12" /> {{ o.councilDate }}
                            </span>
                          }
                        </div>
                        @if (o.recommendation) {
                          <div class="text-[12px] text-mute mt-1">
                            <span class="font-semibold">{{ fr() ? 'Recommandation' : 'Recommendation' }}:</span> {{ o.recommendation }}
                          </div>
                        }
                        @if (o.decision) {
                          <div class="text-sm text-ink mt-1">
                            <span class="font-semibold text-[12px] text-mute">{{ fr() ? 'Décision' : 'Decision' }}:</span> {{ o.decision }}
                          </div>
                        }
                      </div>
                      @if (canWrite) {
                        <button (click)="removeOri(o)"
                          class="w-7 h-7 rounded text-mute hover:text-rose-600 hover:bg-rose-50 flex items-center justify-center self-center opacity-0 group-hover:opacity-100 transition"
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
export class DocumentsComponent {
  protected i18n = inject(I18nService);
  private api = inject(DocumentsApi);
  private studentApi = inject(StudentApi);
  private auth = inject(AuthService);

  protected readonly kindKeys = [
    'birth_cert', 'photo', 'prior_report', 'certificate', 'medical', 'other',
  ];
  protected readonly KIND: Record<string, KindMeta> = {
    birth_cert:   { fr: 'Acte de naissance', en: 'Birth certificate', badge: 'bg-sky-100 text-sky-700' },
    photo:        { fr: 'Photo',             en: 'Photo',             badge: 'bg-violet-100 text-violet-700' },
    prior_report: { fr: 'Bulletin antérieur', en: 'Prior report',    badge: 'bg-amber-100 text-amber-700' },
    certificate:  { fr: 'Certificat',        en: 'Certificate',       badge: 'bg-emerald-100 text-emerald-700' },
    medical:      { fr: 'Médical',           en: 'Medical',           badge: 'bg-rose-100 text-rose-700' },
    other:        { fr: 'Autre',             en: 'Other',             badge: 'bg-slate-100 text-slate-700' },
  };

  protected students = signal<Student[]>([]);
  protected query = signal('');
  protected selectedId = signal<string | null>(null);
  protected dossier = signal<StudentDossier | null>(null);

  protected showDocForm = signal(false);
  protected docDraft: DocumentUpsert = this.blankDoc();
  protected showOriForm = signal(false);
  protected oriDraft: OrientationUpsert = this.blankOri();

  protected canWrite = this.auth.can('documents', 'write');
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
    this.cancelDoc();
    this.cancelOri();
    this.api.forStudent(s.id).subscribe((d) => this.dossier.set(d));
  }

  private reload(): void {
    const id = this.selectedId();
    if (id) this.api.forStudent(id).subscribe((d) => this.dossier.set(d));
  }

  // --- documents ---
  protected toggleDocForm(): void {
    this.docDraft = this.blankDoc();
    this.showDocForm.update((v) => !v);
  }
  protected cancelDoc(): void {
    this.showDocForm.set(false);
    this.docDraft = this.blankDoc();
  }
  protected saveDoc(): void {
    const id = this.selectedId();
    if (!id || !this.docDraft.kind || !this.docDraft.title) return;
    this.api.addDocument(id, this.docDraft).subscribe(() => {
      this.cancelDoc();
      this.reload();
    });
  }
  protected removeDoc(d: DocumentView): void {
    this.api.removeDocument(d.id).subscribe(() => this.reload());
  }

  // --- orientations ---
  protected toggleOriForm(): void {
    this.oriDraft = this.blankOri();
    this.showOriForm.update((v) => !v);
  }
  protected cancelOri(): void {
    this.showOriForm.set(false);
    this.oriDraft = this.blankOri();
  }
  protected saveOri(): void {
    const id = this.selectedId();
    if (!id || !this.oriDraft.academicYear || !this.oriDraft.stage) return;
    this.api.addOrientation(id, {
      ...this.oriDraft,
      councilDate: this.oriDraft.councilDate ? this.oriDraft.councilDate : null,
    }).subscribe(() => {
      this.cancelOri();
      this.reload();
    });
  }
  protected removeOri(o: OrientationView): void {
    this.api.removeOrientation(o.id).subscribe(() => this.reload());
  }

  protected isUrl(ref: string): boolean {
    return /^https?:\/\//i.test(ref.trim());
  }

  private blankDoc(): DocumentUpsert {
    return { kind: 'birth_cert', title: '', note: '', fileRef: '' };
  }
  private blankOri(): OrientationUpsert {
    return { academicYear: '', stage: '', recommendation: '', decision: '', councilDate: null };
  }
}
