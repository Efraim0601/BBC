import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { AlertsApi, AlertView } from './alerts.api';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import {
  IconComponent, CardComponent, PageHeaderComponent, EmptyComponent, AvatarComponent, KpiComponent,
} from '../../core/ui';

interface TypeMeta { fr: string; en: string; icon: string; }

@Component({
  selector: 'bbc-alerts',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    IconComponent, CardComponent, PageHeaderComponent, EmptyComponent, AvatarComponent, KpiComponent,
  ],
  template: `
    <div class="fade-in max-w-6xl mx-auto">
      <bbc-page-header
        [title]="fr() ? 'Alertes proactives' : 'Proactive alerts'"
        [subtitle]="fr() ? 'Détection précoce des élèves à risque' : 'Early detection of at-risk students'">
        <div right class="flex items-center gap-2">
          @if (canWrite) {
            <button (click)="rescan()" [disabled]="scanning()"
              class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700 disabled:opacity-60">
              <bbc-icon name="spark" [s]="16" /> {{ fr() ? 'Relancer le scan' : 'Rescan' }}
            </button>
          }
        </div>
      </bbc-page-header>

      <!-- KPI row -->
      <div class="grid grid-cols-3 gap-3 mb-4">
        <bbc-kpi [label]="fr() ? 'Critiques' : 'Critical'" [value]="countBy('critical')" icon="shield" tone="bad" />
        <bbc-kpi [label]="fr() ? 'À surveiller' : 'Warnings'" [value]="countBy('warn')" icon="bell" tone="warn" />
        <bbc-kpi [label]="fr() ? 'Informations' : 'Info'" [value]="countBy('info')" icon="spark" tone="neutral" />
      </div>

      <!-- Type filter -->
      <div class="flex flex-wrap items-center gap-1.5 mb-3">
        <button (click)="typeFilter.set(null)"
          class="text-xs font-semibold px-3 h-8 rounded-full transition"
          [class]="typeFilter() === null ? 'bg-brand-600 text-white' : 'bg-white text-mute border border-slate-200 hover:border-brand-300'">
          {{ fr() ? 'Toutes' : 'All' }} ({{ alerts().length }})
        </button>
        @for (t of typeKeys; track t) {
          <button (click)="typeFilter.set(t)"
            class="inline-flex items-center gap-1.5 text-xs font-semibold px-3 h-8 rounded-full transition"
            [class]="typeFilter() === t ? 'bg-brand-600 text-white' : 'bg-white text-mute border border-slate-200 hover:border-brand-300'">
            <bbc-icon [name]="TYPE[t].icon" [s]="13" />
            {{ fr() ? TYPE[t].fr : TYPE[t].en }} ({{ countByType(t) }})
          </button>
        }
      </div>

      <!-- List -->
      <bbc-card
        [title]="fr() ? 'Alertes ouvertes' : 'Open alerts'"
        [subtitle]="filtered().length + (fr() ? ' alertes' : ' alerts')">
        @if (filtered().length === 0) {
          <bbc-empty icon="check" [label]="fr() ? 'Aucune alerte — tout va bien' : 'No alerts — all clear'" />
        } @else {
          <div class="space-y-2">
            @for (a of filtered(); track a.id) {
              <div class="flex items-start gap-3 p-3 rounded-lg border border-slate-100 hover:bg-slate-50/50 group">
                <bbc-avatar [name]="a.studentName || '?'" [hue]="hueFor(a.severity)" />
                <div class="flex-1 min-w-0">
                  <div class="flex items-center gap-2 flex-wrap">
                    <span class="font-semibold text-ink">{{ a.studentName }}</span>
                    @if (a.className) { <span class="text-[11px] text-mute">· {{ a.className }}</span> }
                    <span class="text-[10px] font-bold uppercase px-1.5 py-0.5 rounded" [class]="severityBadge(a.severity)">
                      {{ fr() ? severityFr(a.severity) : a.severity }}
                    </span>
                    @if (a.status === 'ack') {
                      <span class="text-[10px] font-bold uppercase px-1.5 py-0.5 rounded bg-slate-100 text-slate-600">
                        {{ fr() ? 'Pris en compte' : 'Acknowledged' }}
                      </span>
                    }
                  </div>
                  <div class="text-sm font-semibold text-ink mt-0.5 flex items-center gap-1.5">
                    <bbc-icon [name]="TYPE[a.type]?.icon ?? 'bell'" [s]="14" /> {{ a.title }}
                  </div>
                  @if (a.detail) { <div class="text-[13px] text-mute mt-0.5">{{ a.detail }}</div> }
                </div>
                @if (canWrite) {
                  <div class="flex items-center gap-1 self-center">
                    @if (a.status !== 'ack') {
                      <button (click)="ack(a)"
                        class="inline-flex items-center gap-1 h-8 px-2.5 text-xs font-semibold rounded-lg bg-white border border-slate-200 text-mute hover:text-brand-600 hover:border-brand-300"
                        [title]="fr() ? 'Prendre en compte' : 'Acknowledge'">
                        <bbc-icon name="eye" [s]="14" /> {{ fr() ? 'Vu' : 'Ack' }}
                      </button>
                    }
                    <button (click)="resolve(a)"
                      class="inline-flex items-center gap-1 h-8 px-2.5 text-xs font-semibold rounded-lg bg-emerald-600 text-white hover:bg-emerald-700"
                      [title]="fr() ? 'Résoudre' : 'Resolve'">
                      <bbc-icon name="check" [s]="14" /> {{ fr() ? 'Résoudre' : 'Resolve' }}
                    </button>
                  </div>
                }
              </div>
            }
          </div>
        }
      </bbc-card>
    </div>
  `,
})
export class AlertsComponent {
  protected i18n = inject(I18nService);
  private api = inject(AlertsApi);
  private auth = inject(AuthService);

