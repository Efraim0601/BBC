import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it, vi, afterEach } from 'vitest';
import { AuthService } from './auth.service';
import { actionGuard, contextualActionGuard, parentGuard, scopeGuard } from './guards';
import { ScopeService } from './scope.service';
import { Router, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';

describe('contextual action route guards', () => {
  afterEach(() => TestBed.resetTestingModule());

  function evaluate(effect: string) {
    const tree = { redirect: '/apps' };
    const auth = {
      isLoggedIn: vi.fn(() => true),
      capabilities: signal({ actions: [] }),
      actionState: vi.fn(() => effect),
      loadCapabilities: vi.fn(),
    };
    const router = { createUrlTree: vi.fn(() => tree) };
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });
    const result = TestBed.runInInjectionContext(() =>
      contextualActionGuard('HR_VIEW')({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );
    return { result, tree, router };
  }

  it('denies ordinary profiles when the server action is DENY', () => {
    const { result, tree } = evaluate('DENY');
    expect(result).toBe(tree);
  });

  it('opens the staff route for an ALLOW decision', () => {
    expect(evaluate('ALLOW').result).toBe(true);
  });

  it('opens the staff route for an explicitly contextual decision', () => {
    expect(evaluate('CONTEXT_REQUIRED').result).toBe(true);
  });

  it('keeps profile creation behind the create action, not directory read', () => {
    const tree = { redirect: '/apps' };
    const auth = {
      isLoggedIn: vi.fn(() => true),
      capabilities: signal({ actions: [{ actionCode: 'STUDENT_PROFILE_CREATE', effect: 'DENY' }] }),
      actionState: vi.fn(() => 'DENY'),
      loadCapabilities: vi.fn(),
    };
    const router = { createUrlTree: vi.fn(() => tree) };
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });

    const result = TestBed.runInInjectionContext(() =>
      contextualActionGuard('STUDENT_PROFILE_CREATE')({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );

    expect(result).toBe(tree);
  });

  it('keeps family import behind the import action', () => {
    const tree = { redirect: '/apps' };
    const auth = {
      isLoggedIn: vi.fn(() => true),
      capabilities: signal({ actions: [{ actionCode: 'STUDENT_IMPORT', effect: 'ALLOW' }] }),
      actionState: vi.fn(() => 'ALLOW'),
      loadCapabilities: vi.fn(),
    };
    const router = { createUrlTree: vi.fn(() => tree) };
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });

    const result = TestBed.runInInjectionContext(() =>
      contextualActionGuard('STUDENT_IMPORT')({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );

    expect(result).toBe(true);
  });
});

describe('action route guards', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('keeps the events route aligned with the server EVENTS_VIEW decision', () => {
    const tree = { redirect: '/apps' };
    const auth = {
      isLoggedIn: vi.fn(() => true),
      capabilities: signal({ actions: [{ actionCode: 'EVENTS_VIEW', effect: 'DENY' }] }),
      actionState: vi.fn(() => 'DENY'),
      canAction: vi.fn(() => false),
      loadCapabilities: vi.fn(),
    };
    const router = { createUrlTree: vi.fn(() => tree) };
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });

    const result = TestBed.runInInjectionContext(() =>
      actionGuard('EVENTS_VIEW')({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );

    expect(result).toBe(tree);
    expect(auth.canAction).toHaveBeenCalledWith('EVENTS_VIEW');
  });
});

describe('parent portal route guard', () => {
  afterEach(() => TestBed.resetTestingModule());

  function evaluate(role: string | null, loggedIn = true) {
    const tree = { redirect: role === null && !loggedIn ? '/login' : '/apps' };
    const auth = {
      isLoggedIn: vi.fn(() => loggedIn),
      user: signal(role === null ? null : { role }),
    };
    const router = { createUrlTree: vi.fn(() => tree) };
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });
    const result = TestBed.runInInjectionContext(() =>
      parentGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );
    return { result, tree, router };
  }

  it('opens the parent portal only for a parent account', () => {
    expect(evaluate('parent').result).toBe(true);
  });

  it('redirects ordinary staff roles away from the parent portal', () => {
    const { result, tree } = evaluate('accountant');
    expect(result).toBe(tree);
  });

  it('redirects an unauthenticated direct route to login', () => {
    const { result, tree } = evaluate(null, false);
    expect(result).toBe(tree);
  });
});

describe('parcours route guard', () => {
  afterEach(() => TestBed.resetTestingModule());

  function evaluate(user: any, selected: any, allMode = false) {
    const tree = { redirect: '/parcours' };
    const auth = { user: signal(user) };
    const scope = {
      scope: signal(selected),
      allMode: signal(allMode),
      resolved: signal(selected != null || allMode),
      clear: vi.fn(),
    };
    const router = { createUrlTree: vi.fn(() => tree) };
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: ScopeService, useValue: scope },
        { provide: Router, useValue: router },
      ],
    });
    const result = TestBed.runInInjectionContext(() =>
      scopeGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );
    return { result, tree, scope };
  }

  const restrictedPrincipal = {
    role: 'principal', parcoursScopeMode: 'EXPLICIT',
    allowedParcours: [{ level: 'primary', subsystem: 'FR' }],
  };

  it('accepts an assigned principal parcours', () => {
    expect(evaluate(restrictedPrincipal, { level: 'primary', subsystem: 'FR' }).result).toBe(true);
  });

  it('clears stale all-parcours state inherited from an administrator', () => {
    const { result, tree, scope } = evaluate(restrictedPrincipal, null, true);
    expect(result).toBe(tree);
    expect(scope.clear).toHaveBeenCalled();
  });

  it('clears a selected parcours that is no longer assigned', () => {
    const { result, tree, scope } = evaluate(
      restrictedPrincipal, { level: 'secondary', subsystem: 'FR' }, false,
    );
    expect(result).toBe(tree);
    expect(scope.clear).toHaveBeenCalled();
  });
});
