import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface DepartmentView { id: string; name: string; headEmployeeId: string | null; headName: string | null; memberCount: number; }
export interface DepartmentUpsert { name: string; headEmployeeId?: string | null; }

export interface LeaveView {
  id: string; employeeId: string; employeeName: string | null; type: string;
  startDate: string; endDate: string; days: number; reason: string | null;
  status: 'pending' | 'approved' | 'rejected'; decidedAt: string | null;
}
export interface LeaveCreate { employeeId: string; type: string; startDate: string; endDate: string; reason?: string; }

/** HR / Operations — departments and leave management. */
@Injectable({ providedIn: 'root' })
export class HrApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/hr`;

  listDepartments(): Observable<DepartmentView[]> { return this.http.get<DepartmentView[]>(`${this.base}/departments`); }
  createDepartment(b: DepartmentUpsert): Observable<DepartmentView> { return this.http.post<DepartmentView>(`${this.base}/departments`, b); }
  updateDepartment(id: string, b: DepartmentUpsert): Observable<DepartmentView> { return this.http.put<DepartmentView>(`${this.base}/departments/${id}`, b); }
  deleteDepartment(id: string): Observable<void> { return this.http.delete<void>(`${this.base}/departments/${id}`); }

  listLeaves(): Observable<LeaveView[]> { return this.http.get<LeaveView[]>(`${this.base}/leaves`); }
  createLeave(b: LeaveCreate): Observable<LeaveView> { return this.http.post<LeaveView>(`${this.base}/leaves`, b); }
  decideLeave(id: string, status: 'approved' | 'rejected'): Observable<LeaveView> {
    return this.http.put<LeaveView>(`${this.base}/leaves/${id}/decision`, { status });
  }
}
