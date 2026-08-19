import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import {
  LibraryApi, ResourceView, ResourceUpsert, Audience, Category, Section, openBlob, fmtBytes,
} from './library.api';
import {
  IconComponent, CardComponent, PageHeaderComponent, EmptyComponent, ChipFilterComponent,
  ConfirmComponent, KpiComponent,
} from '../../core/ui';

/**
 * Ressources partagées — la direction dépose un document et choisit qui le voit.
 *
 * <p>Deux réglages, et deux seulement, portent la granularité : le destinataire
 * (tout le monde / personnel / parents) et le périmètre (toute l'école ou un
 * cycle). Le second n'est proposé qu'à l'admin principal : un administrateur de
 * cycle publie pour le sien, le serveur le lui impose de toute façon — l'écran
 * se contente de ne pas faire mine du contraire.
 *
 * <p>Rien n'est visible tant que la ressource n'est pas publiée : on dépose, on
 * relit, on publie. Le bandeau de chaque fiche dit à voix haute qui la reçoit,
 * pour qu'une circulaire interne ne parte jamais aux familles par inadvertance.
 */
@Component({
  selector: 'bbc-library',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule, DatePipe, IconComponent, CardComponent, PageHeaderComponent, EmptyComponent,
    ChipFilterComponent, ConfirmComponent, KpiComponent,
  ],
  template: `
    <div class="fade-in max-w-6xl mx-auto">
      <bbc-page-header [title]="i18n.t('library')"
        [subtitle]="fr() ? 'Documents mis à disposition du personnel et des familles'
                         : 'Documents made available to staff and families'">
        @if (canWrite) {
          <button right (click)="openForm()"
            class="inline-flex items-center gap-2 h-9 px-4 text-sm font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700">
            <bbc-icon name="plus" [s]="16" /> {{ fr() ? 'Déposer un document' : 'Upload a document' }}
          </button>
        }
      </bbc-page-header>

      @if (canWrite) {
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-5">
          <bbc-kpi icon="doc" tone="neutral" [label]="fr() ? 'Documents' : 'Documents'" [value]="items().length" />
          <bbc-kpi icon="check" tone="ok" [label]="fr() ? 'Publiés' : 'Published'" [value]="publishedCount()" />
          <bbc-kpi icon="eye" tone="warn" [label]="fr() ? 'Brouillons' : 'Drafts'" [value]="draftCount()" />
        </div>
      }

      <!-- ---- Dépôt / modification ------------------------------------------ -->
      @if (canWrite && showForm()) {
        <bbc-card className="mb-5"
          [title]="editId() ? (fr() ? 'Modifier la fiche' : 'Edit the record') : (fr() ? 'Nouveau document' : 'New document')"
          [subtitle]="editId()
            ? (fr() ? 'Le fichier ne se remplace pas — déposez-en un nouveau pour le changer.'
                    : 'The file cannot be swapped — upload a new document to change it.')
            : (fr() ? 'PDF, Word, Excel, PowerPoint, texte ou image — 25 Mo maximum.'
                    : 'PDF, Word, Excel, PowerPoint, text or image — 25 MB maximum.')">
          <form (ngSubmit)="save()" class="grid grid-cols-1 md:grid-cols-12 gap-3">
            @if (!editId()) {
              <label class="block md:col-span-12">
                <span class="text-xs font-semibold text-ink">{{ fr() ? 'Fichier' : 'File' }} *</span>
                <input type="file" (change)="pick($event)" [accept]="ACCEPT"
                  class="mt-1 w-full text-sm file:mr-3 file:h-9 file:px-4 file:rounded-lg file:border-0
                         file:bg-brand-50 file:text-brand-700 file:text-sm file:font-semibold
                         file:hover:bg-brand-100 file:cursor-pointer" />
                @if (file(); as f) {
                  <span class="mt-1 inline-flex items-center gap-1.5 text-xs text-mute">
                    <bbc-icon name="doc" [s]="12" /> {{ f.name }} · {{ size(f.size) }}
                  </span>
                }
              </label>
            }

            <label class="block md:col-span-7">
              <span class="text-xs font-semibold text-ink">{{ fr() ? 'Titre' : 'Title' }} *</span>
              <input [(ngModel)]="draft.title" name="title" required maxlength="200"
                [placeholder]="fr() ? 'Ex. Circulaire de rentrée' : 'e.g. Back-to-school circular'"
                class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
            </label>

            <label class="block md:col-span-5">
              <span class="text-xs font-semibold text-ink">{{ fr() ? 'Rubrique' : 'Category' }}</span>
              <select [(ngModel)]="draft.category" name="category"
                class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 bg-white text-sm focus:outline-none focus:border-brand-400">
                @for (c of categoryKeys; track c) { <option [value]="c">{{ categoryLabel(c) }}</option> }
              </select>
            </label>

            <label class="block md:col-span-12">
              <span class="text-xs font-semibold text-ink">{{ fr() ? 'Description' : 'Description' }}</span>
              <textarea [(ngModel)]="draft.description" name="description" rows="2" maxlength="4000"
                [placeholder]="fr() ? 'À quoi sert ce document, ce qu\\'il faut en faire (facultatif)'
                                    : 'What this document is for (optional)'"
                class="mt-1 w-full px-3 py-2 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400"></textarea>
            </label>

            <!-- Destinataire : le cœur du réglage, montré en toutes lettres. -->
            <div class="md:col-span-12">
              <span class="text-xs font-semibold text-ink">{{ fr() ? 'Qui verra ce document ?' : 'Who will see this document?' }} *</span>
              <div class="mt-1.5 grid grid-cols-1 sm:grid-cols-3 gap-2">
                @for (a of audienceKeys; track a) {
                  <button type="button" (click)="draft.audience = a"
                    class="flex items-start gap-2.5 p-3 rounded-lg border text-left transition"
                    [class]="draft.audience === a
                      ? 'border-brand-400 bg-brand-50/60 ring-1 ring-brand-200'
                      : 'border-slate-200 hover:border-brand-300 bg-white'">
                    <span class="w-8 h-8 rounded-lg flex items-center justify-center shrink-0" [class]="AUD[a].tone">
                      <bbc-icon [name]="AUD[a].icon" [s]="16" />
                    </span>
                    <span class="min-w-0">
                      <span class="block text-sm font-semibold text-ink">{{ audienceLabel(a) }}</span>
                      <span class="block text-[11px] text-mute leading-snug">{{ audienceHint(a) }}</span>
                    </span>
                  </button>
                }
              </div>
            </div>

            <!-- Périmètre : seul l'admin principal choisit ; les autres sont verrouillés. -->
            <div class="md:col-span-12">
              <span class="text-xs font-semibold text-ink">{{ fr() ? 'Périmètre' : 'Scope' }}</span>
              @if (lockedSection()) {
                <div class="mt-1.5 inline-flex items-center gap-2 h-10 px-3 rounded-lg bg-slate-50 border border-slate-200 text-sm text-mute">
                  <bbc-icon name="shield" [s]="14" />
                  {{ sectionLabel(lockedSection()) }}
                  <span class="text-[11px]">· {{ fr() ? 'votre section' : 'your section' }}</span>
                </div>
              } @else {
                <select [(ngModel)]="draft.section" name="section"
                  class="mt-1.5 w-full md:w-72 h-10 px-3 rounded-lg border border-slate-200 bg-white text-sm focus:outline-none focus:border-brand-400">
                  <option [ngValue]="null">{{ fr() ? 'Toute l’école' : 'Whole school' }}</option>
                  @for (s of sectionKeys; track s) { <option [ngValue]="s">{{ sectionLabel(s) }}</option> }
                </select>
              }
            </div>

            <label class="flex items-center gap-2 md:col-span-12">
              <input type="checkbox" [(ngModel)]="draft.published" name="published"
                class="w-4 h-4 rounded border-slate-300 text-brand-600" />
              <span class="text-sm text-ink">
                {{ fr() ? 'Publier immédiatement' : 'Publish immediately' }}
                <span class="text-xs text-mute">
                  — {{ fr() ? 'sinon le document reste un brouillon, visible de vous seul'
                            : 'otherwise it stays a draft, visible only to you' }}
                </span>
              </span>
            </label>

            <div class="md:col-span-12 flex items-center justify-end gap-2 pt-1">
              <button type="button" (click)="closeForm()"
                class="h-9 px-4 rounded-lg bg-slate-100 text-sm font-semibold text-ink hover:bg-slate-200">
                {{ i18n.t('cancel') }}
              </button>
              <button type="submit" [disabled]="!ready() || busy()"
                class="inline-flex items-center gap-1.5 h-9 px-5 rounded-lg bg-brand-600 hover:bg-brand-700 disabled:opacity-50 text-white text-sm font-semibold">
                <bbc-icon name="check" [s]="16" />
                {{ busy() ? (fr() ? 'Envoi…' : 'Uploading…') : i18n.t('save') }}
              </button>
            </div>
          </form>
        </bbc-card>
      }

      <!-- ---- Liste ---------------------------------------------------------- -->
      <bbc-card [title]="fr() ? 'Bibliothèque' : 'Library'"
        [subtitle]="filtered().length + (fr() ? ' document(s)' : ' document(s)')">
        <div action>
          <bbc-chip-filter [options]="audienceFilters()" [value]="audienceFilter()"
            [allLabel]="fr() ? 'Tous' : 'All'" (change)="audienceFilter.set($any($event))" />
        </div>

        @if (!filtered().length) {
          <bbc-empty icon="doc"
            [label]="items().length
              ? (fr() ? 'Aucun document pour ce filtre.' : 'No document for this filter.')
              : (fr() ? 'Aucun document déposé pour le moment.' : 'No document uploaded yet.')" />
        } @else {
          <div class="space-y-2">
            @for (r of filtered(); track r.id) {
              <div class="flex items-start gap-3 p-3 rounded-lg border border-slate-100 hover:bg-slate-50/60 transition group">
                <div class="w-10 h-10 rounded-lg flex items-center justify-center shrink-0" [class]="CAT[r.category].tone">
                  <bbc-icon [name]="CAT[r.category].icon" [s]="18" />
                </div>

                <div class="flex-1 min-w-0">
                  <div class="flex items-center gap-2 flex-wrap">
                    <button (click)="open(r)" class="font-semibold text-ink hover:text-brand-600 text-left">
                      {{ r.title }}
                    </button>
                    <span class="text-[10px] font-bold uppercase px-1.5 py-0.5 rounded" [class]="AUD[r.audience].badge">
                      {{ audienceLabel(r.audience) }}
                    </span>
                    <span class="text-[10px] font-bold uppercase px-1.5 py-0.5 rounded bg-slate-100 text-slate-600">
                      {{ r.section ? sectionLabel(r.section) : (fr() ? 'Toute l’école' : 'Whole school') }}
                    </span>
                    @if (!r.published) {
                      <span class="text-[10px] font-bold uppercase px-1.5 py-0.5 rounded bg-amber-100 text-amber-700">
                        {{ fr() ? 'Brouillon' : 'Draft' }}
                      </span>
                    }
                  </div>

                  @if (r.description) {
                    <div class="text-[12px] text-mute mt-0.5 line-clamp-2">{{ r.description }}</div>
                  }

                  <div class="flex items-center gap-2 mt-1 text-[11px] text-mute flex-wrap">
                    <span class="inline-flex items-center gap-1">
                      <bbc-icon name="doc" [s]="11" /> {{ r.fileName }} · {{ size(r.byteSize) }}
                    </span>
                    <span>·</span>
                    <span>{{ r.createdAt | date: 'dd/MM/yyyy' }}</span>
                    @if (r.uploadedByName) { <span>·</span> <span>{{ r.uploadedByName }}</span> }
                  </div>
                </div>

                <div class="flex items-center gap-1 self-center shrink-0">
                  <button (click)="open(r)"
                    class="w-8 h-8 rounded-lg text-mute hover:text-brand-600 hover:bg-brand-50 flex items-center justify-center"
                    [title]="fr() ? 'Ouvrir' : 'Open'">
                    <bbc-icon name="download" [s]="15" />
                  </button>
                  @if (r.canEdit) {
                    <button (click)="togglePublish(r)"
                      class="w-8 h-8 rounded-lg flex items-center justify-center"
                      [class]="r.published ? 'text-emerald-600 hover:bg-emerald-50' : 'text-amber-600 hover:bg-amber-50'"
                      [title]="r.published ? (fr() ? 'Dépublier' : 'Unpublish') : (fr() ? 'Publier' : 'Publish')">
                      <bbc-icon [name]="r.published ? 'check' : 'eye'" [s]="15" />
                    </button>
                    <button (click)="edit(r)"
                      class="w-8 h-8 rounded-lg text-mute hover:text-brand-600 hover:bg-brand-50 flex items-center justify-center opacity-0 group-hover:opacity-100 transition"
                      [title]="fr() ? 'Modifier' : 'Edit'">
                      <bbc-icon name="edit" [s]="15" />
                    </button>
                    <button (click)="askRemove(r)"
                      class="w-8 h-8 rounded-lg text-mute hover:text-rose-600 hover:bg-rose-50 flex items-center justify-center opacity-0 group-hover:opacity-100 transition"
                      [title]="fr() ? 'Supprimer' : 'Delete'">
                      <bbc-icon name="trash" [s]="15" />
                    </button>
                  }
                </div>
              </div>
            }
          </div>
        }

        @if (err(); as e) { <div class="mt-3 text-xs rounded-lg px-3 py-2 bg-rose-50 text-rose-600">{{ e }}</div> }
      </bbc-card>
    </div>

    @if (pendingRemoval(); as r) {
      <bbc-confirm [title]="fr() ? 'Supprimer ce document ?' : 'Delete this document?'"
        [body]="removalBody(r)"
        [confirmLabel]="fr() ? 'Supprimer' : 'Delete'" [cancelLabel]="i18n.t('cancel')"
        (confirm)="remove(r)" (cancel)="pendingRemoval.set(null)" />
    }
  `,
})
export class LibraryComponent {
  protected i18n = inject(I18nService);
  private auth = inject(AuthService);
  private api = inject(LibraryApi);

