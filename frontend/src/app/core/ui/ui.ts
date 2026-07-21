import { Component, ChangeDetectionStrategy, input, output, computed } from '@angular/core';
import { IconComponent } from './icon';

/** Shared UI primitives — ported from the BBC SMS prototype's component kit. */

@Component({
  selector: 'bbc-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class]': 'hostClass()' },
  template: `
    <div class="bg-white rounded-xl2 shadow-card border border-slate-100 h-full">
      @if (title() || subtitle()) {
        <div class="flex items-start justify-between px-5 pt-5 pb-3">
          <div>
            @if (title()) { <div class="text-[15px] font-semibold text-ink">{{ title() }}</div> }
            @if (subtitle()) { <div class="text-xs text-mute mt-0.5">{{ subtitle() }}</div> }
          </div>
          <div><ng-content select="[action]"></ng-content></div>
        </div>
      }
      <div [class]="title() || subtitle() ? 'px-5 pb-5' : 'p-5'">
        <ng-content></ng-content>
      </div>
    </div>
  `,
})
export class CardComponent {
  title = input<string>();
  subtitle = input<string>();
  className = input('');
  protected hostClass = computed(() => 'block ' + this.className());
}

@Component({
  selector: 'bbc-kpi',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="bg-white rounded-xl2 shadow-card border border-slate-100 p-5 flex items-start gap-4">
      <div class="w-11 h-11 rounded-xl flex items-center justify-center shrink-0" [class]="tones[tone()]">
        <bbc-icon [name]="icon()" [s]="22" />
      </div>
      <div class="min-w-0 flex-1">
        <div class="text-[12px] text-mute uppercase tracking-wide font-medium">{{ label() }}</div>
        <div class="text-[26px] font-bold text-ink leading-tight mt-1 truncate">{{ value() }}</div>
        @if (sub() || delta() !== undefined) {
          <div class="flex items-center gap-2 mt-1 text-xs">
            @if (delta() !== undefined) {
              <span class="inline-flex items-center gap-0.5 font-semibold" [class]="delta()! >= 0 ? 'text-emerald-600' : 'text-rose-600'">
                <bbc-icon name="trendUp" [s]="12" [sw]="2.4" />{{ delta()! >= 0 ? '+' : '' }}{{ delta() }}%
              </span>
            }
            @if (sub()) { <span class="text-mute">{{ sub() }}</span> }
          </div>
        }
      </div>
    </div>
  `,
})
export class KpiComponent {
  icon = input('chart');
  label = input('');
  value = input<string | number>('');
  sub = input<string>();
  delta = input<number>();
  tone = input<'neutral' | 'gold' | 'ok' | 'warn' | 'bad'>('neutral');
  protected tones: Record<string, string> = {
    neutral: 'bg-brand-50 text-brand-600',
    gold: 'bg-gold-50 text-gold-500',
    ok: 'bg-emerald-50 text-emerald-600',
    warn: 'bg-amber-50 text-amber-600',
    bad: 'bg-rose-50 text-rose-600',
  };
}

@Component({
  selector: 'bbc-status-pill',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="inline-flex items-center gap-1.5 text-[11px] font-semibold px-2 py-1 rounded-full" [class]="meta().c">
      <span class="dot" style="background:currentColor"></span>{{ meta().l }}
    </span>
  `,
})
export class StatusPillComponent {
  status = input.required<string>();
  label = input<string>();
  protected meta = () => {
    const map: Record<string, { c: string; l: string }> = {
      present: { c: 'bg-emerald-100 text-emerald-700', l: 'Présent' },
      paid: { c: 'bg-emerald-100 text-emerald-700', l: 'Payé' },
      ok: { c: 'bg-emerald-100 text-emerald-700', l: 'OK' },
      late: { c: 'bg-amber-100 text-amber-700', l: 'Retard' },
      partial: { c: 'bg-amber-100 text-amber-700', l: 'Partiel' },
      absent: { c: 'bg-rose-100 text-rose-700', l: 'Absent' },
      unpaid: { c: 'bg-rose-100 text-rose-700', l: 'Impayé' },
    };
    const m = map[this.status()] ?? { c: 'bg-slate-100 text-slate-700', l: this.status() };
    return { c: m.c, l: this.label() ?? m.l };
  };
}

