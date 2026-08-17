import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { AccessControlApi } from './access-control.api';
import { AccessControlWorkspaceComponent } from './access-control-workspace';

describe('access-control workspace preview and confirmation boundaries', () => {
  afterEach(() => TestBed.resetTestingModule());

  function create() {
    const action = {
      code: 'PERMISSION_MANAGE', module: 'settings', groupCode: 'security',
      labelFr: 'Droits', labelEn: 'Permissions', descriptionFr: 'Droits',
      descriptionEn: 'Permissions', riskLevel: 'CRITICAL', requiredLevel: 'ADMIN',
      scopeType: 'NONE', defaultReadAction: false, displayOrder: 1,
    };
    const group = { code: 'security', labelFr: 'Sécurité', labelEn: 'Security', actions: [action] };
    const role = { code: 'principal', labelFr: 'Direction', labelEn: 'Principal', builtin: true };
    const workspace = {
      roleCode: 'principal', labelFr: 'Direction', labelEn: 'Principal', builtin: true,
      policyVersion: 42, groups: [group], rules: [],
    };
    const template = {
      code: 'principal_oversight', labelFr: 'Supervision', labelEn: 'Oversight',
      descriptionFr: 'Supervision', descriptionEn: 'Oversight', baseRoleCode: 'principal', rules: [],
    };
    const preview = {
      subjectType: 'ROLE', subjectCode: 'principal', currentPolicyVersion: 42,
      changes: [{ actionCode: 'PERMISSION_MANAGE', beforeEffect: 'INHERIT', afterEffect: 'ALLOW', beforeScopeMode: 'NONE', afterScopeMode: 'NONE', riskLevel: 'CRITICAL', changeType: 'ADDITION' }],
      warnings: [{ code: 'HIGH_RISK', severity: 'CRITICAL', messageFr: 'Risque', messageEn: 'Risk' }],
      requiresConfirmation: true, affectedUsers: [], preservedUserExceptions: [],
    };
    const api = {
      catalog: vi.fn(() => of([group])),
      roles: vi.fn(() => of([role])),
      role: vi.fn(() => of(workspace)),
      templates: vi.fn(() => of([template])),
      users: vi.fn(() => of([])),
      audit: vi.fn(() => of([])),
      previewTemplate: vi.fn(() => of(preview)),
      applyTemplate: vi.fn(() => of(workspace)),
      previewRole: vi.fn(() => of(preview)),
      updateRole: vi.fn(() => of(workspace)),
      user: vi.fn(() => of({ user: { id: 'user-1', username: 'user', displayName: 'User', roleCode: 'teacher', active: true, roles: ['teacher'] }, policyVersion: 42, overrides: [], effectiveActions: [] })),
      previewUser: vi.fn(() => of(preview)),
      updateUser: vi.fn(() => of({})),
      updateUserRoles: vi.fn(() => of({})),
    };
    const auth = { canAction: vi.fn(() => true) };

    TestBed.configureTestingModule({
      imports: [AccessControlWorkspaceComponent],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: AccessControlApi, useValue: api },
        { provide: I18nService, useValue: { lang: signal('en') } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => null } } } },
      ],
    });
    TestBed.overrideComponent(AccessControlWorkspaceComponent, { set: { template: '' } });
    const fixture = TestBed.createComponent(AccessControlWorkspaceComponent);
    fixture.detectChanges();
    return { fixture, api, preview, workspace };
  }

  it('stages a safe-template preview through the non-mutating API only', () => {
    const { fixture, api, preview } = create();
    const component = fixture.componentInstance as any;

    component.selectedTemplate.set('principal_oversight');
    component.previewSelectedTemplate();

    expect(api.previewTemplate).toHaveBeenCalledWith('principal', 'principal_oversight');
    expect(component.templateStaged()).toBe(true);
    expect(component.preview()).toEqual(preview);
    expect(api.applyTemplate).not.toHaveBeenCalled();
    expect(api.updateRole).not.toHaveBeenCalled();
  });

  it('requires a reason and explicit high-risk confirmation before applying a preview', () => {
    const { fixture, api, preview } = create();
    const component = fixture.componentInstance as any;
    component.templateStaged.set(true);
    component.selectedTemplate.set('principal_oversight');
    component.preview.set(preview);

    component.applyStagedTemplate();
    expect(api.applyTemplate).not.toHaveBeenCalled();

    component.reason.set('Gate 14 focused confirmation regression');
    component.applyStagedTemplate();
    expect(api.applyTemplate).not.toHaveBeenCalled();

    component.confirmHighRisk.set(true);
    component.applyStagedTemplate();
    expect(api.applyTemplate).toHaveBeenCalledWith(
      'principal', 'principal_oversight', 42,
      'Gate 14 focused confirmation regression', true,
    );
  });

  it('surfaces a stale-policy preview failure and clears the busy state', () => {
    const { fixture, api } = create();
    const component = fixture.componentInstance as any;
    api.previewRole.mockReturnValue(throwError(() => ({ error: { message: 'POLICY_VERSION_CONFLICT' } })));
    component.reason.set('Gate 14 stale preview regression');

    component.previewChanges();

    expect(api.previewRole).toHaveBeenCalledWith('principal', expect.objectContaining({ expectedPolicyVersion: 42 }));
    expect(component.busy()).toBe(false);
    expect(component.message()).toEqual({ ok: false, text: 'POLICY_VERSION_CONFLICT' });
  });
});
