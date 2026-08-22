import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface CohortClassOption {
  id: string; name: string; level: string; subsystem: string; sectionLabel: string | null;
}

export interface CohortProgrammeView {
  id: string; classId: string; className: string; subsystem: string; level: string;
  reportCardEnabled: boolean; active: boolean;
}

export interface CohortView {
  id: string; academicSessionId: string; sessionLabel: string; code: string; displayName: string;
  level: string; mode: string; attendanceMode: string; status: string; studentCount: number;
  programmes: CohortProgrammeView[]; version: number;
}

export interface CohortUpsert {
  academicSessionId: string; code: string; displayName: string; level: string; mode: string;
  francophoneClassId: string; anglophoneClassId: string | null; attendanceMode: string;
}

export interface PathwayTargetView {
  cohortId: string; displayName: string; level: string; mode: string;
  programmeLabel: string; subsystem: string | null;
}

export interface PathwayStudentView {
  studentId: string; matricule: string; studentName: string; currentCohortId: string | null;
  currentCohortName: string; selectedTargetCohortId: string | null; selectedTargetCohortName: string;
  status: string; version: number;
}

export interface PathwayPreview {
  sourceSessionId: string; sourceSessionLabel: string; targetSessionId: string; targetSessionLabel: string;
  sourceCohortId: string; sourceCohortName: string; targets: PathwayTargetView[]; students: PathwayStudentView[];
}

export interface PathwayChoice { studentId: string; targetCohortId: string; reason?: string | null; }
export interface PathwayApply {
  sourceSessionId: string; targetSessionId: string; sourceCohortId: string;
  choices: PathwayChoice[]; confirm: boolean;
}
export interface PathwayApplyResult {
  saved: number; confirmed: number; plannedEnrollments: number; warnings: string[]; appliedAt: string;
}

@Injectable({ providedIn: 'root' })
export class CohortApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/setup/cohorts`;

  classOptions(sessionId: string): Observable<CohortClassOption[]> {
    return this.http.get<CohortClassOption[]>(`${this.base}/class-options`, { params: new HttpParams().set('sessionId', sessionId) });
  }
  list(sessionId: string): Observable<CohortView[]> {
    return this.http.get<CohortView[]>(this.base, { params: new HttpParams().set('sessionId', sessionId) });
  }
  create(body: CohortUpsert): Observable<CohortView> { return this.http.post<CohortView>(this.base, body); }
  update(id: string, body: CohortUpsert): Observable<CohortView> { return this.http.put<CohortView>(`${this.base}/${id}`, body); }
  pathwayPreview(sourceSessionId: string, targetSessionId: string, sourceCohortId: string): Observable<PathwayPreview> {
    return this.http.get<PathwayPreview>(`${this.base}/pathway-preview`, { params: new HttpParams()
      .set('sourceSessionId', sourceSessionId).set('targetSessionId', targetSessionId).set('sourceCohortId', sourceCohortId) });
  }
  applyPathway(body: PathwayApply): Observable<PathwayApplyResult> {
    return this.http.post<PathwayApplyResult>(`${this.base}/pathway-choices`, body);
  }
}
