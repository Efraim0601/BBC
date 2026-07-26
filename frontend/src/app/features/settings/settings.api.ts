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

export interface RoleUpsert {
  code?: string;
  labelFr: string;
  labelEn?: string;
}

export interface PermissionMatrix {
  modules: string[];
  roles: RoleView[];
  matrix: Record<string, Record<string, string>>;
}

export interface PermissionUpdate {
  roleCode: string;
  module: string;
  level: string;
}

export interface SchoolProfileView {
  code: string;
  name: string;
  motto: string | null;
  city: string | null;
  country: string | null;
  address: string | null;
  phone: string | null;
  email: string | null;
  website: string | null;
  currency: string;
  authority: string | null;
  academicYear: string | null;
  schoolStartTime: string;
  schoolEndTime: string;
}

export interface SchoolProfileUpdate {
  name: string;
  motto?: string | null;
  city?: string | null;
  country?: string | null;
  address?: string | null;
  phone?: string | null;
  email?: string | null;
  website?: string | null;
  currency?: string | null;
  authority?: string | null;
  schoolStartTime?: string | null;
  schoolEndTime?: string | null;
}

export interface HolidayView {
  id: string;
  date: string;
  label: string;
}

export interface CatalogItemView {
  id: string;
  kind: 'type' | 'sanction';
  code: string;
  labelFr: string;
  labelEn: string;
  sortOrder: number;
  active: boolean;
}

export interface CatalogItemUpsert {
  kind: 'type' | 'sanction';
  code?: string;
  labelFr: string;
  labelEn?: string;
  sortOrder?: number;
  active?: boolean;
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

  createRole(body: RoleUpsert): Observable<RoleView> {
    return this.http.post<RoleView>(`${this.base}/roles`, body);
  }

  updateRole(code: string, body: RoleUpsert): Observable<RoleView> {
    return this.http.put<RoleView>(`${this.base}/roles/${encodeURIComponent(code)}`, body);
  }

  deleteRole(code: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/roles/${encodeURIComponent(code)}`);
  }

  getSchool(): Observable<SchoolProfileView> {
    return this.http.get<SchoolProfileView>(`${this.base}/school`);
  }

  updateSchool(body: SchoolProfileUpdate): Observable<SchoolProfileView> {
    return this.http.put<SchoolProfileView>(`${this.base}/school`, body);
  }

  listHolidays(): Observable<HolidayView[]> {
    return this.http.get<HolidayView[]>(`${this.base}/holidays`);
  }

  addHoliday(date: string, label: string): Observable<HolidayView> {
    return this.http.post<HolidayView>(`${this.base}/holidays`, { date, label });
  }

  deleteHoliday(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/holidays/${id}`);
  }

  listCatalog(kind?: string): Observable<CatalogItemView[]> {
    const q = kind ? `?kind=${kind}` : '';
    return this.http.get<CatalogItemView[]>(`${this.base}/discipline-catalog${q}`);
  }

  createCatalog(body: CatalogItemUpsert): Observable<CatalogItemView> {
    return this.http.post<CatalogItemView>(`${this.base}/discipline-catalog`, body);
  }

  updateCatalog(id: string, body: CatalogItemUpsert): Observable<CatalogItemView> {
    return this.http.put<CatalogItemView>(`${this.base}/discipline-catalog/${id}`, body);
  }

  deleteCatalog(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/discipline-catalog/${id}`);
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
