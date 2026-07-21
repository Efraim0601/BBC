import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { SetupApi, ClassView, SubjectView } from '../../core/setup.api';
import { ClassKitApi, ResourceKind, ClassResourceView, ResourceItem, ResourceItemUpsert } from './classkit.api';
import { IconComponent, CardComponent, TabsComponent, EmptyComponent } from '../../core/ui';

/**
 * Class resources manager — staff configure the supplies (fournitures) and the payable
 * book list (livres) for a class, then PUBLISH so parents can see them. Compartmentalised:
 * the class dropdown only lists the active parcours' classes (server-side scope filter).
 */
@Component({
  selector: 'bbc-classkit',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, DecimalPipe, IconComponent, CardComponent, TabsComponent, EmptyComponent],
  template: `
    <bbc-tabs [tabs]="kindTabs()" [value]="kind()" (change)="switchKind($any($event))" />

    <bbc-card [title]="kind() === 'books' ? (fr() ? 'Manuels scolaires' : 'School textbooks') : (fr() ? 'Fournitures' : 'Supplies')"
      [subtitle]="fr() ? 'Configurez la liste par classe, puis publiez-la pour les parents'
                       : 'Configure the per-class list, then publish it for parents'">
      <div action class="flex items-center gap-2">
        <select [(ngModel)]="classId" (ngModelChange)="reload()" name="cls"
          class="h-9 px-3 rounded-lg border border-slate-200 bg-white text-sm">
          <option value="">{{ fr() ? '— Choisir une classe —' : '— Choose a class —' }}</option>
          @for (c of classes(); track c.id) { <option [value]="c.id">{{ c.name }}</option> }
        </select>
      </div>

      @if (!classId) {
        <bbc-empty icon="building" [label]="fr() ? 'Sélectionnez une classe pour configurer la liste.' : 'Select a class to configure the list.'" />
      } @else if (view(); as v) {
        <!-- Publish state bar -->
        <div class="flex items-center justify-between mb-4 p-3 rounded-lg"
          [class]="v.published ? 'bg-emerald-50' : 'bg-amber-50'">
          <div class="flex items-center gap-2 text-sm font-semibold"
            [class]="v.published ? 'text-emerald-700' : 'text-amber-700'">
            <bbc-icon [name]="v.published ? 'check' : 'eye'" [s]="16" />
            {{ v.published ? (fr() ? 'Publié — visible par les parents' : 'Published — visible to parents')
                           : (fr() ? 'Brouillon — non visible' : 'Draft — not visible') }}
          </div>
          @if (canWrite) {
            <button (click)="togglePublish(v)" [disabled]="!v.items.length && !v.published"
              class="h-8 px-3.5 text-sm font-semibold rounded-lg disabled:opacity-50"
              [class]="v.published ? 'bg-amber-100 text-amber-800 hover:bg-amber-200' : 'bg-emerald-600 text-white hover:bg-emerald-700'">
              {{ v.published ? (fr() ? 'Dépublier' : 'Unpublish') : (fr() ? 'Publier' : 'Publish') }}
            </button>
          }
        </div>

        <!-- Add / edit form -->
        @if (canWrite) {
          <form (ngSubmit)="save()" class="grid grid-cols-1 md:grid-cols-12 gap-3 mb-4 p-3 rounded-lg bg-slate-50">
            <label class="block md:col-span-5">
              <span class="text-xs font-semibold text-ink">{{ kind() === 'books' ? (fr() ? 'Titre du livre' : 'Book title') : (fr() ? 'Fourniture' : 'Supply') }} *</span>
              <input [(ngModel)]="draft.label" name="label" required
                class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
            </label>
            @if (kind() === 'books') {
              <label class="block md:col-span-3">
                <span class="text-xs font-semibold text-ink">{{ fr() ? 'Prix (FCFA)' : 'Price (FCFA)' }}</span>
                <input type="number" min="0" [(ngModel)]="draft.price" name="price"
                  class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
              </label>
              <label class="block md:col-span-4">
                <span class="text-xs font-semibold text-ink">{{ fr() ? 'Matière' : 'Subject' }}</span>
                <select [(ngModel)]="draft.subjectCode" name="subj"
                  class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400">
                  <option [ngValue]="null">{{ fr() ? '— Aucune —' : '— None —' }}</option>
                  @for (s of subjects(); track s.id) { <option [ngValue]="s.code">{{ subjectLabel(s) }}</option> }
                </select>
              </label>
              <label class="block md:col-span-4">
                <span class="text-xs font-semibold text-ink">{{ fr() ? 'Auteur / édition' : 'Author / edition' }}</span>
                <input [(ngModel)]="draft.author" name="author"
                  class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
              </label>
              <label class="flex items-center gap-2 md:col-span-4 mt-1">
                <input type="checkbox" [(ngModel)]="draft.mandatory" name="mand"
                  class="w-4 h-4 rounded border-slate-300 text-brand-600" />
                <span class="text-xs font-semibold text-ink">{{ fr() ? 'Manuel obligatoire' : 'Mandatory textbook' }}</span>
              </label>
            } @else {
              <label class="block md:col-span-3">
                <span class="text-xs font-semibold text-ink">{{ fr() ? 'Quantité' : 'Quantity' }}</span>
                <input type="number" min="1" [(ngModel)]="draft.quantity" name="qty"
                  class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
              </label>
            }
            <label class="block md:col-span-4">
              <span class="text-xs font-semibold text-ink">{{ fr() ? 'Note' : 'Note' }}</span>
              <input [(ngModel)]="draft.note" name="note"
                class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
            </label>
            <div class="md:col-span-12 flex items-center justify-end gap-2">
              @if (editId()) {
                <button type="button" (click)="resetForm()" class="h-9 px-4 rounded-lg bg-slate-100 text-sm font-semibold text-ink hover:bg-slate-200">{{ i18n.t('cancel') }}</button>
              }
              <button type="submit" [disabled]="!draft.label" class="h-9 px-5 rounded-lg bg-brand-600 hover:bg-brand-700 disabled:opacity-50 text-white text-sm font-semibold">
                {{ editId() ? (fr() ? 'Mettre à jour' : 'Update') : (fr() ? 'Ajouter' : 'Add') }}
              </button>
            </div>
          </form>
        }

        @if (v.items.length) {
          <div class="overflow-x-auto">
          <table class="min-w-full text-sm">
            <thead><tr class="border-b border-slate-100 text-[11px] uppercase text-mute text-left">
              <th class="py-2 pr-3 font-semibold">{{ kind() === 'books' ? (fr() ? 'Titre' : 'Title') : (fr() ? 'Fourniture' : 'Supply') }}</th>
              @if (kind() === 'books') {
                <th class="py-2 px-3 font-semibold">{{ fr() ? 'Matière' : 'Subject' }}</th>
                <th class="py-2 px-3 font-semibold">{{ fr() ? 'Auteur' : 'Author' }}</th>
              }
              <th class="py-2 px-3 font-semibold text-center">{{ kind() === 'books' ? (fr() ? 'Prix' : 'Price') : (fr() ? 'Qté' : 'Qty') }}</th>
              <th class="py-2 px-3 font-semibold">{{ fr() ? 'Note' : 'Note' }}</th>
              <th></th>
            </tr></thead>
            <tbody>
              @for (it of v.items; track it.id) {
                <tr class="border-b border-slate-50 hover:bg-slate-50/40">
                  <td class="py-2 pr-3 font-semibold text-ink">
                    {{ it.label }}
                    @if (kind() === 'books' && it.mandatory === false) {
                      <span class="ml-1.5 text-[10px] font-semibold px-1.5 py-0.5 rounded bg-slate-100 text-slate-500">{{ fr() ? 'optionnel' : 'optional' }}</span>
                    }
                  </td>
                  @if (kind() === 'books') {
                    <td class="py-2 px-3 text-mute">{{ subjectName(it.subjectCode) }}</td>
                    <td class="py-2 px-3 text-mute">{{ it.author || '—' }}</td>
                  }
                  <td class="py-2 px-3 text-center">
                    {{ kind() === 'books' ? (it.price != null ? (it.price | number) + ' F' : '—') : (it.quantity ?? '—') }}
                  </td>
                  <td class="py-2 px-3 text-mute">{{ it.note }}</td>
                  <td class="py-2 pl-3 text-right whitespace-nowrap">
                    @if (canWrite) {
                      <button (click)="edit(it)" class="text-mute hover:text-brand-600 px-1.5"><bbc-icon name="edit" [s]="15" /></button>
                      <button (click)="remove(it)" class="text-mute hover:text-rose-600 px-1.5"><bbc-icon name="trash" [s]="15" /></button>
                    }
                  </td>
                </tr>
              }
              @if (kind() === 'books' && booksTotal() > 0) {
                <tr class="font-semibold text-ink">
                  <td class="py-2 pr-3 text-right" [attr.colspan]="3">{{ fr() ? 'Total' : 'Total' }}</td>
                  <td class="py-2 px-3 text-center">{{ booksTotal() | number }} F</td>
                  <td colspan="2"></td>
                </tr>
              }
            </tbody>
          </table>
          </div>
        } @else {
          <bbc-empty icon="book" [label]="fr() ? 'Liste vide — ajoutez des éléments.' : 'Empty list — add items.'" />
        }
      }

      @if (err(); as e) { <div class="mt-3 text-xs rounded-lg px-3 py-2 bg-rose-50 text-rose-600">{{ e }}</div> }
    </bbc-card>
  `,
})
export class ClasskitComponent {
  protected i18n = inject(I18nService);
  private auth = inject(AuthService);
  private setupApi = inject(SetupApi);
  private api = inject(ClassKitApi);

