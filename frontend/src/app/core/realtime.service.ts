import { Injectable, inject } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';

/**
 * STOMP-over-WebSocket client. Lazily connects on the first watch().
 * Channels are tenant-scoped: /topic/school/{schoolId}/{channel}.
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService {
  private auth = inject(AuthService);
  private client?: Client;
  private subjects = new Map<string, Subject<any>>();
  private subs = new Map<string, StompSubscription>();

  /** Subscribe to a tenant channel (e.g. "attendance", "payments"). */
  watch<T = any>(channel: string): Observable<T> {
    this.ensureConnected();
    if (!this.subjects.has(channel)) {
      this.subjects.set(channel, new Subject<T>());
    }
    if (this.client?.connected && !this.subs.has(channel)) {
      this.bind(channel);
    }
    return this.subjects.get(channel)!.asObservable();
  }

  private ensureConnected(): void {
    if (this.client) return;
    const token = this.auth.accessToken;
    this.client = new Client({
      webSocketFactory: () => new SockJS(environment.wsUrl) as any,
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      reconnectDelay: 4000,
      onConnect: () => {
        // (re)bind every requested channel after a (re)connection
        for (const channel of this.subjects.keys()) this.bind(channel);
      },
    });
    this.client.activate();
  }

  private bind(channel: string): void {
    const schoolId = this.auth.user()?.schoolId;
    if (!schoolId || !this.client) return;
    const dest = `/topic/school/${schoolId}/${channel}`;
    const sub = this.client.subscribe(dest, (msg: IMessage) => {
      this.subjects.get(channel)?.next(JSON.parse(msg.body));
    });
    this.subs.set(channel, sub);
  }

  disconnect(): void {
    this.subs.forEach((s) => s.unsubscribe());
    this.subs.clear();
    this.client?.deactivate();
    this.client = undefined;
  }
}
