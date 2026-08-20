import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface AccessAction {
  code: string; module: string; groupCode: string; labelFr: string; labelEn: string;
  descriptionFr: string; descriptionEn: string; riskLevel: string; scopeType: string;
  requiredLevel: string; defaultReadAction: boolean; displayOrder: number;
}
export interface AccessActionGroup { code: string; labelFr: string; labelEn: string; actions: AccessAction[]; }
export interface AccessRole { code: string; labelFr: string; labelEn: string; builtin: boolean; }
export interface AccessRule {
  id: string | null; subjectType: string; subjectCode: string; actionCode: string;
  effect: 'ALLOW' | 'DENY' | 'INHERIT' | string; scopeMode: string; scopePayload: unknown;
  effectiveFrom: string | null; effectiveTo: string | null; permanent: boolean;
  reason: string; version: number;
}
export interface AccessRoleWorkspace {
  roleCode: string; labelFr: string; labelEn: string; builtin: boolean;
  policyVersion: number; groups: AccessActionGroup[]; rules: AccessRule[];
}
export interface AccessRuleInput {
  actionCode: string; effect: string; scopeMode: string; scopePayload: unknown;
  effectiveFrom: string | null; effectiveTo: string | null; permanent: boolean; reason: string;
}
export interface AccessMutation {
  expectedPolicyVersion: number; reason: string; rules: AccessRuleInput[];
  confirmHighRisk: boolean; separationOfDutiesOverride: boolean; separationOfDutiesReason?: string;
}
export interface AccessUser {
  id: string; username: string; displayName: string; roleCode: string; active: boolean; roles: string[];
}
export interface AccessUserWorkspace { user: AccessUser; policyVersion: number; overrides: AccessRule[]; effectiveActions: AccessEffectiveAction[]; }
export interface AccessEffectiveAction {
  actionCode: string; labelFr: string; labelEn: string; effect: string; scopeMode: string;
  source: string; requiresContext: boolean; riskLevel: string;
}
export interface AccessPreviewChange {
  actionCode: string; beforeEffect: string; afterEffect: string; beforeScopeMode: string;
  afterScopeMode: string; riskLevel: string; changeType: 'ADDITION' | 'REMOVAL' | 'CHANGE' | string;
}
export interface AccessRiskWarning { code: string; severity: string; messageFr: string; messageEn: string; }
export interface AccessPolicyPreview {
  subjectType: string; subjectCode: string; currentPolicyVersion: number;
  changes: AccessPreviewChange[]; warnings: AccessRiskWarning[]; requiresConfirmation: boolean;
  affectedUsers: AccessUser[]; preservedUserExceptions: AccessRule[];
}
export interface AccessTemplate {
  code: string; labelFr: string; labelEn: string; descriptionFr: string; descriptionEn: string;
  baseRoleCode: string | null; rules: AccessRule[];
}
export interface AccessRoleAssignment {
  roleCode: string; primary: boolean; effectiveFrom: string | null; effectiveTo: string | null; reason: string;
}
export interface AccessRoleAssignmentMutation {
  expectedPolicyVersion: number; reason: string; assignments: AccessRoleAssignment[]; confirmHighRisk: boolean;
}
export interface AccessCapability {
  actionCode: string; labelFr: string; labelEn: string; effect: string; scopeMode: string;
  source: string; requiresContext: boolean; riskLevel: string;
}
export interface AccessCapabilities {
  policyVersion: number; parcoursScopeMode: string; allowedParcours: string[]; actions: AccessCapability[];
}
export interface AccessDecisionRequest {
  actionCode: string; academicSessionId?: string | null; effectiveDate?: string | null; parcours?: string | null;
  classId?: string | null; subjectCode?: string | null; studentId?: string | null;
  timetableOccurrenceId?: string | null; documentId?: string | null; periodKey?: string | null; level?: string | null;
}
export interface AccessDecision {
  allowed: boolean; actionCode: string; denialCode: string | null; messageFr: string;
  messageEn: string; winningRuleSource: string | null; matchedScope: string | null;
  policyVersion: number; repairHint: string | null;
}
export interface AccessAudit {
  id: string; actorUserId: string | null; targetRoleCode: string | null; targetUserId: string | null;
  mutationType: string; reason: string; correlationId: string | null; occurredAt: string;
}

@Injectable({ providedIn: 'root' })
export class AccessControlApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/access`;

  catalog(): Observable<AccessActionGroup[]> { return this.http.get<AccessActionGroup[]>(`${this.base}/catalog`); }
  roles(): Observable<AccessRole[]> { return this.http.get<AccessRole[]>(`${this.base}/roles`); }
  role(roleCode: string): Observable<AccessRoleWorkspace> { return this.http.get<AccessRoleWorkspace>(`${this.base}/roles/${encodeURIComponent(roleCode)}`); }
  previewRole(roleCode: string, body: AccessMutation): Observable<AccessPolicyPreview> { return this.http.post<AccessPolicyPreview>(`${this.base}/roles/${encodeURIComponent(roleCode)}/preview`, body); }
  updateRole(roleCode: string, body: AccessMutation): Observable<AccessRoleWorkspace> { return this.http.put<AccessRoleWorkspace>(`${this.base}/roles/${encodeURIComponent(roleCode)}`, body); }
  templates(): Observable<AccessTemplate[]> { return this.http.get<AccessTemplate[]>(`${this.base}/templates`); }
  previewTemplate(roleCode: string, templateCode: string): Observable<AccessPolicyPreview> { return this.http.post<AccessPolicyPreview>(`${this.base}/roles/${encodeURIComponent(roleCode)}/template-preview/${encodeURIComponent(templateCode)}`, {}); }
  applyTemplate(roleCode: string, templateCode: string, expectedPolicyVersion: number, reason: string, confirmHighRisk: boolean): Observable<AccessRoleWorkspace> {
    return this.http.post<AccessRoleWorkspace>(`${this.base}/roles/${encodeURIComponent(roleCode)}/apply-template/${encodeURIComponent(templateCode)}`, { expectedPolicyVersion, reason, confirmHighRisk });
  }
  users(search = ''): Observable<AccessUser[]> { return this.http.get<AccessUser[]>(`${this.base}/users`, { params: { search } }); }
  user(userId: string): Observable<AccessUserWorkspace> { return this.http.get<AccessUserWorkspace>(`${this.base}/users/${userId}`); }
  previewUser(userId: string, body: AccessMutation): Observable<AccessPolicyPreview> { return this.http.post<AccessPolicyPreview>(`${this.base}/users/${userId}/preview`, body); }
  updateUser(userId: string, body: AccessMutation): Observable<AccessUserWorkspace> { return this.http.put<AccessUserWorkspace>(`${this.base}/users/${userId}`, body); }
  updateUserRoles(userId: string, body: AccessRoleAssignmentMutation): Observable<AccessUserWorkspace> { return this.http.put<AccessUserWorkspace>(`${this.base}/users/${userId}/roles`, body); }
  capabilities(): Observable<AccessCapabilities> { return this.http.get<AccessCapabilities>(`${this.base}/me/capabilities`); }
  decision(body: AccessDecisionRequest): Observable<AccessDecision> { return this.http.post<AccessDecision>(`${this.base}/me/decision`, body); }
  audit(limit = 100): Observable<AccessAudit[]> { return this.http.get<AccessAudit[]>(`${this.base}/audit`, { params: { limit } }); }
}
