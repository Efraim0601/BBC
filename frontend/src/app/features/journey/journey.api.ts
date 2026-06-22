import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface JourneyView {
  id: string;
  studentId: string;
  academicYear: string;
  className: string;
  level: string | null;
  subsystem: string | null;
  result: string;
  generalAverage: number | null;
  rank: number | null;
  classSize: number | null;
  decision: string | null;
  note: string | null;
}

export interface StudentJourney {
  studentId: string;
  studentName: string;
  matricule: string;
  currentClass: string;
  yearsCount: number;
  bestAverage: number | null;
  entries: JourneyView[];
}

export interface JourneyUpsert {
  studentId: string;
  academicYear: string;
  className: string;
  level?: string;
  subsystem?: string;
  result?: string;
  generalAverage?: number | null;
  rank?: number | null;
  classSize?: number | null;
  decision?: string;
  note?: string;
}

@Injectable({ providedIn: 'root' })
export class JourneyApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/journey`;

  forStudent(studentId: string): Observable<StudentJourney> {
    return this.http.get<StudentJourney>(`${this.base}/students/${studentId}`);
  }
  upsert(body: JourneyUpsert): Observable<JourneyView> {
    return this.http.post<JourneyView>(this.base, body);
  }
  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
