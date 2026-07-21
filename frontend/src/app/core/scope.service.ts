import { Injectable, computed, signal } from '@angular/core';
import { Parcours } from './models';

const SCOPE_KEY = 'bbc-parcours';

/**
 * Global parcours scope (Maternelle/Primaire/Secondaire × Francophone/Anglophone).
 * Chosen on the post-login picker, persisted in localStorage, and sent on every HTTP
 * request as the `X-Parcours` header so the whole app is compartmentalised by parcours.
 */
@Injectable({ providedIn: 'root' })
export class ScopeService {
  readonly scope = signal<Parcours | null>(this.restore());

  /** Header value `level:subsystem`, or null when no scope is active. */
  readonly header = computed(() => {
    const s = this.scope();
    return s ? `${s.level}:${s.subsystem}` : null;
  });

  private restore(): Parcours | null {
    const raw = localStorage.getItem(SCOPE_KEY);
    return raw ? (JSON.parse(raw) as Parcours) : null;
  }

  set(scope: Parcours): void {
    localStorage.setItem(SCOPE_KEY, JSON.stringify(scope));
    this.scope.set(scope);
  }

  clear(): void {
    localStorage.removeItem(SCOPE_KEY);
    this.scope.set(null);
  }
}
