import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

/** One sub-competence of a primary grid; a null evaluation type is not assessed. */
export interface PrimaryGridItem {
  competenceCode: string;
  competenceLabel: Record<string, string>;
  code: string;
  label: Record<string, string>;
  points: number;
  oral: number | null;
  written: number | null;
  practical: number | null;
  attitude: number | null;
  alternativeGroup: string | null;
  countsInTotal: boolean;
}

export interface PrimaryGridView {
  id: string; subsystem: 'FR' | 'EN'; level: number; version: number; totalPoints: number; items: PrimaryGridItem[];
}

export interface PrimaryClassStatus {
  classId: string; className: string; suggestedLevel: number | null; appliedLevel: number | null;
  appliedVersion: number | null; latestVersion: number | null;
}

export interface PrimaryApplyResult {
  classId: string; className: string; subsystem: string; level: number; gridVersion: number; subjects: number;
  assessmentsCreated: number; assessmentsUpdated: number; assessmentsKept: number; retiredSubjects: string[];
}

@Injectable({ providedIn: 'root' })
export class PrimaryCompetenceApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/academic/primary-competence`;

  grids(): Observable<PrimaryGridView[]> { return this.http.get<PrimaryGridView[]>(`${this.base}/grids`); }
  save(subsystem: string, level: number, items: PrimaryGridItem[]): Observable<PrimaryGridView> {
    return this.http.put<PrimaryGridView>(`${this.base}/grids/${subsystem}/${level}`, { items });
  }
  classes(sessionId: string): Observable<PrimaryClassStatus[]> {
    return this.http.get<PrimaryClassStatus[]>(`${this.base}/classes`, { params: new HttpParams().set('academicSessionId', sessionId) });
  }
  apply(classId: string, sessionId: string, level: number): Observable<PrimaryApplyResult> {
    return this.http.post<PrimaryApplyResult>(`${this.base}/classes/${classId}/apply`, null, {
      params: new HttpParams().set('academicSessionId', sessionId).set('level', level),
    });
  }
}
