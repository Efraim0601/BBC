import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface BulletinLine {
  subjectCode: string;
  subjectLabel: string;
  coef: number;
  mark: number;
  weighted: number;
}

export interface BulletinView {
  studentId: string;
  studentName: string;
  className: string;
  sequence: number;
  lines: BulletinLine[];
  average: number;
  rank: number;
  classSize: number;
  classAverage: number;
  validated: boolean;
  generalAppreciation: string | null;
  financiallyBlocked: boolean;
}

export interface PvRow {
  studentId: string;
  studentName: string;
  average: number;
  rank: number;
}

export interface PvView {
  className: string;
  sequence: number;
  rows: PvRow[];
  classAverage: number;
}

@Injectable({ providedIn: 'root' })
export class AcademicApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/academic`;

  bulletin(studentId: string, sequence: number): Observable<BulletinView> {
    return this.http.get<BulletinView>(
      `${this.base}/students/${encodeURIComponent(studentId)}/bulletin?sequence=${sequence}`,
    );
  }

  validate(studentId: string, sequence: number, appreciation: string): Observable<BulletinView> {
    return this.http.post<BulletinView>(
      `${this.base}/students/${encodeURIComponent(studentId)}/bulletin/validate`,
      { sequence, generalAppreciation: appreciation },
    );
  }

  pv(className: string, sequence: number): Observable<PvView> {
    return this.http.get<PvView>(
      `${this.base}/classes/${encodeURIComponent(className)}/pv?sequence=${sequence}`,
    );
  }
}