@Component({
  selector: 'bbc-page-header',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex items-end justify-between mb-5 gap-4 flex-wrap">
      <div>
        <h1 class="text-[22px] font-bold text-ink leading-tight font-display">{{ title() }}</h1>
        @if (subtitle()) { <div class="text-sm text-mute mt-1">{{ subtitle() }}</div> }
      </div>
      <div class="flex items-center gap-2"><ng-content select="[right]"></ng-content></div>
    </div>
  `,
})
export class PageHeaderComponent {
  title = input('');
  subtitle = input<string>();
}

@Component({
  selector: 'bbc-tabs',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex items-center gap-1 border-b border-slate-200 mb-5 overflow-x-auto">
      @for (tb of tabs(); track tb.id) {
        <button (click)="change.emit(tb.id)"
          class="relative px-4 py-2.5 text-sm font-semibold transition whitespace-nowrap"
          [class]="value() === tb.id ? 'text-brand-600' : 'text-mute hover:text-ink'">
          {{ tb.label }}
          @if (value() === tb.id) { <span class="absolute left-2 right-2 -bottom-px h-0.5 bg-gold-400 rounded-full"></span> }
        </button>
      }
    </div>
  `,
})
export class TabsComponent {
  tabs = input<{ id: string; label: string }[]>([]);
  value = input<string>();
  change = output<string>();
}

@Component({
  selector: 'bbc-chip-filter',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex items-center gap-1.5 flex-wrap">
      <button (click)="change.emit(null)"
        class="px-3 py-1.5 rounded-full text-xs font-semibold transition"
        [class]="value() === null ? 'bg-brand-600 text-white' : 'bg-white text-mute border border-slate-200 hover:border-brand-300'">
        {{ allLabel() }}
      </button>
      @for (o of options(); track o.value) {
        <button (click)="change.emit(o.value)"
          class="px-3 py-1.5 rounded-full text-xs font-semibold transition"
          [class]="value() === o.value ? 'bg-brand-600 text-white' : 'bg-white text-mute border border-slate-200 hover:border-brand-300'">
          {{ o.label }}
        </button>
      }
    </div>
  `,
})
export class ChipFilterComponent {
  options = input<{ value: string; label: string }[]>([]);
  value = input<string | null>(null);
  allLabel = input('Tout');
  change = output<string | null>();
}

@Component({
  selector: 'bbc-empty',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="flex flex-col items-center justify-center py-10 text-center">
      <div class="w-12 h-12 rounded-full bg-slate-100 text-slate-400 flex items-center justify-center mb-2">
        <bbc-icon [name]="icon()" />
      </div>
      <div class="text-sm text-mute">{{ label() }}</div>
    </div>
  `,
})
export class EmptyComponent {
  icon = input('search');
  label = input('');
}

@Component({
  selector: 'bbc-avatar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="rounded-full font-semibold flex items-center justify-center shrink-0 text-white"
      [style.width.px]="size()" [style.height.px]="size()" [style.font-size.px]="size() * 0.36"
      [style.background]="bg()">{{ initials() }}</div>
  `,
})
export class AvatarComponent {
  name = input('');
  hue = input(210);
  size = input(36);
  protected initials = () => this.name().split(' ').slice(0, 2).map((s) => s[0]).join('').toUpperCase();
  protected bg = () => `linear-gradient(135deg, hsl(${this.hue()} 50% 45%), hsl(${(this.hue() + 30) % 360} 55% 35%))`;
}

/**
 * Destructive-action confirmation.
 *
 * Deletes used to be inconsistent across modules — some showed a modal, some called
 * window.confirm(), most deleted on the first click with no warning at all. This is
 * the single affordance they all now use.
 */
@Component({
  selector: 'bbc-confirm',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 fade-in" (click)="cancel.emit()">
      <div class="bg-white rounded-xl2 shadow-pop w-full max-w-md p-6" (click)="$event.stopPropagation()">
        <div class="flex items-start gap-3">
          <div class="w-10 h-10 rounded-full flex items-center justify-center shrink-0"
            [class]="danger() ? 'bg-rose-100 text-rose-600' : 'bg-brand-50 text-brand-600'">
            <bbc-icon [name]="danger() ? 'trash' : 'alert'" [s]="18" />
          </div>
          <div class="flex-1">
            <div class="text-[15px] font-semibold text-ink">{{ title() }}</div>
            @if (body()) { <div class="text-sm text-mute mt-1">{{ body() }}</div> }
          </div>
        </div>
        <div class="flex items-center justify-end gap-2 mt-5">
          <button (click)="cancel.emit()"
            class="h-9 px-4 rounded-lg bg-slate-100 text-sm font-semibold text-ink hover:bg-slate-200">
            {{ cancelLabel() }}
          </button>
          <button (click)="confirm.emit()"
            class="h-9 px-4 rounded-lg text-white text-sm font-semibold"
            [class]="danger() ? 'bg-rose-600 hover:bg-rose-700' : 'bg-brand-600 hover:bg-brand-700'">
            {{ confirmLabel() }}
          </button>
        </div>
      </div>
    </div>
  `,
})
export class ConfirmComponent {
  title = input('');
  body = input('');
  confirmLabel = input('OK');
  cancelLabel = input('Annuler');
  danger = input(true);
  confirm = output<void>();
  cancel = output<void>();
}
