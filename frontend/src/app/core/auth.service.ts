import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { Level, TokenResponse, UserView } from './models';
import { ScopeService } from './scope.service';

const ACCESS_KEY = 'bbc-access';
const REFRESH_KEY = 'bbc-refresh';
const USER_KEY = 'bbc-user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private scope = inject(ScopeService);

  // Signals — the single source of truth the whole UI reacts to.
  readonly user = signal<UserView | null>(this.restoreUser());
  readonly isLoggedIn = computed(() => this.user() !== null);

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

  refresh(): Observable<TokenResponse> {
    const refreshToken = localStorage.getItem(REFRESH_KEY);
    return this.http
      .post<TokenResponse>(`${environment.apiUrl}/auth/refresh`, { refreshToken })
      .pipe(tap((res) => this.persist(res)));
  }

  logout(): void {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
    localStorage.removeItem(USER_KEY);
    this.scope.clear();
    this.user.set(null);
    this.router.navigate(['/login']);
  }

  /** RBAC check mirrored from the backend matrix — used to show/hide UI only. */
  can(module: string, level: Level = 'read'): boolean {
    const u = this.user();
    if (!u) return false;
    const rank = { none: 0, read: 1, write: 2 };
    return rank[u.permissions[module] ?? 'none'] >= rank[level];
  }

  private persist(res: TokenResponse): void {
    localStorage.setItem(ACCESS_KEY, res.accessToken);
    localStorage.setItem(REFRESH_KEY, res.refreshToken);
    localStorage.setItem(USER_KEY, JSON.stringify(res.user));
    this.user.set(res.user);
  }
}
