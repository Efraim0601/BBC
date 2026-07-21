import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ChildView {
  studentId: string;
  matricule: string;
  name: string;
  className: string;
  balance: number;
  feeStatus: 'paid' | 'partial' | 'unpaid';
  attendanceRate: number;
}

export interface GradeView {
  subjectCode: string;
  subjectLabelFr: string;
  subjectLabelEn: string;
  /** Subject weight — the portal average must match the bulletin's, which is weighted. */
  coef: number;
  sequence: number;
  mark: number;
}

export interface SuggestionView {
  id: string;
  category: string;
  message: string;
  status: string;
  createdAt: string;
}

export interface SuggestionRequest {
  category: string;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class ParentApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/parent`;

  children(): Observable<ChildView[]> {
    return this.http.get<ChildView[]>(`${this.base}/children`);
  }
  grades(studentId: string): Observable<GradeView[]> {
    return this.http.get<GradeView[]>(`${this.base}/children/${studentId}/grades`);
  }
  addSuggestion(body: SuggestionRequest): Observable<SuggestionView> {
    return this.http.post<SuggestionView>(`${this.base}/suggestions`, body);
  }
  mySuggestions(): Observable<SuggestionView[]> {
    return this.http.get<SuggestionView[]>(`${this.base}/suggestions`);
  }
}
