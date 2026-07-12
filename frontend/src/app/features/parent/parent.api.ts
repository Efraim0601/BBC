import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ChildView {
  studentId: string;
  name: string;
  className: string;
  balance: number;
  feeStatus: string;
  attendanceRate: number;
}

export interface GradeView {
  subjectCode: string;
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

export type ResourceKind = 'supplies' | 'books';
export interface ResourceItem {
  id: string;
  label: string;
  quantity: number | null;
  price: number | null;
  note: string | null;
  subjectCode: string | null;
  author: string | null;
  mandatory: boolean | null;
}
export interface ClassResourceView {
  classId: string | null;
  className: string;
  kind: ResourceKind;
  published: boolean;
  publishedAt: string | null;
  items: ResourceItem[];
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
  resources(studentId: string, kind: ResourceKind): Observable<ClassResourceView> {
    return this.http.get<ClassResourceView>(`${this.base}/children/${studentId}/resources/${kind}`);
  }
  addSuggestion(body: SuggestionRequest): Observable<SuggestionView> {
    return this.http.post<SuggestionView>(`${this.base}/suggestions`, body);
  }
  mySuggestions(): Observable<SuggestionView[]> {
    return this.http.get<SuggestionView[]>(`${this.base}/suggestions`);
  }
}
