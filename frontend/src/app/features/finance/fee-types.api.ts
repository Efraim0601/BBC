import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AccountingApiError } from './accounting.api';

export type FeeLifecycle = 'DRAFT' | 'ACTIVE' | 'INACTIVE';
export type FeeRevisionStatus = 'DRAFT' | 'ACTIVE' | 'SUPERSEDED';

export interface FeeAccountRef {
  id: string; code: string; nameFr: string; nameEn: string; accountType: string;
  currency: string | null; active: boolean; postingAllowed: boolean;
  compatible: boolean; compatibilityMessage: string | null;
}

export interface FeeTypeRevisionView {
  id: string; revisionNo: number; revisionStatus: FeeRevisionStatus;
  nameFr: string; nameEn: string; descriptionFr: string | null; descriptionEn: string | null;
  category: string; defaultAmountMinor: number; defaultCurrency: string; frequency: string;
  mandatory: boolean; refundable: boolean; taxable: boolean; taxBasisPoints: number;
  receivableAccountId: string | null; receivableAccount: FeeAccountRef | null;
  revenueAccountId: string | null; revenueAccount: FeeAccountRef | null;
  effectiveFrom: string | null; effectiveTo: string | null; effectiveStatus: string;
  activatedAt: string | null; version: number;
}

export interface FeeTypeView {
  id: string; code: string; lifecycle: FeeLifecycle; currentRevisionNo: number | null;
  currentRevision: FeeTypeRevisionView | null; revisions: FeeTypeRevisionView[];
  usageCount: number; effectiveStatus: string; version: number; createdAt: string | null;
  activatedAt: string | null; deactivatedAt: string | null; deactivationReason: string | null;
}

export interface FeeTypeRevisionInput {
  nameFr: string; nameEn: string; descriptionFr: string | null; descriptionEn: string | null;
  category: string; defaultAmountMinor: number; defaultCurrency: string; frequency: string;
  mandatory: boolean; refundable: boolean; taxable: boolean; taxBasisPoints: number;
  receivableAccountId: string | null; revenueAccountId: string | null;
  effectiveFrom: string | null; effectiveTo: string | null; version?: number;
}
export interface FeeTypeCreateRequest { code: string; revision: FeeTypeRevisionInput; }
export interface FeeTypeDraftUpdate { code: string; revision: FeeTypeRevisionInput; typeVersion: number; }
export interface FeeTypeRevisionCreateRequest { revision: FeeTypeRevisionInput; typeVersion: number; reason: string | null; }
export interface FeeTypeActionRequest { typeVersion: number; reason: string | null; }

export interface FeeTypeDependency {
  entityType: string; entityId: string; label: string; sessionId: string | null;
  sessionLabel: string | null; classId: string | null; classLabel: string | null;
  status: string | null; detail: string;
}
export interface FeeTypeUsageView { feeTypeId: string; code: string; usageCount: number; dependencies: FeeTypeDependency[]; }

export interface LegacyFeeCandidate {
  sourceKey: string; sourceConfigId: string; level: string; classId: string | null;
  rawName: string; suggestedCode: string; suggestedNameFr: string; suggestedNameEn: string;
  amountMinor: number; currency: string; category: string; ambiguous: boolean; reviewReason: string | null;
}
export interface LegacyPreviewView {
  candidates: LegacyFeeCandidate[]; candidateCount: number; ambiguousCount: number;
  unresolvedCount: number; generatedAt: string;
}
export interface LegacyMappingRow {
  sourceKey: string; accept: boolean; feeTypeId: string | null; code: string | null;
  nameFr: string | null; nameEn: string | null; category: string | null;
}
export interface LegacyMappingRequest { rows: LegacyMappingRow[]; reason: string | null; }
export interface LegacyMigrationResult {
  acceptedCount: number; unresolvedCount: number; mappedFeeTypes: FeeTypeView[];
  unresolved: LegacyFeeCandidate[]; completedAt: string;
}
export interface FeeTypeComparison {
  feeTypeId: string; code: string; leftRevision: number; rightRevision: number;
  differences: { field: string; leftValue: string; rightValue: string }[];
}

@Injectable({ providedIn: 'root' })
export class FeeTypesApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/finance/v2/fee-types`;

  list(query?: string, lifecycle?: string, category?: string): Observable<FeeTypeView[]> {
    let params = new HttpParams();
    if (query?.trim()) params = params.set('query', query.trim());
    if (lifecycle) params = params.set('lifecycle', lifecycle);
    if (category) params = params.set('category', category);
    return this.http.get<FeeTypeView[]>(this.base, { params });
  }
  detail(id: string): Observable<FeeTypeView> { return this.http.get<FeeTypeView>(`${this.base}/${id}`); }
  create(body: FeeTypeCreateRequest): Observable<FeeTypeView> { return this.http.post<FeeTypeView>(this.base, body); }
  updateDraft(id: string, body: FeeTypeDraftUpdate): Observable<FeeTypeView> { return this.http.put<FeeTypeView>(`${this.base}/${id}/draft`, body); }
  createRevision(id: string, body: FeeTypeRevisionCreateRequest): Observable<FeeTypeView> { return this.http.post<FeeTypeView>(`${this.base}/${id}/revisions`, body); }
  activate(id: string, body: FeeTypeActionRequest): Observable<FeeTypeView> { return this.http.post<FeeTypeView>(`${this.base}/${id}/activate`, body); }
  deactivate(id: string, body: FeeTypeActionRequest): Observable<FeeTypeView> { return this.http.post<FeeTypeView>(`${this.base}/${id}/deactivate`, body); }
  usage(id: string): Observable<FeeTypeUsageView> { return this.http.get<FeeTypeUsageView>(`${this.base}/${id}/usage`); }
  compare(id: string, leftRevision: number, rightRevision: number): Observable<FeeTypeComparison> {
    const params = new HttpParams().set('leftRevision', leftRevision).set('rightRevision', rightRevision);
    return this.http.get<FeeTypeComparison>(`${this.base}/${id}/compare`, { params });
  }
  legacyPreview(): Observable<LegacyPreviewView> { return this.http.get<LegacyPreviewView>(`${this.base}/legacy/fee-config/preview`); }
  migrateLegacy(body: LegacyMappingRequest): Observable<LegacyMigrationResult> {
    return this.http.post<LegacyMigrationResult>(`${this.base}/legacy/fee-config/migrate`, body);
  }
}

export type FeeTypesApiError = AccountingApiError;
