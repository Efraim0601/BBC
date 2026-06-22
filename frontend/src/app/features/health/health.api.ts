import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface HealthRecordView {
  id: string;
  studentId: string;
  bloodGroup: string | null;
  allergies: string | null;
  conditions: string | null;
  vaccinations: string | null;
  doctorName: string | null;
  doctorPhone: string | null;
  heightCm: number | null;
  weightKg: number | null;
}

export interface HealthRecordUpsert {
  bloodGroup?: string | null;
  allergies?: string | null;
  conditions?: string | null;
  vaccinations?: string | null;
  doctorName?: string | null;
  doctorPhone?: string | null;
  heightCm?: number | null;
  weightKg?: number | null;
}

export interface VisitView {
  id: string;
  studentId: string;
  visitDate: string;
  reason: string;
  treatment: string | null;
}

export interface VisitUpsert {
  visitDate: string;
  reason: string;
  treatment?: string | null;
}

export interface ActivityView {
  id: string;
  studentId: string;
  name: string;
  category: string;
  role: string | null;
  season: string | null;
}

export interface ActivityUpsert {
  name: string;
  category: string;
  role?: string | null;
  season?: string | null;
}

export interface StudentHealth {
  studentId: string;
  studentName: string;
  matricule: string;
  className: string;
  record: HealthRecordView | null;
  visits: VisitView[];
  activities: ActivityView[];
}

@Injectable({ providedIn: 'root' })
export class HealthApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/health`;

  forStudent(studentId: string): Observable<StudentHealth> {
    return this.http.get<StudentHealth>(`${this.base}/students/${studentId}`);
  }
  saveRecord(studentId: string, body: HealthRecordUpsert): Observable<HealthRecordView> {
    return this.http.put<HealthRecordView>(`${this.base}/students/${studentId}/record`, body);
  }
  addVisit(studentId: string, body: VisitUpsert): Observable<VisitView> {
    return this.http.post<VisitView>(`${this.base}/students/${studentId}/visits`, body);
  }
  removeVisit(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/visits/${id}`);
  }
  addActivity(studentId: string, body: ActivityUpsert): Observable<ActivityView> {
    return this.http.post<ActivityView>(`${this.base}/students/${studentId}/activities`, body);
  }
  removeActivity(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/activities/${id}`);
  }
}
