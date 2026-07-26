import { Injectable, computed, signal } from '@angular/core';
import { Parcours } from './models';

const SCOPE_KEY = 'bbc-parcours';
const ALL_KEY = 'bbc-parcours-all';

/**
 * Global parcours scope (Maternelle/Primaire/Secondaire × Francophone/Anglophone).
 * Chosen on the post-login picker, persisted in localStorage, and sent on every HTTP
 * request as the `X-Parcours` header so the whole app is compartmentalised by parcours.
 *
 * Admins may also pick "all parcours" ({@link #setAll}) — no header is sent, lists show
 * every level/subsystem, and the route guard still treats the choice as resolved.
 */
@Injectable({ providedIn: 'root' })
export class ScopeService {
  readonly scope = signal<Parcours | null>(this.restore());
  /** True when the user explicitly chose to see every parcours (admins). */
  readonly allMode = signal<boolean>(localStorage.getItem(ALL_KEY) === '1');

  /** Header value `level:subsystem`, or null when no single parcours is active. */
  readonly header = computed(() => {
    const s = this.scope();
    return s ? `${s.level}:${s.subsystem}` : null;
  });

  /** Whether a post-login parcours decision has been made (scoped or all). */
  readonly resolved = computed(() => this.scope() !== null || this.allMode());

  private restore(): Parcours | null {
    if (localStorage.getItem(ALL_KEY) === '1') return null;
    const raw = localStorage.getItem(SCOPE_KEY);
    return raw ? (JSON.parse(raw) as Parcours) : null;
  }

  set(scope: Parcours): void {
    localStorage.removeItem(ALL_KEY);
    localStorage.setItem(SCOPE_KEY, JSON.stringify(scope));
    this.allMode.set(false);
    this.scope.set(scope);
  }

  /** Browse without a parcours filter (admins / unrestricted users). */
  setAll(): void {
    localStorage.removeItem(SCOPE_KEY);
    localStorage.setItem(ALL_KEY, '1');
    this.allMode.set(true);
    this.scope.set(null);
  }

  clear(): void {
    localStorage.removeItem(SCOPE_KEY);
    localStorage.removeItem(ALL_KEY);
    this.allMode.set(false);
    this.scope.set(null);
  }
}
