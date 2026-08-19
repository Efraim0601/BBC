import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Student } from '../../core/models';

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
}
