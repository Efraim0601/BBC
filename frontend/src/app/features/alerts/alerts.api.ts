import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface AlertView {
  id: string;
  studentId: string;
  studentName: string;
  className: string;
  type: string;        // grade_drop | absences | discipline | unpaid
  severity: string;    // info | warn | critical
  title: string;
  detail: string | null;
  status: string;      // open | ack | resolved
  createdAt: string;
}

export interface ScanResult {
  created: number;
}

@Injectable({ providedIn: 'root' })
export class AlertsApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/alerts`;

  list(): Observable<AlertView[]> {
    return this.http.get<AlertView[]>(this.base);
  }
  scan(): Observable<ScanResult> {
    return this.http.post<ScanResult>(`${this.base}/scan`, {});
  }
  ack(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/ack`, {});
  }
  resolve(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/resolve`, {});
  }
}