  protected fr = () => this.i18n.lang() === 'fr';
  protected canWrite = this.auth.can('library', 'write');

  /** Extensions proposées par le sélecteur — le serveur applique la même liste. */
  protected readonly ACCEPT =
    '.pdf,.doc,.docx,.odt,.rtf,.xls,.xlsx,.ods,.ppt,.pptx,.odp,.txt,.csv,.jpg,.jpeg,.png,.webp,.gif';

  /** Destinataires : libellé, aide, et pastille de la liste. */
  protected readonly AUD: Record<Audience, { icon: string; tone: string; badge: string }> = {
    all: { icon: 'users', tone: 'bg-brand-50 text-brand-600', badge: 'bg-brand-100 text-brand-700' },
    staff: { icon: 'building', tone: 'bg-violet-50 text-violet-600', badge: 'bg-violet-100 text-violet-700' },
    parents: { icon: 'home', tone: 'bg-emerald-50 text-emerald-600', badge: 'bg-emerald-100 text-emerald-700' },
  };

  protected readonly CAT: Record<Category, { icon: string; tone: string }> = {
    circular: { icon: 'mail', tone: 'bg-brand-50 text-brand-600' },
    pedagogy: { icon: 'book', tone: 'bg-emerald-50 text-emerald-600' },
    admin: { icon: 'building', tone: 'bg-slate-100 text-slate-600' },
    form: { icon: 'receipt', tone: 'bg-amber-50 text-amber-600' },
    other: { icon: 'doc', tone: 'bg-slate-100 text-slate-600' },
  };

