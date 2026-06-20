import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AttendanceView, DailyBoard } from '../../core/models';

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
}
