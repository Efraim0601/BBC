import { Injectable, computed, inject, signal } from '@angular/core';
import { FoundationApi, AcademicSessionView, AcademicTermView } from './foundation.api';

@Injectable({ providedIn: 'root' })
export class AcademicContextService {
  private api = inject(FoundationApi);
  readonly sessions = signal<AcademicSessionView[]>([]);
  readonly sessionId = signal<string | null>(null);
  readonly termId = signal<string | null>(null);
  readonly loading = signal(false);
  readonly session = computed(() => this.sessions().find((s) => s.id === this.sessionId()) ?? null);
  readonly terms = computed<AcademicTermView[]>(() => this.session()?.terms ?? []);
  readonly term = computed(() => this.terms().find((t) => t.id === this.termId()) ?? null);
  readonly historical = computed(() => !!this.session() && !this.session()!.current);

  load(force = false): void {
    if (!force && (this.loading() || this.sessions().length)) return;
    this.loading.set(true);
    this.api.listSessions().subscribe({
      next: (rows) => {
        this.sessions.set(rows);
        const selected = rows.find((s) => s.id === this.sessionId()) ?? rows.find((s) => s.current) ?? rows[0] ?? null;
        this.sessionId.set(selected?.id ?? null);
        if (!selected?.terms.some((t) => t.id === this.termId())) this.termId.set(selected?.terms[0]?.id ?? null);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  selectSession(id: string): void {
    this.sessionId.set(id);
    this.termId.set(this.sessions().find((s) => s.id === id)?.terms[0]?.id ?? null);
  }
}
