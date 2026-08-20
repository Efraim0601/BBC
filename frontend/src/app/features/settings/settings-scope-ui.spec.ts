import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { SettingsApi } from './settings.api';
import { SettingsComponent } from './settings';

describe('settings academic-setup scope boundary', () => {
  afterEach(() => TestBed.resetTestingModule());

  function create(actionState: (action: string) => string): ComponentFixture<SettingsComponent> {
    const auth = {
      user: signal({ role: 'principal', displayName: 'Direction A' }),
      actionState: vi.fn(actionState),
      canAction: vi.fn((action: string) => actionState(action) === 'ALLOW'),
    };
    const api = {
      getMatrix: vi.fn(() => of({ modules: [], roles: [], matrix: {} })),
      getMail: vi.fn(() => of({ enabled: false, host: null, port: 587, username: null, passwordSet: false, fromAddress: null, fromName: null, useTls: true, notifyOnUserCreate: true })),
      getSchool: vi.fn(() => of({ code: 'BBC-E2E', name: 'School', motto: null, city: 'Yaoundé', country: 'Cameroun', address: '12 Test Avenue, Bastos', phone: null, email: null, website: null, currency: 'XAF', authority: null, academicYear: '2026-2027', schoolStartTime: '07:30', schoolEndTime: '15:30' })),
      listHolidays: vi.fn(() => of([])),
      listCatalog: vi.fn(() => of([])),
    };

    TestBed.configureTestingModule({
      imports: [SettingsComponent],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: SettingsApi, useValue: api },
        { provide: I18nService, useValue: { lang: signal('fr'), t: vi.fn((key: string) => key) } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => null } } } },
        { provide: Router, useValue: { navigate: vi.fn() } },
      ],
    });
    TestBed.overrideComponent(SettingsComponent, { set: { template: '' } });
    const fixture = TestBed.createComponent(SettingsComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('does not expose or instantiate academic setup for a read-only Direction session', () => {
    const fixture = create(() => 'DENY');
    expect((fixture.componentInstance as any).canViewAcademicSetup()).toBe(false);
  });

  it('hides administrator-only permissions and roles tabs from a principal', () => {
    const fixture = create(() => 'DENY');
    const ids = (fixture.componentInstance as any).tabs().map((tab: { id: string }) => tab.id);
    expect(ids).not.toContain('perms');
    expect(ids).not.toContain('roles');
  });

  it('exposes permissions and roles tabs to an administrator', () => {
    const fixture = create((action) => action === 'PERMISSION_VIEW' ? 'ALLOW' : 'DENY');
    const ids = (fixture.componentInstance as any).tabs().map((tab: { id: string }) => tab.id);
    expect(ids).toContain('perms');
    expect(ids).toContain('roles');
  });

  it('keeps setup available for an allowed setup action', () => {
    const allowed = create((action) => action === 'CLASS_MANAGE' ? 'ALLOW' : 'DENY');
    expect((allowed.componentInstance as any).canViewAcademicSetup()).toBe(true);
  });

  it('keeps setup available for a contextual setup action', () => {
    const contextual = create((action) => action === 'CURRICULUM_MANAGE' ? 'CONTEXT_REQUIRED' : 'DENY');
    expect((contextual.componentInstance as any).canViewAcademicSetup()).toBe(true);
  });

  it('binds the persisted street address into the editable school profile draft', () => {
    const fixture = create(() => 'DENY');
    expect((fixture.componentInstance as any).schoolDraft.address).toBe('12 Test Avenue, Bastos');
  });
});
