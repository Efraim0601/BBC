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
  studentId: string;
  incidentDate: string;
  type: string;
  description?: string;
  sanction?: string;
}

@Injectable({ providedIn: 'root' })
export class DisciplineApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/discipline`;

  list(): Observable<IncidentView[]> {
    return this.http.get<IncidentView[]>(this.base);
  }
  create(body: IncidentUpsert): Observable<IncidentView> {
    return this.http.post<IncidentView>(this.base, body);
  }
  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
