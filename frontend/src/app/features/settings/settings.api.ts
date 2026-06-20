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

@Injectable({ providedIn: 'root' })
export class SettingsApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/settings/permissions`;

  getMatrix(): Observable<PermissionMatrix> {
    return this.http.get<PermissionMatrix>(this.base);
  }

  update(updates: PermissionUpdate[]): Observable<PermissionMatrix> {
    return this.http.put<PermissionMatrix>(this.base, { updates });
  }
}
