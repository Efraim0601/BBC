import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';

export interface SchoolProfile {
  code: string;
  name: string;
  motto: string | null;
  city: string | null;
  country: string | null;
  address: string | null;
  phone: string | null;
  email: string | null;
  website: string | null;
  currency: string;
  /** Supervising authority printed on bulletins, e.g. "République du Cameroun · MINESEC". */
  authority: string | null;
  academicYear: string | null;
}

/**
 * The school's identity, shared by every screen that prints it — bulletin header,
 * payment receipt, parent portal contacts, Settings → Général.
 *
 * Loaded once per session and cached in a signal: these values change about once a
 * year, and four screens re-fetching them on every visit would be pure noise.
 */
@Injectable({ providedIn: 'root' })
export class SchoolService {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  private base = `${environment.apiUrl}/settings/school`;

  readonly profile = signal<SchoolProfile | null>(null);
  private inFlight = false;
  private loadedSession = -1;

  /** Fetch once. Safe to call from every component's constructor. */
  ensureLoaded(): void {
    const session = this.auth.sessionVersion;
    if (session !== this.loadedSession) {
      this.profile.set(null);
      this.inFlight = false;
      this.loadedSession = session;
    }
    if (this.profile() || this.inFlight) return;
    this.inFlight = true;
    this.http.get<SchoolProfile>(`${this.base}/branding`).subscribe({
      next: (p) => { if (session === this.auth.sessionVersion) { this.profile.set(p); this.inFlight = false; } },
      error: () => { if (session === this.auth.sessionVersion) this.inFlight = false; },
    });
  }

  reload(): Observable<SchoolProfile> {
    const session = this.auth.sessionVersion;
    return this.http.get<SchoolProfile>(`${this.base}/branding`).pipe(tap((p) => {
      if (session === this.auth.sessionVersion) { this.loadedSession = session; this.profile.set(p); }
    }));
  }

  update(body: Omit<SchoolProfile, 'code' | 'academicYear'>): Observable<SchoolProfile> {
    const session = this.auth.sessionVersion;
    return this.http.put<SchoolProfile>(this.base, body).pipe(tap((p) => {
      if (session === this.auth.sessionVersion) { this.loadedSession = session; this.profile.set(p); }
    }));
  }

  /** Money label — falls back to FCFA until the profile lands. */
  currency(): string {
    return this.profile()?.currency ?? 'FCFA';
  }

  /** "Maroua, Cameroun" — omits whichever half is missing. */
  location(): string {
    const p = this.profile();
    return [p?.city, p?.country].filter(Boolean).join(', ');
  }
}
