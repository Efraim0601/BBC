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

export interface SlotView {
  id: string;
  dayIdx: number;
  slotIdx: number;
  subjectCode: string | null;
  teacherId: string | null;
  room: string | null;
}

export interface Conflict {
  className: string;
  dayIdx: number;
  slotIdx: number;
  teacherId: string;
}

export interface SlotSaveResult {
  slot: SlotView;
  conflicts: Conflict[];
}

export interface SlotSaveBody {
  className: string;
  dayIdx: number;
  slotIdx: number;
  subjectCode?: string;
  teacherId?: string;
  room?: string;
}

@Injectable({ providedIn: 'root' })
export class TimetableApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/timetable`;

  classes(): Observable<ClassRef[]> {
    return this.http.get<ClassRef[]>(`${this.base}/classes`);
  }

  grid(className: string): Observable<SlotView[]> {
    return this.http.get<SlotView[]>(`${this.base}?className=${encodeURIComponent(className)}`);
  }

  saveSlot(body: SlotSaveBody): Observable<SlotSaveResult> {
    return this.http.put<SlotSaveResult>(`${this.base}/slot`, body);
  }

  deleteSlot(className: string, dayIdx: number, slotIdx: number): Observable<void> {
    const q = `?className=${encodeURIComponent(className)}&dayIdx=${dayIdx}&slotIdx=${slotIdx}`;
    return this.http.delete<void>(`${this.base}${q}`);
  }
}
