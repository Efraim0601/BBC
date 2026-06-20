import { Component, ChangeDetectionStrategy, input, computed } from '@angular/core';

/** Lightweight hand-rolled SVG charts (no external dependency) matching the prototype look. */

export interface Pt { label: string; value: number; }
export interface Segment { value: number; color: string; }
export interface BarGroup { label: string; segments: Segment[]; }
export interface Slice { name: string; value: number; color: string; }

const W = 600;

@Component({
  selector: 'bbc-area-chart',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg [attr.viewBox]="'0 0 ' + W + ' ' + h()" preserveAspectRatio="none" class="w-full block" [style.height.px]="h()">
      <defs>
        <linearGradient [attr.id]="gid()" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" [attr.stop-color]="color()" stop-opacity="0.45" />
          <stop offset="100%" [attr.stop-color]="color()" stop-opacity="0" />
        </linearGradient>
      </defs>
      @for (g of grid(); track g) { <line x1="0" [attr.x2]="W" [attr.y1]="g" [attr.y2]="g" stroke="#EEF2F7" stroke-width="1" /> }
      <path [attr.d]="area()" [attr.fill]="'url(#' + gid() + ')'" />
      <path [attr.d]="line()" fill="none" [attr.stroke]="stroke()" stroke-width="2.5" stroke-linejoin="round" stroke-linecap="round" />
    </svg>
    <div class="flex justify-between mt-1.5 text-[10px] text-mute">
      @for (l of labels(); track $index) { <span>{{ l }}</span> }
    </div>
  `,
})
export class AreaChartComponent {
  data = input<Pt[]>([]);
  color = input('#D4A843');
  stroke = input('#B98B25');
  h = input(220);
  protected W = W;
  protected gid = computed(() => 'ag' + Math.abs(this.color().split('').reduce((a, c) => a + c.charCodeAt(0), 0)));
  private pts = computed(() => {
    const d = this.data();
    if (!d.length) return [] as { x: number; y: number }[];
    const max = Math.max(...d.map((p) => p.value), 1);
    const min = Math.min(...d.map((p) => p.value), 0);
    const range = max - min || 1;
    const step = W / (d.length - 1 || 1);
    const pad = 8;
    return d.map((p, i) => ({ x: i * step, y: pad + (1 - (p.value - min) / range) * (this.h() - pad * 2) }));
  });
  protected line = computed(() => this.pts().map((p, i) => `${i ? 'L' : 'M'}${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join(' '));
  protected area = computed(() => {
    const p = this.pts();
    if (!p.length) return '';
    return `M0 ${this.h()} ` + p.map((q) => `L${q.x.toFixed(1)} ${q.y.toFixed(1)}`).join(' ') + ` L${W} ${this.h()} Z`;
  });
  protected grid = computed(() => [0.25, 0.5, 0.75].map((f) => +(f * this.h()).toFixed(1)));
  protected labels = computed(() => {
    const d = this.data();
    if (d.length <= 6) return d.map((p) => p.label);
    const out: string[] = [];
    const stepN = Math.ceil(d.length / 6);
    for (let i = 0; i < d.length; i += stepN) out.push(d[i].label);
    return out;
  });
}

@Component({
  selector: 'bbc-bar-chart',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex items-end gap-3" [style.height.px]="h()">
      @for (g of data(); track $index) {
        <div class="flex-1 flex flex-col items-center justify-end h-full gap-1">
          <div class="w-full flex flex-col justify-end rounded-t-md overflow-hidden" style="max-width:46px;margin:0 auto" [style.height.%]="100">
            @for (s of g.segments; track $index) {
              <div [style.height.%]="pct(s.value)" [style.background]="s.color" class="w-full"></div>
            }
          </div>
        </div>
      }
    </div>
    <div class="flex gap-3 mt-2">
      @for (g of data(); track $index) { <div class="flex-1 text-center text-[11px] text-mute">{{ g.label }}</div> }
    </div>
  `,
})
export class BarChartComponent {
  data = input<BarGroup[]>([]);
  h = input(200);
  private max = computed(() => Math.max(...this.data().map((g) => g.segments.reduce((a, s) => a + s.value, 0)), 1));
  protected pct = (v: number) => (v / this.max()) * 100;
}

@Component({
  selector: 'bbc-donut',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="relative" [style.height.px]="size()">
      <svg [attr.viewBox]="'0 0 ' + size() + ' ' + size()" class="w-full h-full -rotate-90">
        @for (a of arcs(); track $index) {
          <circle [attr.cx]="size() / 2" [attr.cy]="size() / 2" [attr.r]="r"
            fill="none" [attr.stroke]="a.color" [attr.stroke-width]="stroke()"
            [attr.stroke-dasharray]="a.dash" [attr.stroke-dashoffset]="a.offset" />
        }
      </svg>
      <div class="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
        <div class="text-[26px] font-bold text-ink">{{ total() }}</div>
        <div class="text-[10px] uppercase tracking-wide text-mute">{{ centerLabel() }}</div>
      </div>
    </div>
    <div class="grid grid-cols-2 gap-1.5 mt-3 text-xs">
      @for (d of data(); track $index) {
        <div class="flex items-center gap-2">
          <span class="w-2.5 h-2.5 rounded-sm shrink-0" [style.background]="d.color"></span>
          <span class="text-mute flex-1 truncate">{{ d.name }}</span>
          <span class="font-semibold text-ink">{{ d.value }}</span>
        </div>
      }
    </div>
  `,
})
export class DonutComponent {
  data = input<Slice[]>([]);
  total = input(0);
  centerLabel = input('élèves');
  size = input(180);
  protected stroke = () => 22;
  protected r = 0;
  protected arcs = computed(() => {
    const sz = this.size();
    this.r = sz / 2 - this.stroke() / 2;
    const C = 2 * Math.PI * this.r;
    const sum = this.data().reduce((a, d) => a + d.value, 0) || 1;
    let acc = 0;
    return this.data().map((d) => {
      const frac = d.value / sum;
      const dash = `${(frac * C).toFixed(2)} ${C.toFixed(2)}`;
      const offset = (-acc * C).toFixed(2);
      acc += frac;
      return { color: d.color, dash, offset };
    });
  });
}