  protected readonly audienceKeys: Audience[] = ['all', 'staff', 'parents'];
  protected readonly categoryKeys: Category[] = ['circular', 'pedagogy', 'admin', 'form', 'other'];
  protected readonly sectionKeys: Exclude<Section, null>[] = ['maternelle', 'primary', 'secondary'];

  protected items = signal<ResourceView[]>([]);
  protected err = signal<string | null>(null);
  protected busy = signal(false);
  protected showForm = signal(false);
  protected editId = signal<string | null>(null);
  protected file = signal<File | null>(null);
  protected audienceFilter = signal<Audience | null>(null);
  protected pendingRemoval = signal<ResourceView | null>(null);

  protected draft: ResourceUpsert = this.blank();

  /** Section imposée au compte : un admin de cycle ne choisit pas son périmètre. */
  protected lockedSection = computed(() => (this.auth.schoolWide() ? null : this.auth.section()));

  protected publishedCount = computed(() => this.items().filter((r) => r.published).length);
  protected draftCount = computed(() => this.items().filter((r) => !r.published).length);

  protected filtered = computed(() => {
    const a = this.audienceFilter();
    return a ? this.items().filter((r) => r.audience === a) : this.items();
  });

  protected audienceFilters = computed(() =>
    this.audienceKeys.map((a) => ({ value: a, label: this.audienceLabel(a) })));

