import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface NoticeView {
  id: string;
  studentId: string;
  studentName: string;
  className: string;
  category: string;
  subject: string;
  body: string;
  requiresAck: boolean;
  acknowledged: boolean;
  acknowledgedAt: string | null;
  acknowledgedBy: string | null;
  senderName: string | null;
  createdAt: string;
}

export interface NoticeUpsert {
  studentId: string;
  category: string;
  subject: string;
  body: string;
  requiresAck: boolean;
}

@Injectable({ providedIn: 'root' })
export class MessagesApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/messages`;

  forStudent(studentId: string): Observable<NoticeView[]> {
    return this.http.get<NoticeView[]>(`${this.base}?studentId=${studentId}`);
  }
  list(): Observable<NoticeView[]> {
    return this.http.get<NoticeView[]>(this.base);
  }
  create(body: NoticeUpsert): Observable<NoticeView> {
    return this.http.post<NoticeView>(this.base, body);
  }
  acknowledge(id: string, signedBy: string): Observable<NoticeView> {
    return this.http.post<NoticeView>(`${this.base}/${id}/ack`, { signedBy });
  }
  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
