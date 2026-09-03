import {
  Component, ChangeDetectionStrategy, input, output, computed, signal, effect,
  Directive, TemplateRef, contentChildren,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { IconComponent } from './icon';
import { ListPaginationComponent, paginateRows } from './list-pagination';

/**
 * Column definition for {@link DataTableComponent}.
 * `value` is the accessor used for sorting (and the default text cell when no
 * custom template is supplied for the column key).
 */
export interface Column<T = any> {
  key: string;
  label: string;
  align?: 'left' | 'right' | 'center';
  sortable?: boolean;
  width?: string;
  value?: (row: T) => string | number | null | undefined;
}

/** Marks an `<ng-template bbcCell="key">` as the renderer for a column. Context: `$implicit` = row. */
@Directive({ selector: '[bbcCell]', standalone: true })
export class CellTemplateDirective {
  bbcCell = input.required<string>();
  constructor(public tpl: TemplateRef<unknown>) {}
}

/** High-density, sortable data table. Cells are projected per-column via `[bbcCell]` templates. */
@Component({
  selector: 'bbc-data-table',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgTemplateOutlet, IconComponent, ListPaginationComponent],
  styles: [`
    @media (max-width: 899.98px) {
      .responsive-data-table-sort { display: flex; }
      .responsive-data-table-scroll {
        max-height: none !important;
        overflow: visible;
      }
      .responsive-data-table,
      .responsive-data-table tbody { display: block; width: 100%; }
      .responsive-data-table thead { display: none; }
      .responsive-data-table tbody { display: grid; gap: .75rem; padding: .25rem 0; }
      .responsive-data-table tbody > tr {
        display: block;
        overflow: hidden;
        border: 1px solid #e2e8f0;
        border-radius: .875rem;
        background: white;
        box-shadow: 0 1px 2px rgba(15, 34, 56, .04);
      }
      .responsive-data-table td {
        position: static !important;
        display: grid !important;
        grid-template-columns: minmax(5.5rem, 34%) minmax(0, 1fr);
        align-items: start;
        gap: .75rem;
        min-width: 0 !important;
        width: auto !important;
        padding: .7rem .8rem !important;
        border-bottom: 1px solid #f1f5f9;
        text-align: left !important;
        overflow-wrap: anywhere;
      }
      .responsive-data-table td:last-child { border-bottom: 0; }
      .responsive-data-table td::before {
        content: attr(data-label);
        color: #64748b;
        font-size: .65rem;
        font-weight: 700;
        line-height: 1.3;
        letter-spacing: .04em;
        text-transform: uppercase;
      }
      .responsive-data-table td > * { min-width: 0; max-width: 100%; }
      .responsive-data-table td[data-select] {
        display: flex !important;
        min-height: 2.75rem;
        align-items: center;
        justify-content: space-between;
      }
      .responsive-data-table td[data-select]::before { content: attr(data-label); }
      .responsive-data-table td[data-select] input[type="checkbox"] { width: 1.25rem; height: 1.25rem; }
      .responsive-data-table td[data-empty] { display: block !important; border: 0; }
      .responsive-data-table td[data-empty]::before { content: none; }
    }
  `],
  template: `
    @if (sortableColumns().length) {
      <div class="responsive-data-table-sort hidden mb-3 items-end gap-2 rounded-xl border border-slate-200 bg-slate-50 p-3">
        <label class="min-w-0 flex-1 text-[11px] font-bold uppercase tracking-wide text-mute">
          {{ language() === 'fr' ? 'Trier par' : 'Sort by' }}
          <select class="mt-1 block min-h-11 w-full rounded-lg border border-slate-300 bg-white px-3 text-base font-semibold normal-case tracking-normal text-ink"
            [value]="sortKey() ?? ''" (change)="selectSort($any($event.target).value)">
            <option value="">{{ language() === 'fr' ? 'Ordre initial' : 'Default order' }}</option>
            @for (col of sortableColumns(); track col.key) { <option [value]="col.key">{{ col.label }}</option> }
          </select>
        </label>
        <button type="button" (click)="reverseSort()" [disabled]="!sortKey()"
          class="inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-lg border border-slate-300 bg-white text-brand-700 disabled:opacity-40"
          [attr.aria-label]="language() === 'fr' ? 'Inverser le tri' : 'Reverse sort'">
          <bbc-icon [name]="sortDir() === 'asc' ? 'chevronDown' : 'chevronRight'" [s]="16" />
        </button>
      </div>
    }
    <div class="responsive-data-table-scroll overflow-x-auto scroll-y" [style.max-height]="maxHeight()">
      <table class="responsive-data-table w-full text-sm border-collapse">
        <thead class="sticky top-0 z-10 bg-slate-50">
          <tr class="border-b border-slate-200">
            @if (selectable()) {
              <th class="pl-4 pr-1 py-2.5 w-9">
                <input type="checkbox" class="w-4 h-4 rounded border-slate-300 text-brand-600 focus:ring-brand-400 align-middle"
                  [checked]="allSelected()" [indeterminate]="someSelected()"
                  (change)="toggleAll()" [attr.aria-label]="selectAllLabel()" />
              </th>
            }
            @for (col of columns(); track col.key) {
              <th [style.width]="col.width"
                class="px-4 py-2.5 text-[11px] uppercase tracking-wide font-bold text-mute select-none"
                [class]="alignCls(col) + (col.sortable !== false ? ' cursor-pointer hover:text-ink' : '')"
                (click)="col.sortable !== false && toggleSort(col.key)">
                <span class="inline-flex items-center gap-1" [class.justify-end]="col.align === 'right'">
                  {{ col.label }}
                  @if (sortKey() === col.key) {
                    <bbc-icon [name]="sortDir() === 'asc' ? 'chevronDown' : 'chevronRight'" [s]="12" />
                  }
                </span>
              </th>
            }
          </tr>
        </thead>
        <tbody>
          @for (row of visibleRows(); track trackBy()(row); let i = $index) {
            <tr (click)="rowClick.emit(row)"
              class="border-b border-slate-50 last:border-0 transition"
              [class]="(rowClick ? 'cursor-pointer ' : '') + (isActive(row) ? 'bg-brand-50' : isSelected(row) ? 'bg-brand-50/60' : 'hover:bg-slate-50')">
              @if (selectable()) {
                <!-- La case ne doit pas ouvrir la fiche : cocher et consulter sont deux gestes distincts. -->
                <td data-select [attr.data-label]="language() === 'fr' ? 'Sélectionner' : 'Select'" class="pl-4 pr-1 py-2.5 align-middle" (click)="$event.stopPropagation()">
                  <input type="checkbox" class="w-4 h-4 rounded border-slate-300 text-brand-600 focus:ring-brand-400 align-middle"
                    [checked]="isSelected(row)" (change)="toggleRow(row)" />
                </td>
              }
              @for (col of columns(); track col.key) {
                <td [attr.data-label]="col.label" class="px-4 py-2.5 align-middle" [class]="alignCls(col)">
                  @if (tplFor(col.key); as t) {
                    <ng-container [ngTemplateOutlet]="t" [ngTemplateOutletContext]="{ $implicit: row, index: i }" />
                  } @else {
                    {{ text(col, row) }}
                  }
                </td>
              }
            </tr>
          } @empty {
            <tr>
              <td data-empty [attr.colspan]="columns().length + (selectable() ? 1 : 0)" class="py-12">
                <div class="flex flex-col items-center justify-center text-center">
                  <div class="w-12 h-12 rounded-full bg-slate-100 text-slate-400 flex items-center justify-center mb-2">
                    <bbc-icon name="search" />
                  </div>
                  <div class="text-sm text-mute">{{ emptyLabel() }}</div>
                </div>
              </td>
            </tr>
          }
        </tbody>
      </table>
    </div>
    @if (pagination() && sorted().length) {
      <bbc-list-pagination
        [total]="sorted().length"
        [page]="currentPage()"
        [pageSize]="rowsPerPage()"
        [language]="language()"
        (pageChange)="currentPage.set($event)"
        (pageSizeChange)="changePageSize($event)" />
    }
  `,
})
export class DataTableComponent<T = any> {
  columns = input.required<Column<T>[]>();
  rows = input.required<T[]>();
  emptyLabel = input('Aucun résultat');
  maxHeight = input('640px');
  pagination = input(false);
  initialPageSize = input(25);
  language = input<'fr' | 'en'>('fr');
  /** Identity for change tracking + active-row matching. */
  trackBy = input<(row: T) => unknown>((r: T) => r as unknown);
  activeId = input<unknown>(null);

