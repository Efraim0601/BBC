import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Student } from '../../core/models';

export interface StudentUpsert {
  firstName: string;
  lastName: string;
  sex?: string;
  dob?: string | null;
  className?: string;
  subsystem?: string;
  level?: string;
  parentName?: string;
  parentPhone?: string;
}

@Injectable({ providedIn: 'root' })
export class StudentApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/students`;

  list(className?: string): Observable<Student[]> {
    const q = className ? `?className=${encodeURIComponent(className)}` : '';
    return this.http.get<Student[]>(`${this.base}${q}`);
  }
  create(body: StudentUpsert): Observable<Student> {
    return this.http.post<Student>(this.base, body);
  }
  update(id: string, body: StudentUpsert): Observable<Student> {
    return this.http.put<Student>(`${this.base}/${id}`, body);
  }
  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
