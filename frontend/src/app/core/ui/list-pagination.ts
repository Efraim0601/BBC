import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { IconComponent } from './icon';

/**
 * Shared pagination footer for browse-only lists.
 *
 * Pages are one-based at the component boundary so screens and assistive text
 * use the same numbering the user sees. The data helper below intentionally
 * clamps invalid values, which keeps a list usable after a filter or deletion
 * reduces the number of pages.
 */
@Component({
  selector: 'bbc-list-pagination',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    @if (total() > 0) {
      <div class="flex flex-col gap-3 border-t border-slate-100 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
        <div class="flex flex-wrap items-center gap-x-4 gap-y-2 text-xs text-mute">
          <span>
            {{ fr() ? 'Affichage' : 'Showing' }}
            <strong class="text-ink">{{ firstItem() }}–{{ lastItem() }}</strong>
            {{ fr() ? 'sur' : 'of' }}
            <strong class="text-ink">{{ total() }}</strong>
          </span>
          <label class="inline-flex items-center gap-2">
            <span>{{ fr() ? 'Lignes par page' : 'Rows per page' }}</span>
            <select
              (change)="changeSize($any($event.target).value)"
              class="h-8 rounded-lg border border-slate-200 bg-white px-2 text-xs font-semibold text-ink focus:border-brand-400 focus:outline-none"
              [attr.aria-label]="fr() ? 'Lignes par page' : 'Rows per page'">
              @for (size of pageSizeOptions(); track size) {
                <option [value]="size" [selected]="size === pageSize()">{{ size }}</option>
              }
            </select>
          </label>
        </div>

        @if (pageCount() > 1) {
          <nav class="flex items-center gap-1" [attr.aria-label]="fr() ? 'Pagination' : 'Pagination'">
            <button type="button" (click)="go(page() - 1)" [disabled]="page() <= 1"
              class="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 bg-white text-mute hover:border-brand-300 hover:text-brand-700 disabled:cursor-not-allowed disabled:opacity-35"
              [attr.aria-label]="fr() ? 'Page précédente' : 'Previous page'">
              <bbc-icon name="chevronLeft" [s]="14" />
            </button>

            @for (item of pageItems(); track $index) {
              @if (item === null) {
                <span class="inline-flex h-8 min-w-6 items-center justify-center px-1 text-xs text-mute">…</span>
              } @else {
                <button type="button" (click)="go(item)"
                  class="inline-flex h-8 min-w-8 items-center justify-center rounded-lg border px-2 text-xs font-bold transition"
                  [class]="item === page()
                    ? 'border-brand-600 bg-brand-600 text-white'
                    : 'border-slate-200 bg-white text-mute hover:border-brand-300 hover:text-brand-700'"
                  [attr.aria-current]="item === page() ? 'page' : null"
                  [attr.aria-label]="(fr() ? 'Page ' : 'Page ') + item">
                  {{ item }}
                </button>
              }
            }

            <button type="button" (click)="go(page() + 1)" [disabled]="page() >= pageCount()"
              class="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 bg-white text-mute hover:border-brand-300 hover:text-brand-700 disabled:cursor-not-allowed disabled:opacity-35"
              [attr.aria-label]="fr() ? 'Page suivante' : 'Next page'">
              <bbc-icon name="chevronRight" [s]="14" />
            </button>
          </nav>
        }
      </div>
    }
  `,
})
export class ListPaginationComponent {
  total = input(0);
  page = input(1);
  pageSize = input(25);
  pageSizeOptions = input<number[]>([10, 25, 50]);
  language = input<'fr' | 'en'>('fr');

  pageChange = output<number>();
  pageSizeChange = output<number>();

  protected fr = computed(() => this.language() === 'fr');
  protected pageCount = computed(() => Math.max(1, Math.ceil(this.total() / Math.max(1, this.pageSize()))));
  protected firstItem = computed(() => this.total() ? (clampPage(this.page(), this.total(), this.pageSize()) - 1) * this.pageSize() + 1 : 0);
  protected lastItem = computed(() => Math.min(this.total(), clampPage(this.page(), this.total(), this.pageSize()) * this.pageSize()));
  protected pageItems = computed<Array<number | null>>(() => compactPages(clampPage(this.page(), this.total(), this.pageSize()), this.pageCount()));

  protected go(page: number): void {
    const next = clampPage(page, this.total(), this.pageSize());
    if (next !== this.page()) this.pageChange.emit(next);
  }

  protected changeSize(raw: string): void {
    const size = Math.max(1, Number(raw) || 25);
    if (size !== this.pageSize()) this.pageSizeChange.emit(size);
  }
}

export function clampPage(page: number, total: number, pageSize: number): number {
  const count = Math.max(1, Math.ceil(Math.max(0, total) / Math.max(1, pageSize)));
  return Math.min(count, Math.max(1, Math.trunc(page) || 1));
}

export function paginateRows<T>(rows: readonly T[], page: number, pageSize: number): T[] {
  const safeSize = Math.max(1, Math.trunc(pageSize) || 25);
  const safePage = clampPage(page, rows.length, safeSize);
  const start = (safePage - 1) * safeSize;
  return rows.slice(start, start + safeSize);
}

function compactPages(current: number, count: number): Array<number | null> {
  if (count <= 7) return Array.from({ length: count }, (_, index) => index + 1);
  const pages = new Set([1, count, current - 1, current, current + 1]);
  const ordered = [...pages].filter((page) => page >= 1 && page <= count).sort((a, b) => a - b);
  const result: Array<number | null> = [];
  for (const page of ordered) {
    if (result.length && page - (result[result.length - 1] as number) > 1) result.push(null);
    result.push(page);
  }
  return result;
}
