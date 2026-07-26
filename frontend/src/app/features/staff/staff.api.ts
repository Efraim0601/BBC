import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface EmployeeView {
  id: string;
  code: string;
  name: string;
  initials: string;
  sex: string;
  type: string;
  email: string;
  phone: string;
  formClass: string;
  departmentId: string | null;
  departmentName: string | null;
  monthlySalary: number;
  hourlyRate: number;
  roles: string[];
  active: boolean;
  hasLogin: boolean;
  username: string | null;
}

export interface EmployeeUpsert {
  name: string;
  sex?: string;
  type?: string;
  email?: string;
  phone?: string;
  formClass?: string;
  departmentId?: string | null;
  monthlySalary?: number;
  hourlyRate?: number;
  roles?: string[];
  createLogin?: boolean;
}

export interface AccountResult {
  hasAccount: boolean;
  username: string;
  emailSent: boolean;
  message: string;
}

export interface StaffImportRow {
  name: string;
  sex?: string;
  type?: string;
  email?: string;
  phone?: string;
  formClass?: string;
  department?: string;
  departmentId?: string | null;
  monthlySalary?: number | null;
  hourlyRate?: number | null;
  roles?: string[];
}

export interface StaffImportRequest {
  createLogin?: boolean;
  rows: StaffImportRow[];
}

export interface StaffImportError {
  row: number;
  name: string;
  message: string;
}

export interface StaffImportResult {
  created: number;
  failed: number;
  errors: StaffImportError[];
}

@Injectable({ providedIn: 'root' })
export class StaffApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/staff`;

  list(): Observable<EmployeeView[]> {
    return this.http.get<EmployeeView[]>(this.base);
  }
  get(id: string): Observable<EmployeeView> {
    return this.http.get<EmployeeView>(`${this.base}/${id}`);
  }
  create(body: EmployeeUpsert): Observable<EmployeeView> {
    return this.http.post<EmployeeView>(this.base, body);
  }
  update(id: string, body: EmployeeUpsert): Observable<EmployeeView> {
    return this.http.put<EmployeeView>(`${this.base}/${id}`, body);
  }
  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
  resetCredentials(id: string): Observable<AccountResult> {
    return this.http.post<AccountResult>(`${this.base}/${id}/reset-credentials`, {});
  }
  importStaff(body: StaffImportRequest): Observable<StaffImportResult> {
    return this.http.post<StaffImportResult>(`${this.base}/import`, body);
  }
}
