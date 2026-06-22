import {
  Component, ChangeDetectionStrategy, input, output, computed, signal,
  Directive, TemplateRef, contentChildren,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { IconComponent } from './icon';

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
  imports: [NgTemplateOutlet, IconComponent],
  template: `
    <div class="overflow-x-auto scroll-y" [style.max-height]="maxHeight()">
      <table class="w-full text-sm border-collapse">
        <thead class="sticky top-0 z-10 bg-slate-50">
          <tr class="border-b border-slate-200">
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
          @for (row of sorted(); track trackBy()(row); let i = $index) {
            <tr (click)="rowClick.emit(row)"
              class="border-b border-slate-50 last:border-0 transition"
              [class]="(rowClick ? 'cursor-pointer ' : '') + (isActive(row) ? 'bg-brand-50' : 'hover:bg-slate-50')">
              @for (col of columns(); track col.key) {
                <td class="px-4 py-2.5 align-middle" [class]="alignCls(col)">
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
              <td [attr.colspan]="columns().length" class="py-12">
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
  `,
})
export class DataTableComponent<T = any> {
  columns = input.required<Column<T>[]>();
  rows = input.required<T[]>();
  emptyLabel = input('Aucun résultat');
  maxHeight = input('640px');
  /** Identity for change tracking + active-row matching. */
  trackBy = input<(row: T) => unknown>((r: T) => r as unknown);
  activeId = input<unknown>(null);

  rowClick = output<T>();

  private cells = contentChildren(CellTemplateDirective);

  protected sortKey = signal<string | null>(null);
  protected sortDir = signal<'asc' | 'desc'>('asc');

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

  protected toggleSort(key: string): void {
    if (this.sortKey() === key) {
      this.sortDir.set(this.sortDir() === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortKey.set(key);
      this.sortDir.set('asc');
    }
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
}
