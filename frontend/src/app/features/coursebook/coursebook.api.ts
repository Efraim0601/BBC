import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ClassRef {
  id: string;
  name: string;
  sectionId: string;
  subsystem: string;
  level: string;
}

export interface EntryView {
  id: string;
  className: string;
  subjectCode: string;
  subjectLabel: string;
  entryDate: string;
  content: string;
  homework: string | null;
  dueDate: string | null;
}

export interface EntryUpsert {
  className: string;
  subjectCode: string;
  entryDate: string;
  content: string;
  homework?: string | null;
  dueDate?: string | null;
}

@Injectable({ providedIn: 'root' })
export class CoursebookApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/coursebook`;

  classes(): Observable<ClassRef[]> {
    return this.http.get<ClassRef[]>(`${environment.apiUrl}/timetable/classes`);
  }

  forClass(className: string): Observable<EntryView[]> {
    return this.http.get<EntryView[]>(`${this.base}?className=${encodeURIComponent(className)}`);
  }

  create(body: EntryUpsert): Observable<EntryView> {
    return this.http.post<EntryView>(this.base, body);
  }

  update(id: string, body: EntryUpsert): Observable<EntryView> {
    return this.http.put<EntryView>(`${this.base}/${id}`, body);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
