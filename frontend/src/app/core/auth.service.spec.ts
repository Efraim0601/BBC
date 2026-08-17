import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { describe, expect, it, afterEach, vi } from 'vitest';
import { AuthService } from './auth.service';

describe('AuthService capability session boundary', () => {
  afterEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  function setup() {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [{ provide: Router, useValue: { navigate: vi.fn() } }],
    });
    return {
      auth: TestBed.inject(AuthService),
      http: TestBed.inject(HttpTestingController),
    };
  }

  const token = (username: string) => ({
    accessToken: `${username}-access`,
    refreshToken: `${username}-refresh`,
    expiresInMs: 60_000,
    user: {
      id: `${username}-id`, username, role: username,
      displayName: username, permissions: {},
    },
  });

  const capabilities = (effect: string) => ({
    policyVersion: 'V2', parcoursScopeMode: 'NONE', allowedParcours: [],
    actions: [{ actionCode: 'CLASS_MANAGE', labelFr: 'Classes', labelEn: 'Classes', effect, scopeMode: null, source: 'TEST', requiresContext: false, riskLevel: 'HIGH' }],
  });

  it('ignores an old capability response after logout and a new login', () => {
    const { auth, http } = setup();
    auth.login('admin', 'admin').subscribe();
    const adminLogin = http.expectOne('/api/auth/login');
    adminLogin.flush(token('admin'));
    const stale = http.expectOne('/api/access/me/capabilities');

    auth.logout();
    auth.login('teacher', 'teacher').subscribe();
    const teacherLogin = http.expectOne('/api/auth/login');
    teacherLogin.flush(token('teacher'));
    const current = http.expectOne('/api/access/me/capabilities');

    stale.flush(capabilities('ALLOW'));
    expect(auth.capabilities()).toBeNull();
    current.flush(capabilities('DENY'));
    expect(auth.actionState('CLASS_MANAGE')).toBe('DENY');
    http.verify();
  });
});