  /** Affiche la colonne de cases à cocher (actions groupées). */
  selectable = input(false);
  /** Identifiants cochés — la sélection est tenue par le parent, la table ne fait que l'afficher. */
  selectedIds = input<ReadonlySet<unknown>>(new Set());
  selectAllLabel = input('Tout sélectionner');

  rowClick = output<T>();
  /** Nouvelle sélection après un clic sur une case (ou sur celle d'en-tête). */
  selectionChange = output<Set<unknown>>();

  private cells = contentChildren(CellTemplateDirective);

  protected sortKey = signal<string | null>(null);
  protected sortDir = signal<'asc' | 'desc'>('asc');
  protected currentPage = signal(1);
  protected rowsPerPage = signal(25);
  protected sortableColumns = computed(() => this.columns().filter((col) => col.sortable !== false));

  protected sorted = computed<T[]>(() => {
    const key = this.sortKey();
    const list = [...this.rows()];
    if (!key) return list;
    const col = this.columns().find((c) => c.key === key);
    if (!col) return list;
    const dir = this.sortDir() === 'asc' ? 1 : -1;
    const get = (r: T) => col.value?.(r) ?? (r as any)?.[key] ?? '';
    return list.sort((a, b) => {
      const va = get(a), vb = get(b);
      if (typeof va === 'number' && typeof vb === 'number') return (va - vb) * dir;
      return String(va).localeCompare(String(vb), undefined, { numeric: true }) * dir;
    });
  });