  protected fr = () => this.i18n.lang() === 'fr';
  protected canWrite = this.auth.can('classkit', 'write');

  protected kind = signal<ResourceKind>('supplies');
  protected classes = signal<ClassView[]>([]);
  protected subjects = signal<SubjectView[]>([]);
  protected classId = '';
  protected view = signal<ClassResourceView | null>(null);
  protected err = signal<string | null>(null);

  protected editId = signal<string | null>(null);
  protected draft: ResourceItemUpsert = this.blank();

  protected kindTabs = computed(() => [
    { id: 'supplies', label: this.fr() ? 'Fournitures' : 'Supplies' },
    { id: 'books', label: this.fr() ? 'Manuels scolaires' : 'School textbooks' },
  ]);

  protected booksTotal = computed(() =>
    (this.view()?.items ?? []).reduce((sum, it) => sum + (it.price ?? 0), 0));

  constructor() {
    this.setupApi.listClasses().subscribe((c) => this.classes.set(c));
    this.setupApi.listSubjects().subscribe((s) => this.subjects.set(s));
  }

  protected subjectLabel(s: SubjectView): string {
    const l = s.label || {};
    return (this.fr() ? l['fr'] : l['en']) || l['fr'] || l['en'] || s.code;
  }
  protected subjectName(code: string | null): string {
    if (!code) return '—';
    const s = this.subjects().find((x) => x.code === code);
    return s ? this.subjectLabel(s) : code;
  }