  protected readonly typeKeys = ['grade_drop', 'absences', 'discipline', 'unpaid'];
  protected readonly TYPE: Record<string, TypeMeta> = {
    grade_drop: { fr: 'Résultats', en: 'Grades',     icon: 'chart' },
    absences:   { fr: 'Absences',  en: 'Absences',   icon: 'calendar' },
    discipline: { fr: 'Discipline', en: 'Discipline', icon: 'shield' },
    unpaid:     { fr: 'Impayés',   en: 'Unpaid',     icon: 'wallet' },
  };

  protected alerts = signal<AlertView[]>([]);
  protected typeFilter = signal<string | null>(null);
  protected scanning = signal(false);

  protected canWrite = this.auth.can('alerts', 'write');
  protected fr = () => this.i18n.lang() === 'fr';

  protected filtered = computed(() => {
    const t = this.typeFilter();
    const list = this.alerts();
    return t ? list.filter((a) => a.type === t) : list;
  });

  constructor() {
    this.reload();
  }

  private reload(): void {
    this.api.list().subscribe((r) => this.alerts.set(r));
  }

  protected rescan(): void {
    this.scanning.set(true);
    this.api.scan().subscribe({
      next: () => { this.scanning.set(false); this.reload(); },
      error: () => this.scanning.set(false),
    });
  }

  protected ack(a: AlertView): void {
    this.api.ack(a.id).subscribe(() => this.reload());
  }

  protected resolve(a: AlertView): void {
    this.api.resolve(a.id).subscribe(() => this.reload());
  }

  protected countBy(severity: string): string {
    return this.alerts().filter((a) => a.severity === severity).length.toString();
  }

  protected countByType(type: string): number {
    return this.alerts().filter((a) => a.type === type).length;
  }

  protected hueFor(severity: string): number {
    const map: Record<string, number> = { critical: 350, warn: 35, info: 205 };
    return map[severity] ?? 210;
  }

  protected severityBadge(severity: string): string {
    const map: Record<string, string> = {
      critical: 'bg-rose-100 text-rose-700',
      warn: 'bg-amber-100 text-amber-700',
      info: 'bg-sky-100 text-sky-700',
    };
    return map[severity] ?? 'bg-slate-100 text-slate-700';
  }

  protected severityFr(severity: string): string {
    const map: Record<string, string> = {
      critical: 'Critique', warn: 'Alerte', info: 'Info',
    };
    return map[severity] ?? severity;
  }
}
