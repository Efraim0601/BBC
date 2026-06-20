import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface EventView {
  id: string;
  title: string;
  type: string;
  eventDate: string;
  description: string;
  audience: string;
  targetClasses: string[];
  notified: boolean;
  notifiedAt: string | null;
}

export interface EventUpsert {
  title: string;
  type: string;
  eventDate: string;
  description?: string;
  audience?: string;
  targetClasses?: string[];
}

export interface NotifyResult {
  eventId: string;
  notifiedCount: number;
}

@Injectable({ providedIn: 'root' })
export class EventApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/events`;

  list(): Observable<EventView[]> {
    return this.http.get<EventView[]>(this.base);
  }
  create(body: EventUpsert): Observable<EventView> {
    return this.http.post<EventView>(this.base, body);
  }
  update(id: string, body: EventUpsert): Observable<EventView> {
    return this.http.put<EventView>(`${this.base}/${id}`, body);
  }
  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
  notify(id: string): Observable<NotifyResult> {
    return this.http.post<NotifyResult>(`${this.base}/${id}/notify`, {});
  }
}