  protected visibleRows = computed(() => this.pagination()
    ? paginateRows(this.sorted(), this.currentPage(), this.rowsPerPage())
    : this.sorted());

  private readonly resetPage = effect(() => {
    this.rows();
    this.currentPage.set(1);
  }, { allowSignalWrites: true });

  private readonly syncInitialPageSize = effect(() => {
    this.rowsPerPage.set(Math.max(1, this.initialPageSize()));
    this.currentPage.set(1);
  }, { allowSignalWrites: true });

  protected toggleSort(key: string): void {
    if (this.sortKey() === key) {
      this.sortDir.set(this.sortDir() === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortKey.set(key);
      this.sortDir.set('asc');
    }
  }

  protected selectSort(key: string): void {
    this.sortKey.set(key || null);
    this.sortDir.set('asc');
  }

  protected reverseSort(): void {
    if (this.sortKey()) this.sortDir.set(this.sortDir() === 'asc' ? 'desc' : 'asc');
  }

  protected changePageSize(size: number): void {
    this.rowsPerPage.set(size);
    this.currentPage.set(1);
  }

  protected tplFor(key: string): TemplateRef<unknown> | null {
    return this.cells().find((c) => c.bbcCell() === key)?.tpl ?? null;
  }

  protected text(col: Column<T>, row: T): string {
    const v = col.value?.(row) ?? (row as any)?.[col.key];
    return v === null || v === undefined || v === '' ? '—' : String(v);
  }

  protected alignCls(col: Column<T>): string {
    return col.align === 'right' ? 'text-right' : col.align === 'center' ? 'text-center' : 'text-left';
  }

  protected isActive(row: T): boolean {
    const id = this.activeId();
    return id != null && this.trackBy()(row) === id;
  }

  // ---- Sélection multiple --------------------------------------------------
  protected isSelected(row: T): boolean {
    return this.selectedIds().has(this.trackBy()(row));
  }

  /** Toutes les lignes affichées sont cochées — la case d'en-tête est pleine. */
  protected allSelected = computed(() => {
    const ids = this.selectedIds();
    const rows = this.rows();
    return rows.length > 0 && rows.every((r) => ids.has(this.trackBy()(r)));
  });

  /** Une partie seulement — la case d'en-tête passe en état indéterminé. */
  protected someSelected = computed(() => {
    const ids = this.selectedIds();
    const rows = this.rows();
    return rows.some((r) => ids.has(this.trackBy()(r))) && !this.allSelected();
  });

  protected toggleRow(row: T): void {
    const next = new Set(this.selectedIds());
    const id = this.trackBy()(row);
    if (!next.delete(id)) next.add(id);
    this.selectionChange.emit(next);
  }

  /**
   * La case d'en-tête ne porte que sur les lignes visibles : après un filtre,
   * « tout cocher » ne doit pas embarquer des fiches que l'écran ne montre pas.
   */
  protected toggleAll(): void {
    const next = new Set(this.selectedIds());
    const on = !this.allSelected();
    for (const r of this.rows()) {
      const id = this.trackBy()(r);
      if (on) next.add(id); else next.delete(id);
    }
    this.selectionChange.emit(next);
  }
}
