import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, finalize, shareReplay, throwError, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { ActionEffect, CapabilityView, Level, TokenResponse, UserView } from './models';
import { ScopeService } from './scope.service';

const ACCESS_KEY = 'bbc-access';
const REFRESH_KEY = 'bbc-refresh';
const USER_KEY = 'bbc-user';
const EXPIRES_KEY = 'bbc-access-expires-at';

/** Refresh this many ms before the access token actually expires. */
const REFRESH_SKEW_MS = 90_000;
/** Soft retry delay when a proactive refresh fails for a transient reason. */
const RETRY_MS = 30_000;

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private scope = inject(ScopeService);

  // Signals — the single source of truth the whole UI reacts to.
  readonly user = signal<UserView | null>(this.restoreUser());
  readonly isLoggedIn = computed(() => this.user() !== null);
  /** Server-authoritative action capabilities; null means they are not loaded. */
  readonly capabilities = signal<CapabilityView | null>(null);
  readonly capabilitiesLoading = signal(false);
  readonly capabilitiesError = signal(false);

  /** Shared in-flight refresh so parallel 401s don't race and wipe the session. */
  private refreshInFlight$: Observable<TokenResponse> | null = null;
  private capabilitiesInFlight$: Observable<CapabilityView> | null = null;
  /** Prevent a response started for a previous persona/session from winning a later login. */
  private capabilitiesGeneration = 0;
  private refreshTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    // After a page reload, keep the session alive if tokens are still present.
    queueMicrotask(() => {
      this.ensureSessionKeepAlive();
      if (this.user()) this.loadCapabilities().subscribe({ error: () => undefined });
    });
  }

  private restoreUser(): UserView | null {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as UserView) : null;
  }

  get accessToken(): string | null {
    return localStorage.getItem(ACCESS_KEY);
  }

  login(username: string, password: string, schoolCode?: string): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>(`${environment.apiUrl}/auth/login`, { username, password, schoolCode })
      .pipe(tap((res) => this.persist(res)));
  }

  /** Self-service reset — always resolves with a generic message (anti-enumeration). */
  forgotPassword(username: string, schoolCode?: string): Observable<{ ok: boolean; message: string }> {
    return this.http.post<{ ok: boolean; message: string }>(
      `${environment.apiUrl}/auth/forgot-password`,
      { username, schoolCode },
    );
  }

  /**
   * Silent token renewal. Concurrent callers share one HTTP round-trip
   * (single-flight) so a burst of 401s cannot log the user out spuriously.
   */
  refresh(): Observable<TokenResponse> {
    if (this.refreshInFlight$) return this.refreshInFlight$;

    const refreshToken = localStorage.getItem(REFRESH_KEY);
    if (!refreshToken) {
      return throwError(() => new HttpErrorResponse({ status: 401, statusText: 'No refresh token' }));
    }

    this.refreshInFlight$ = this.http
      .post<TokenResponse>(`${environment.apiUrl}/auth/refresh`, { refreshToken })
      .pipe(
        tap((res) => this.persist(res)),
        finalize(() => {
          this.refreshInFlight$ = null;
        }),
        shareReplay({ bufferSize: 1, refCount: true }),
      );

    return this.refreshInFlight$;
  }

  /** True when the refresh endpoint itself rejected the session (not a network blip). */
  isSessionInvalid(err: unknown): boolean {
    const status = err instanceof HttpErrorResponse ? err.status : (err as { status?: number })?.status;
    return status === 401 || status === 403;
  }

  /** @param reason optional cause (e.g. 'expired') surfaced on the login screen. */
  logout(reason?: string): void {
    this.clearRefreshTimer();
    this.capabilitiesGeneration += 1;
    this.capabilitiesInFlight$ = null;
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem(EXPIRES_KEY);
    this.scope.clear();
    this.user.set(null);
    this.capabilities.set(null);
    this.capabilitiesError.set(false);
    this.router.navigate(['/login'], reason ? { queryParams: { reason } } : undefined);
  }

  /** RBAC check mirrored from the backend matrix — used to show/hide UI only. */
  can(module: string, level: Level = 'read'): boolean {
    const u = this.user();
    if (!u) return false;
    const rank = { none: 0, read: 1, write: 2 };
    const permissionKey = module === 'finance-accounting' ? 'finance' : module;
    return rank[u.permissions[permissionKey] ?? 'none'] >= rank[level];
  }

  /**
   * Resource-scoped modules can be present in the server action policy even
   * when the legacy module matrix is intentionally empty.  CONTEXT_REQUIRED
   * is sufficient to open the module; the API still performs the resource
   * decision for every student/class/relationship request.
   */
  canModuleOrAction(module: string, actionCode: string, level: Level = 'read'): boolean {
    if (this.can(module, level)) return true;
    const state = this.actionState(actionCode);
    return state === 'ALLOW' || state === 'CONTEXT_REQUIRED';
  }

  /** Fetch the staged policy result. Failure stays closed until an explicit retry. */
  loadCapabilities(): Observable<CapabilityView> {
    if (this.capabilitiesInFlight$) return this.capabilitiesInFlight$;
    if (!this.user()) return throwError(() => new HttpErrorResponse({ status: 401, statusText: 'Not logged in' }));
    const generation = this.capabilitiesGeneration;
    this.capabilitiesLoading.set(true);
    this.capabilitiesError.set(false);
    this.capabilitiesInFlight$ = this.http.get<CapabilityView>(`${environment.apiUrl}/access/me/capabilities`).pipe(
      tap((value) => {
        if (generation === this.capabilitiesGeneration) this.capabilities.set(value);
      }),
      finalize(() => {
        if (generation === this.capabilitiesGeneration) {
          this.capabilitiesLoading.set(false);
          this.capabilitiesInFlight$ = null;
        }
      }),
      shareReplay({ bufferSize: 1, refCount: true }),
    );
    this.capabilitiesInFlight$.subscribe({
      error: () => {
        if (generation === this.capabilitiesGeneration) this.capabilitiesError.set(true);
      },
    });
    return this.capabilitiesInFlight$;
  }

  retryCapabilities(): void {
    this.capabilities.set(null);
    this.loadCapabilities().subscribe({ error: () => undefined });
  }

  /** Contextual actions intentionally remain visible as CONTEXT_REQUIRED. */
  actionState(actionCode: string): ActionEffect {
    if (!this.user()) return 'DENY';
    if (!this.capabilities()) return this.capabilitiesLoading() ? 'LOADING' : 'DENY';
    return (this.capabilities()!.actions.find((x) => x.actionCode === actionCode)?.effect ?? 'DENY') as ActionEffect;
  }

  /** Only an evaluated, context-free ALLOW may enable a global control. */
  canAction(actionCode: string): boolean {
    return this.actionState(actionCode) === 'ALLOW';
  }

  /**
   * Le compte décide-t-il pour l'établissement entier ?
   *
   * <p>Faux pour un administrateur de section, à qui les réglages école-entière
   * — matrice des rôles, profil, SMTP, calendrier, catalogues — sont fermés.
   * Comme {@link can}, cet indicateur ne sert qu'à ne pas montrer un écran voué
   * au refus : le serveur reste seul juge.
   */
  readonly schoolWide = computed(() => this.user()?.schoolWide !== false);

  /** Cycle administré (maternelle|primary|secondary), null si le compte n'est pas cloisonné. */
  readonly section = computed(() => this.user()?.section ?? null);

  private persist(res: TokenResponse): void {
    this.capabilitiesGeneration += 1;
    this.capabilitiesInFlight$ = null;
    localStorage.setItem(ACCESS_KEY, res.accessToken);
    localStorage.setItem(REFRESH_KEY, res.refreshToken);
    localStorage.setItem(USER_KEY, JSON.stringify(res.user));
    localStorage.setItem(EXPIRES_KEY, String(Date.now() + res.expiresInMs));
    this.user.set(res.user);
    this.capabilities.set(null);
    this.loadCapabilities().subscribe({ error: () => undefined });
    this.scheduleProactiveRefresh();
  }

  /** On boot: refresh immediately if access is expired/near expiry, else schedule. */
  private ensureSessionKeepAlive(): void {
    if (!this.user() || !localStorage.getItem(REFRESH_KEY)) return;
    const expiresAt = this.accessExpiresAt();
    if (!expiresAt || expiresAt - Date.now() <= REFRESH_SKEW_MS) {
      this.refresh().subscribe({
        error: (err) => {
          if (this.isSessionInvalid(err)) this.logout('expired');
          else this.scheduleRetry();
        },
      });
    } else {
      this.scheduleProactiveRefresh();
    }
  }

  private scheduleProactiveRefresh(): void {
    this.clearRefreshTimer();
    if (!localStorage.getItem(REFRESH_KEY)) return;
    const expiresAt = this.accessExpiresAt();
    if (!expiresAt) return;

    const delay = Math.max(5_000, expiresAt - Date.now() - REFRESH_SKEW_MS);
    this.refreshTimer = setTimeout(() => {
      this.refresh().subscribe({
        error: (err) => {
          if (this.isSessionInvalid(err)) this.logout('expired');
          else this.scheduleRetry();
        },
      });
    }, delay);
  }

  private scheduleRetry(): void {
    this.clearRefreshTimer();
    this.refreshTimer = setTimeout(() => this.ensureSessionKeepAlive(), RETRY_MS);
  }

  private clearRefreshTimer(): void {
    if (this.refreshTimer != null) {
      clearTimeout(this.refreshTimer);
      this.refreshTimer = null;
    }
  }

  /** Absolute expiry of the access token (ms since epoch), from storage or JWT `exp`. */
  private accessExpiresAt(): number | null {
    const stored = localStorage.getItem(EXPIRES_KEY);
    if (stored) {
      const n = Number(stored);
      if (Number.isFinite(n)) return n;
    }
    const token = this.accessToken;
    if (!token) return null;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]!)) as { exp?: number };
      return payload.exp ? payload.exp * 1000 : null;
    } catch {
      return null;
    }
  }
}
