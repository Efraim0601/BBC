import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface FinanceReport {
  totalRevenue: number;
  totalExpense: number;
  balance: number;
  recoveryRate: number;
}

export interface AttendanceRow {
  studentId: string;
  studentName: string;
  className: string;
  present: number;
  late: number;
  absent: number;
  rate: number;
}

export interface Demographics {
  total: number;
  byLevel: Record<string, number>;
  bySubsystem: Record<string, number>;
  bySex: Record<string, number>;
}

@Injectable({ providedIn: 'root' })
export class ReportApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/reports`;

  finance(): Observable<FinanceReport> {
    return this.http.get<FinanceReport>(`${this.base}/finance`);
  }

  attendanceMonthly(month?: string): Observable<AttendanceRow[]> {
    const q = month ? `?month=${encodeURIComponent(month)}` : '';
    return this.http.get<AttendanceRow[]>(`${this.base}/attendance/monthly${q}`);
  }

  demographics(): Observable<Demographics> {
    return this.http.get<Demographics>(`${this.base}/demographics`);
  }
}
