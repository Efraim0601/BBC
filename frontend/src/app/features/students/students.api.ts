import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Student } from '../../core/models';
import { ClassView } from '../../core/setup.api';

export interface StudentUpsert {
  firstName: string;
  lastName: string;
  niu?: string | null;
  sex?: string;
  dob?: string | null;
  birthplace?: string | null;
  repeats?: boolean;
  classId?: string | null;
  className?: string;
  subsystem?: string;
  level?: string;
  parentName?: string;
  parentPhone?: string;
  fatherName?: string | null;
  fatherPhone?: string | null;
  fatherEmail?: string | null;
  motherName?: string | null;
  motherPhone?: string | null;
  motherEmail?: string | null;
  guardianName?: string | null;
  guardianPhone?: string | null;
  guardianEmail?: string | null;
  guardianRelation?: string | null;
  /** Enregistrer malgré un homonyme déjà au fichier — le serveur refuse (409) sans cette confirmation. */
  allowDuplicate?: boolean;
}

/** Une fiche déjà au registre qui ressemble à celle en cours de saisie. */
export interface DuplicateMatch {
  id: string;
  matricule: string;
  name: string;
  classId: string | null;
  className: string | null;
  level: string | null;
  subsystem: string | null;
  dob: string | null;
  niu: string | null;
  sameClass: boolean;
  sameNiu: boolean;
  sameName: boolean;
  sameDob: boolean;
}

export interface DuplicateCheckResult {
  exists: boolean;
  sameClass: boolean;
  /** Vrai quand le refus est ferme (NIU déjà attribué) : aucune confirmation ne le lève. */
  blocking: boolean;
  message: string | null;
  matches: DuplicateMatch[];
}

/** Paramètres de la recherche de doublons — le nom et le prénom suffisent. */
export interface DuplicateQuery {
  lastName?: string;
  firstName?: string;
  niu?: string | null;
  dob?: string | null;
  classId?: string | null;
  /** La fiche en cours de modification, qui ne doit pas se signaler elle-même. */
  excludeId?: string | null;
}

export interface ParentAccountView {
  userId: string;
  displayName: string;
  username: string;
  active: boolean;
  childCount: number;
}

export interface ParentLinkRequest {
  displayName: string;
  username: string;
  password?: string;
}

export type GuardianAccessMode = 'CREATE_ACCOUNT' | 'SEND_INVITE' | 'NO_PORTAL';
export interface GuardianInput {
  guardianId?: string | null; displayName: string; email?: string | null; phone?: string | null;
  relationshipType: string; accessMode: GuardianAccessMode; initialPassword?: string | null;
  legalGuardian?: boolean; livesWith?: boolean; emergencyPriority?: number | null;
  pickupAuthorized?: boolean; financeResponsible?: boolean; receivesAcademic?: boolean;
  receivesAttendance?: boolean; receivesFinance?: boolean; receivesDiscipline?: boolean;
  receivesHealth?: boolean; portalAccess?: boolean; notes?: string | null;
}
export interface GuardianPortalAccessInput { email?: string | null; accessMode: GuardianAccessMode; initialPassword?: string | null; }
export interface GuardianSearchView { id:string; displayName:string; maskedEmail?:string; maskedPhone?:string; linkedChildren:number; accountStatus:string; exactMatch:boolean; }
export interface GuardianRelationshipView extends GuardianInput {
  relationshipId:string; guardianId:string; email?:string; phone?:string; effectiveFrom:string;
  effectiveTo?:string|null; accountStatus:string; invitationStatus?:string|null; version:number;
}
export interface RegistrationRequest { student: StudentUpsert; guardians: GuardianInput[]; }
export interface RegistrationView { student:Student; guardians:GuardianRelationshipView[]; message:string; }
export interface FamilyImportRow { externalKey:string; firstName:string; lastName:string; niu?:string|null; sex?:string; dob?:string|null; birthplace?:string|null; repeats?:boolean; classId:string; guardians:Array<{displayName:string;email?:string|null;phone?:string|null;relationshipType:string;accessMode:string}>; }
export interface FamilyImportView { jobId:string;status:string;totalRows:number;validRows:number;createdRows:number;linkedGuardians:number;failedRows:number;rows:Array<{rowNumber:number;externalKey:string;studentName:string;outcome:string;message:string}>; }

/** One imported row — mirrors the fields asked for when creating a student by hand. */
export interface StudentImportRow {
  name?: string;
  firstName: string;
  lastName: string;
  niu?: string | null;
  sex?: string;
  dob?: string | null;
  birthplace?: string | null;
  repeats?: boolean;
  parentName?: string;
  parentPhone?: string;
  fatherName?: string;
  fatherPhone?: string;
  fatherEmail?: string;
  motherName?: string;
  motherPhone?: string;
  motherEmail?: string;
  guardianName?: string;
  guardianPhone?: string;
  guardianEmail?: string;
  guardianRelation?: string;
}

/** A class to find-or-create on the fly during import (the "5e A" format). */
export interface NewClassSpec {
  name: string;
  subsystem: string;   // FR | EN
  level: string;       // maternelle | primary | secondary
}

export interface StudentImportRequest {
  classId?: string | null;
  newClass?: NewClassSpec | null;
  rows: StudentImportRow[];
}