  constructor() {
    this.reload();
  }

  // ---- Libellés -------------------------------------------------------------

  protected audienceLabel(a: Audience): string {
    const fr = this.fr();
    if (a === 'all') return fr ? 'Tout le monde' : 'Everyone';
    if (a === 'staff') return fr ? 'Personnel' : 'Staff';
    return fr ? 'Parents' : 'Parents';
  }

  protected audienceHint(a: Audience): string {
    const fr = this.fr();
    if (a === 'all') return fr ? 'Personnel et familles' : 'Staff and families';
    if (a === 'staff') return fr ? 'Enseignants et administration' : 'Teachers and administration';
    return fr ? 'Les familles uniquement' : 'Families only';
  }

  protected categoryLabel(c: Category): string {
    const fr = this.fr();
    const map: Record<Category, [string, string]> = {
      circular: ['Circulaire', 'Circular'],
      pedagogy: ['Pédagogie', 'Teaching'],
      admin: ['Administratif', 'Administrative'],
      form: ['Formulaire', 'Form'],
      other: ['Autre', 'Other'],
    };
    return fr ? map[c][0] : map[c][1];
  }

  protected sectionLabel(s: Section): string {
    if (!s) return this.fr() ? 'Toute l’école' : 'Whole school';
    const map: Record<string, [string, string]> = {
      maternelle: ['Maternelle', 'Nursery'],
      primary: ['Primaire', 'Primary'],
      secondary: ['Secondaire', 'Secondary'],
    };
    return this.fr() ? map[s][0] : map[s][1];
  }

