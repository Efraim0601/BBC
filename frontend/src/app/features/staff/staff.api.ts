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
  monthlySalary: number;
  hourlyRate: number;
  roles: string[];
  active: boolean;
}

export interface EmployeeUpsert {
  name: string;
  sex?: string;
  type?: string;
  email?: string;
  phone?: string;
  formClass?: string;
  monthlySalary?: number;
  hourlyRate?: number;
  roles?: string[];
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
}
