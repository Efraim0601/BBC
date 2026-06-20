import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface RoleView {
  code: string;
  labelFr: string;
  labelEn: string;
  builtin: boolean;
}

export interface PermissionMatrix {
  modules: string[];
  roles: RoleView[];
  matrix: Record<string, Record<string, string>>; // matrix[roleCode][module] = 'none'|'read'|'write'
}

export interface PermissionUpdate {
  roleCode: string;
  module: string;
  level: string;
}

export interface MailConfigView {
  enabled: boolean;
  host: string | null;
  port: number;
  username: string | null;
  passwordSet: boolean;
  fromAddress: string | null;
  fromName: string | null;
  useTls: boolean;
  notifyOnUserCreate: boolean;
}

export interface MailConfigUpdate {
  enabled: boolean;
  host: string | null;
  port: number;
  username: string | null;
  password: string | null;
  fromAddress: string | null;
  fromName: string | null;
  useTls: boolean;
  notifyOnUserCreate: boolean;
}

@Injectable({ providedIn: 'root' })
export class SettingsApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/settings`;

  getMatrix(): Observable<PermissionMatrix> {
    return this.http.get<PermissionMatrix>(`${this.base}/permissions`);
  }

  update(updates: PermissionUpdate[]): Observable<PermissionMatrix> {
    return this.http.put<PermissionMatrix>(`${this.base}/permissions`, { updates });
  }

  getMail(): Observable<MailConfigView> {
    return this.http.get<MailConfigView>(`${this.base}/mail`);
  }

  updateMail(body: MailConfigUpdate): Observable<MailConfigView> {
    return this.http.put<MailConfigView>(`${this.base}/mail`, body);
  }

  testMail(to: string): Observable<void> {
    return this.http.post<void>(`${this.base}/mail/test`, { to });
  }
}
