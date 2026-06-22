import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface DocumentView {
  id: string;
  studentId: string;
  kind: string;
  title: string;
  note: string | null;
  fileRef: string | null;
  createdAt: string;
}

export interface DocumentUpsert {
  kind: string;
  title: string;
  note?: string;
  fileRef?: string;
}

export interface OrientationView {
  id: string;
  studentId: string;
  academicYear: string;
  stage: string;
  recommendation: string | null;
  decision: string | null;
  councilDate: string | null;
  createdAt: string;
}

export interface OrientationUpsert {
  academicYear: string;
  stage: string;
  recommendation?: string;
  decision?: string;
  councilDate?: string | null;
}

export interface StudentDossier {
  studentId: string;
  studentName: string;
  matricule: string;
  className: string;
  documents: DocumentView[];
  orientations: OrientationView[];
}

@Injectable({ providedIn: 'root' })
export class DocumentsApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/documents`;

  forStudent(studentId: string): Observable<StudentDossier> {
    return this.http.get<StudentDossier>(`${this.base}/students/${studentId}`);
  }
  addDocument(studentId: string, body: DocumentUpsert): Observable<DocumentView> {
    return this.http.post<DocumentView>(`${this.base}/students/${studentId}/files`, body);
  }
  removeDocument(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/files/${id}`);
  }
  addOrientation(studentId: string, body: OrientationUpsert): Observable<OrientationView> {
    return this.http.post<OrientationView>(`${this.base}/students/${studentId}/orientations`, body);
  }
  removeOrientation(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/orientations/${id}`);
  }
}
