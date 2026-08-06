import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AttendanceView, DailyBoard, AttendancePolicy, AttendanceClass, AttendanceRoster,
  AttendanceSessionSummary, AttendanceAnalytics, DeviceReconciliation, AttendanceDevice } from '../../core/models';

@Injectable({ providedIn: 'root' })
export class AttendanceApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/attendance`;

  board(date?: string): Observable<DailyBoard> {
    const q = date ? `?date=${date}` : '';
    return this.http.get<DailyBoard>(`${this.base}/board${q}`);
  }

  mark(studentId: string, date: string, status: string,
       checkInTime?: string, lateMinutes = 0): Observable<AttendanceView> {
    return this.http.post<AttendanceView>(`${this.base}/mark`,
      { studentId, date, status, checkInTime, lateMinutes });
  }

  policies(): Observable<AttendancePolicy[]> { return this.http.get<AttendancePolicy[]>(`${this.base}/policies`); }
  updatePolicy(level: string, policy: Partial<AttendancePolicy>): Observable<AttendancePolicy> {
    return this.http.put<AttendancePolicy>(`${this.base}/policies/${level}`, policy);
  }
  classes(): Observable<AttendanceClass[]> { return this.http.get<AttendanceClass[]>(`${this.base}/classes`); }
  sessions(classId: string, date: string): Observable<AttendanceSessionSummary[]> {
    return this.http.get<AttendanceSessionSummary[]>(`${this.base}/sessions?classId=${classId}&date=${date}`);
  }
  roster(classId: string, date: string, periodKey?: string): Observable<AttendanceRoster> {
    const period = periodKey ? `&periodKey=${encodeURIComponent(periodKey)}` : '';
    return this.http.get<AttendanceRoster>(`${this.base}/roster?classId=${classId}&date=${date}${period}`);
  }
  save(roster: AttendanceRoster): Observable<AttendanceRoster> {
    return this.http.put<AttendanceRoster>(`${this.base}/sessions/marks`, {
      sessionId: roster.session.id, version: roster.session.version,
      marks: roster.marks.map(({ studentId, status, reason, note, lateMinutes }) =>
        ({ studentId, status, reason, note, lateMinutes })),
    });
  }
  finalize(id: string, version: number): Observable<AttendanceRoster> {
    return this.http.post<AttendanceRoster>(`${this.base}/sessions/${id}/finalize`, { version });
  }
  reopen(id: string, version: number, reason: string): Observable<AttendanceRoster> {
    return this.http.post<AttendanceRoster>(`${this.base}/sessions/${id}/reopen`, { version, reason });
  }
  generate(from: string, to: string, preview: boolean): Observable<{preview:boolean;from:string;to:string;expectedSessions:number;synchronizedSessions:number}> {
    return this.http.post<any>(`${this.base}/generate?from=${from}&to=${to}&preview=${preview}`, {});
  }
  analytics(from: string, to: string, classId?: string): Observable<AttendanceAnalytics> {
    const cls = classId ? `&classId=${classId}` : '';
    return this.http.get<AttendanceAnalytics>(`${this.base}/analytics?from=${from}&to=${to}${cls}`);
  }
  devices(): Observable<AttendanceDevice[]> { return this.http.get<AttendanceDevice[]>(`${this.base}/devices`); }
  reconciliation(date: string): Observable<DeviceReconciliation[]> {
    return this.http.get<DeviceReconciliation[]>(`${this.base}/reconciliation?date=${date}`);
  }
  reconcile(deviceRecordId: string, sessionId: string): Observable<AttendanceRoster> {
    return this.http.post<AttendanceRoster>(`${this.base}/reconciliation`, { deviceRecordId, sessionId });
  }
  scanAlerts(from: string, to: string): Observable<{createdOrUpdated:number;thresholdPercent:number}> {
    return this.http.post<any>(`${this.base}/alerts/scan?from=${from}&to=${to}`, {});
  }
}
