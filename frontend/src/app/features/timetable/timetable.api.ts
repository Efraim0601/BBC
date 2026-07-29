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

/** Un cours impliqué dans un chevauchement. */
export interface ConflictSlot {
  classId: string;
  className: string | null;
  subjectCode: string | null;
  room: string | null;
}

/** Un enseignant placé dans plusieurs classes — donc plusieurs salles — au même créneau. */
export interface TeacherConflict {
  dayIdx: number;
  slotIdx: number;
  teacherId: string;
  teacherName: string | null;
  slots: ConflictSlot[];
}

export interface SlotSaveResult {
  slot: SlotView;
  conflicts: TeacherConflict[];
}

export interface SlotSaveBody {
  className: string;
  dayIdx: number;
  slotIdx: number;
  subjectCode?: string;
  teacherId?: string;
  room?: string;
  /** Enregistrer malgré un chevauchement d'enseignant (classes regroupées). */
  allowOverlap?: boolean;
}

@Injectable({ providedIn: 'root' })
export class TimetableApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/timetable`;

  classes(): Observable<ClassRef[]> {
    return this.http.get<ClassRef[]>(`${this.base}/classes`);
  }

  rooms(): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/rooms`);
  }

  grid(className: string): Observable<SlotView[]> {
    return this.http.get<SlotView[]>(`${this.base}?className=${encodeURIComponent(className)}`);
  }

  /** Tous les chevauchements d'enseignant de l'établissement. */
  conflicts(): Observable<TeacherConflict[]> {
    return this.http.get<TeacherConflict[]>(`${this.base}/conflicts`);
  }

  saveSlot(body: SlotSaveBody): Observable<SlotSaveResult> {
    return this.http.put<SlotSaveResult>(`${this.base}/slot`, body);
  }

  deleteSlot(className: string, dayIdx: number, slotIdx: number): Observable<void> {
    const q = `?className=${encodeURIComponent(className)}&dayIdx=${dayIdx}&slotIdx=${slotIdx}`;
    return this.http.delete<void>(`${this.base}${q}`);
  }
}
