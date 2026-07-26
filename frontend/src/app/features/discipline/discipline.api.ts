import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface IncidentView {
  id: string;
  studentId: string;
  studentName: string;
  className: string;
  incidentDate: string;
  type: string;
  description: string;
  sanction: string;
}

export interface IncidentUpsert {
  studentRef: string;
  incidentDate: string;
  type: string;
  description?: string;
  sanction?: string;
}

export interface StudentLookup {
  id: string;
  matricule: string;
  name: string;
  className: string;
  parentName: string;
  parentPhone: string;
}

export interface NotifyRequest {
  studentRef: string;
  channel: 'sms' | 'email';
  message: string;
}

export interface NotifyResult {
  studentId: string;
  channel: string;
  delivered: boolean;
  recipient: string | null;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class DisciplineApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/discipline`;

  list(): Observable<IncidentView[]> {
    return this.http.get<IncidentView[]>(this.base);
  }
  lookup(q: string): Observable<StudentLookup> {
    return this.http.get<StudentLookup>(`${this.base}/lookup`, { params: { q } });
  }
  create(body: IncidentUpsert): Observable<IncidentView> {
    return this.http.post<IncidentView>(this.base, body);
  }
  notify(body: NotifyRequest): Observable<NotifyResult> {
    return this.http.post<NotifyResult>(`${this.base}/notify`, body);
  }
  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
