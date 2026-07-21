import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';

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
  private base = `${environment.apiUrl}/settings/school`;

  readonly profile = signal<SchoolProfile | null>(null);
  private inFlight = false;

  /** Fetch once. Safe to call from every component's constructor. */
  ensureLoaded(): void {
    if (this.profile() || this.inFlight) return;
    this.inFlight = true;
    this.http.get<SchoolProfile>(this.base).subscribe({
      next: (p) => { this.profile.set(p); this.inFlight = false; },
      error: () => { this.inFlight = false; },
    });
  }

  reload(): Observable<SchoolProfile> {
    return this.http.get<SchoolProfile>(this.base).pipe(tap((p) => this.profile.set(p)));
  }

  update(body: Omit<SchoolProfile, 'code' | 'academicYear'>): Observable<SchoolProfile> {
    return this.http.put<SchoolProfile>(this.base, body).pipe(tap((p) => this.profile.set(p)));
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