  private blank(): ResourceItemUpsert {
    return { label: '', quantity: null, price: null, note: '', subjectCode: null, author: '', mandatory: true };
  }
  private fail = (e: any) => this.err.set(e?.error?.message ?? (this.fr() ? 'Opération impossible.' : 'Operation failed.'));

  protected switchKind(k: ResourceKind): void { this.kind.set(k); this.resetForm(); this.reload(); }

  protected reload(): void {
    this.view.set(null);
    this.resetForm();
    if (!this.classId) return;
    this.api.ofClass(this.kind(), this.classId).subscribe({ next: (v) => this.view.set(v), error: this.fail });
  }

  protected resetForm(): void { this.editId.set(null); this.draft = this.blank(); }

  protected edit(it: ResourceItem): void {
    this.editId.set(it.id);
    this.draft = {
      label: it.label, quantity: it.quantity, price: it.price, note: it.note,
      subjectCode: it.subjectCode, author: it.author ?? '', mandatory: it.mandatory ?? true,
    };
  }

  protected save(): void {
    if (!this.classId || !this.draft.label) return;
    this.err.set(null);
    const id = this.editId();
    const req = id
      ? this.api.updateItem(this.kind(), id, this.draft)
      : this.api.addItem(this.kind(), this.classId, this.draft);
    req.subscribe({ next: () => { this.resetForm(); this.reload(); }, error: this.fail });
  }

  protected remove(it: ResourceItem): void {
    if (!confirm(this.fr() ? `Supprimer « ${it.label} » ?` : `Delete "${it.label}"?`)) return;
    this.err.set(null);
    this.api.deleteItem(this.kind(), it.id).subscribe({ next: () => this.reload(), error: this.fail });
  }

  protected togglePublish(v: ClassResourceView): void {
    this.err.set(null);
    this.api.publish(this.kind(), this.classId, !v.published)
      .subscribe({ next: (r) => this.view.set(r), error: this.fail });
  }
}
