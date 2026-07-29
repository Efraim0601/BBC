import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface EmployeeView {
  id: string;
  code: string;
  name: string;
  initials: string;
  sex: string;
  type: string;
  email: string;
  phone: string;
  formClass: string;
  /** Section (cycle) : maternelle | primary | secondary ; null pour le personnel non enseignant. */
  section: string | null;
  departmentId: string | null;
  departmentName: string | null;
  monthlySalary: number;
  hourlyRate: number;
  roles: string[];
  active: boolean;
  hasLogin: boolean;
  username: string | null;
}

export interface EmployeeUpsert {
  name: string;
  sex?: string;
  type?: string;
  email?: string;
  phone?: string;
  formClass?: string;
  section?: string | null;
  departmentId?: string | null;
  monthlySalary?: number;
  hourlyRate?: number;
  roles?: string[];
  createLogin?: boolean;
}

/** Une classe assignée à un enseignant. */
export interface TeacherClassView {
  id: string;
  name: string;
  level: string;
  subsystem: string;
  sectionLabel: string | null;
  studentCount: number;
}

export interface AccountResult {
  hasAccount: boolean;
  username: string;
  emailSent: boolean;
  message: string;
}

export interface StaffImportRow {
  name: string;
  sex?: string;
  type?: string;
  email?: string;
  phone?: string;
  formClass?: string;
  section?: string;
  department?: string;
  departmentId?: string | null;
  monthlySalary?: number | null;
  hourlyRate?: number | null;
  roles?: string[];
}

export interface StaffImportRequest {
  createLogin?: boolean;
  rows: StaffImportRow[];
}

export interface StaffImportError {
  row: number;
  name: string;
  message: string;
}

export interface StaffImportResult {
  created: number;
  failed: number;
  errors: StaffImportError[];
}

export interface StaffPortalMeta {
  schoolName: string;
  schoolCode: string;
  open: boolean;
}

export interface StaffApplicationSubmit {
  name: string;
  sex?: string;
  type?: string;
  email?: string;
  phone?: string;
  formClass?: string;
  departmentHint?: string;
  desiredRoles?: string;
  notes?: string;
}

export interface StaffApplicationView {
  id: string;
  status: string;
  name: string;
  sex: string | null;
  type: string;
  email: string | null;
  phone: string | null;
  formClass: string | null;
  departmentHint: string | null;
  desiredRoles: string | null;
  notes: string | null;
  rejectReason: string | null;
  employeeId: string | null;
  employeeCode: string | null;
  submittedAt: string;
  decidedAt: string | null;
  finalizedAt: string | null;
}

export interface StaffApplicationFinalize {
  type?: string;
  departmentId?: string | null;
  monthlySalary?: number;
  hourlyRate?: number;
  roles?: string[];
  formClass?: string;
  section?: string | null;
  createLogin?: boolean;
}

export interface StaffPortalSettingsView {
  enabled: boolean;
  slug: string | null;
  token: string | null;
  publicPath: string | null;
}

@Injectable({ providedIn: 'root' })
export class StaffApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/staff`;
  private publicBase = `${environment.apiUrl}/public/staff-portal`;

  list(): Observable<EmployeeView[]> {
    return this.http.get<EmployeeView[]>(this.base);
  }
  get(id: string): Observable<EmployeeView> {
    return this.http.get<EmployeeView>(`${this.base}/${id}`);
  }
  create(body: EmployeeUpsert): Observable<EmployeeView> {
    return this.http.post<EmployeeView>(this.base, body);
  }
  update(id: string, body: EmployeeUpsert): Observable<EmployeeView> {
    return this.http.put<EmployeeView>(`${this.base}/${id}`, body);
  }
  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
  resetCredentials(id: string): Observable<AccountResult> {
    return this.http.post<AccountResult>(`${this.base}/${id}/reset-credentials`, {});
  }
  importStaff(body: StaffImportRequest): Observable<StaffImportResult> {
    return this.http.post<StaffImportResult>(`${this.base}/import`, body);
  }

  portalMeta(slug: string, token: string): Observable<StaffPortalMeta> {
    return this.http.get<StaffPortalMeta>(`${this.publicBase}/${encodeURIComponent(slug)}/meta`, {
      params: { t: token },
    });
  }
  portalApply(slug: string, token: string, body: StaffApplicationSubmit): Observable<StaffApplicationView> {
    return this.http.post<StaffApplicationView>(
      `${this.publicBase}/${encodeURIComponent(slug)}/apply`,
      body,
      { params: { t: token } },
    );
  }

  getPortalSettings(): Observable<StaffPortalSettingsView> {
    return this.http.get<StaffPortalSettingsView>(`${this.base}/portal`);
  }
  updatePortalSettings(enabled: boolean): Observable<StaffPortalSettingsView> {
    return this.http.put<StaffPortalSettingsView>(`${this.base}/portal`, { enabled });
  }
  regeneratePortalToken(): Observable<StaffPortalSettingsView> {
    return this.http.post<StaffPortalSettingsView>(`${this.base}/portal/regenerate-token`, {});
  }

  listApplications(status?: string | null): Observable<StaffApplicationView[]> {
    const opts = status ? { params: { status } } : {};
    return this.http.get<StaffApplicationView[]>(`${this.base}/applications`, opts);
  }
  acceptApplication(id: string): Observable<StaffApplicationView> {
    return this.http.post<StaffApplicationView>(`${this.base}/applications/${id}/accept`, {});
  }
  rejectApplication(id: string, reason: string): Observable<StaffApplicationView> {
    return this.http.post<StaffApplicationView>(`${this.base}/applications/${id}/reject`, { reason });
  }
  /** Classes actuellement assignées à l'enseignant. */
  classesOf(id: string): Observable<TeacherClassView[]> {
    return this.http.get<TeacherClassView[]>(`${this.base}/${id}/classes`);
  }

  /** Remplace la liste ; une liste vide détache l'enseignant de toutes ses classes. */
  setClasses(id: string, classIds: string[]): Observable<TeacherClassView[]> {
    return this.http.put<TeacherClassView[]>(`${this.base}/${id}/classes`, { classIds });
  }

  finalizeApplication(id: string, body: StaffApplicationFinalize): Observable<StaffApplicationView> {
    return this.http.post<StaffApplicationView>(`${this.base}/applications/${id}/finalize`, body);
  }
}