  protected size = (bytes: number) => fmtBytes(bytes, this.fr());

  // ---- Formulaire -----------------------------------------------------------

  private blank(): ResourceUpsert {
    return { title: '', description: '', category: 'circular', audience: 'all', section: null, published: true };
  }

  /** Prêt à envoyer : une fiche complète, et un fichier tant qu'on en dépose un. */
  protected ready = computed(() => !!this.draft.title.trim() && (!!this.editId() || !!this.file()));

  protected openForm(): void {
    this.editId.set(null);
    this.file.set(null);
    this.draft = this.blank();
    this.err.set(null);
    this.showForm.set(true);
  }

  protected closeForm(): void {
    this.showForm.set(false);
    this.editId.set(null);
    this.file.set(null);
    this.draft = this.blank();
  }

  protected edit(r: ResourceView): void {
    this.editId.set(r.id);
    this.file.set(null);
    this.draft = {
      title: r.title,
      description: r.description ?? '',
      category: r.category,
      audience: r.audience,
      section: r.section,
      published: r.published,
    };
    this.err.set(null);
    this.showForm.set(true);
  }

  protected pick(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.file.set(input.files?.[0] ?? null);
  }

  protected save(): void {
    if (!this.ready() || this.busy()) return;
    this.err.set(null);
    this.busy.set(true);
    const id = this.editId();
    const done = { next: () => { this.busy.set(false); this.closeForm(); this.reload(); }, error: this.fail };
    if (id) {
      this.api.update(id, this.draft).subscribe(done);
    } else {
      this.api.create(this.file()!, this.draft).subscribe(done);
    }
  }

  // ---- Actions de liste -----------------------------------------------------

  protected reload(): void {
    this.api.list().subscribe({ next: (r) => this.items.set(r), error: this.fail });
  }

  /** Ouvre le fichier — le jeton voyage en en-tête, d'où le passage par un blob. */
  protected open(r: ResourceView): void {
    this.err.set(null);
    this.api.file(r.id).subscribe({ next: (b) => openBlob(b, r.fileName), error: this.fail });
  }

  protected togglePublish(r: ResourceView): void {
    this.err.set(null);
    this.api.update(r.id, {
      title: r.title,
      description: r.description,
      category: r.category,
      audience: r.audience,
      section: r.section,
      published: !r.published,
    }).subscribe({ next: () => this.reload(), error: this.fail });
  }

  protected askRemove(r: ResourceView): void { this.pendingRemoval.set(r); }

  /** Le texte de la confirmation — hors du gabarit, où les guillemets se croisent. */
  protected removalBody(r: ResourceView): string {
    return this.fr()
      ? `« ${r.title} » sera retiré pour tous, et le fichier effacé du stockage.`
      : `“${r.title}” will be withdrawn for everyone and the file erased from storage.`;
  }

  protected remove(r: ResourceView): void {
    this.pendingRemoval.set(null);
    this.err.set(null);
    this.api.remove(r.id).subscribe({ next: () => this.reload(), error: this.fail });
  }

  private fail = (e: any) => {
    this.busy.set(false);
    this.err.set(e?.error?.message ?? (this.fr() ? 'Opération impossible.' : 'Operation failed.'));
  };
}