export interface StudentImportError {
  row: number;
  name: string;
  message: string;
}

/** Fiche créée malgré un élève du même nom inscrit dans une autre classe. */
export interface StudentImportWarning {
  row: number;
  name: string;
  message: string;
}

export interface StudentImportResult {
  created: number;
  /** Pupils already on file whose empty fields this register filled in. */
  updated: number;
  /** Pupils already on file the register had nothing to add to. */
  unchanged: number;
  fieldsFilled: number;
  failed: number;
  errors: StudentImportError[];
  /** Fiches créées alors qu'un homonyme existe ailleurs dans l'établissement. */
  warnings: StudentImportWarning[];
}

export interface BulkDeleteError {
  id: string;
  message: string;
}

/** Bilan d'une suppression groupée — les refus sont rendus fiche par fiche. */
export interface BulkDeleteResult {
  deleted: number;
  failed: number;
  errors: BulkDeleteError[];
}

@Injectable({ providedIn: 'root' })
export class StudentApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/students`;

  list(className?: string): Observable<Student[]> {
    const q = className ? `?className=${encodeURIComponent(className)}` : '';
    return this.http.get<Student[]>(`${this.base}${q}`);
  }
  listClassOptions(): Observable<ClassView[]> {
    return this.http.get<ClassView[]>(`${this.base}/class-options`);
  }
  listRoster(sessionId: string, classId: string): Observable<Student[]> {
    return this.http.get<Student[]>(`${this.base}/roster`, { params: { sessionId, classId } });
  }
  get(id:string):Observable<Student>{ return this.http.get<Student>(`${this.base}/${id}`); }
  create(body: StudentUpsert): Observable<Student> {
    return this.http.post<Student>(this.base, body);
  }
  /** Cet élève est-il déjà au registre ? Interrogé pendant la saisie de la fiche. */
  checkDuplicates(q: DuplicateQuery): Observable<DuplicateCheckResult> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(q)) {
      if (value !== null && value !== undefined && value !== '') params = params.set(key, value);
    }
    return this.http.get<DuplicateCheckResult>(`${this.base}/duplicates`, { params });
  }
  importStudents(body: StudentImportRequest): Observable<StudentImportResult> {
    return this.http.post<StudentImportResult>(`${this.base}/import`, body);
  }
  update(id: string, body: StudentUpsert): Observable<Student> {
    return this.http.put<Student>(`${this.base}/${id}`, body);
  }
  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
  /** Retire en une requête tous les élèves cochés dans la liste. */
  bulkDelete(ids: string[]): Observable<BulkDeleteResult> {
    return this.http.post<BulkDeleteResult>(`${this.base}/bulk-delete`, { ids });
  }

  // Parent accounts (review issue #2)
  listParents(studentId: string): Observable<ParentAccountView[]> {
    return this.http.get<ParentAccountView[]>(`${this.base}/${studentId}/parents`);
  }
  linkParent(studentId: string, body: ParentLinkRequest): Observable<ParentAccountView> {
    return this.http.post<ParentAccountView>(`${this.base}/${studentId}/parents`, body);
  }
  unlinkParent(studentId: string, parentUserId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${studentId}/parents/${parentUserId}`);
  }
  register(body:RegistrationRequest):Observable<RegistrationView>{return this.http.post<RegistrationView>(`${environment.apiUrl}/student-registrations`,body);}
  searchGuardians(q:string):Observable<GuardianSearchView[]>{return this.http.get<GuardianSearchView[]>(`${environment.apiUrl}/guardians/search`,{params:{q}});}
  guardians(studentId:string):Observable<GuardianRelationshipView[]>{return this.http.get<GuardianRelationshipView[]>(`${this.base}/${studentId}/guardians`);}
  addGuardian(studentId:string,body:GuardianInput):Observable<GuardianRelationshipView>{return this.http.post<GuardianRelationshipView>(`${this.base}/${studentId}/guardians`,body);}
  updateGuardianPortalAccess(studentId:string,guardianId:string,body:GuardianPortalAccessInput):Observable<GuardianRelationshipView>{return this.http.put<GuardianRelationshipView>(`${this.base}/${studentId}/guardians/${guardianId}/portal-access`,body);}
  updateRelationship(id:string,body:Partial<GuardianRelationshipView>):Observable<GuardianRelationshipView>{return this.http.put<GuardianRelationshipView>(`${environment.apiUrl}/student-guardian-relationships/${id}`,body);}
  endRelationship(id:string,reason:string):Observable<void>{return this.http.delete<void>(`${environment.apiUrl}/student-guardian-relationships/${id}`,{params:{reason}});}
  resendInvite(id:string):Observable<unknown>{return this.http.post(`${environment.apiUrl}/guardians/${id}/resend-invite`,{});}
  familyImportDryRun(rows:FamilyImportRow[],sourceName:string):Observable<FamilyImportView>{return this.http.post<FamilyImportView>(`${environment.apiUrl}/family-imports/dry-run`,{sourceName,rows});}
  familyImportCommit(id:string):Observable<FamilyImportView>{return this.http.post<FamilyImportView>(`${environment.apiUrl}/family-imports/${id}/commit`,{});}
}
