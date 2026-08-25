import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { SchoolService } from '../../core/school.service';
import { ChildView, ParentApi } from './parent.api';
import { ParentComponent } from './parent';

describe('parent optional-read scope boundary', () => {
  afterEach(() => TestBed.resetTestingModule());

  const child = (financeVisible: boolean): ChildView => ({
    studentId: 'student-1', matricule: 'BBC-1', name: 'Child', className: 'Class A',
    balance: 0, feeStatus: null, attendanceRate: 0, financeVisible, attendanceVisible: true,
  });

  function create(overrides: Partial<Record<keyof ParentApi, unknown>> = {}): ComponentFixture<ParentComponent> {
    const api = {
      paymentChannels: vi.fn(() => of([])),
      children: vi.fn(() => of([])),
      mySuggestions: vi.fn(() => of([])),
      sharedResources: vi.fn(() => of([])),
      programmeClasses: vi.fn(() => of([])),
      latestPublishedBulletin: vi.fn(() => throwError(() => new Error('404 NOT_FOUND'))),
      fees: vi.fn(() => of(null)),
      resources: vi.fn(() => of(null)),
      journey: vi.fn(() => of([])),
      attendance: vi.fn(() => of(null)),
      discipline: vi.fn(() => of([])),
      health: vi.fn(() => of(null)),
      events: vi.fn(() => of([])),
      messages: vi.fn(() => of([])),
      ...overrides,
    } as unknown as ParentApi;

    TestBed.configureTestingModule({
      imports: [ParentComponent],
      providers: [
        { provide: ParentApi, useValue: api },
        { provide: AuthService, useValue: { user: signal({ displayName: 'Parent' }), canAction: vi.fn(() => false) } },
        { provide: I18nService, useValue: { lang: signal('fr') } },
        { provide: SchoolService, useValue: { ensureLoaded: vi.fn(), profile: signal(null), location: vi.fn(() => '') } },
      ],
    });
    TestBed.overrideComponent(ParentComponent, { set: { template: '' } });
    const fixture = TestBed.createComponent(ParentComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('does not request fees for a child whose finance is not shared', () => {
    const api = {
      fees: vi.fn(() => throwError(() => new Error('403 POLICY_RULE_MISSING'))),
    };
    const fixture = create(api);

    expect(() => (fixture.componentInstance as any).select(child(false))).not.toThrow();
    expect(api.fees).not.toHaveBeenCalled();
  });

  it('contains denied optional child reads instead of surfacing unhandled browser errors', () => {
    const denied = () => throwError(() => new Error('403 POLICY_RULE_MISSING'));
    const api = {
      fees: vi.fn(denied),
      resources: vi.fn(denied),
      journey: vi.fn(denied),
      attendance: vi.fn(denied),
      discipline: vi.fn(denied),
      health: vi.fn(denied),
      events: vi.fn(denied),
      messages: vi.fn(denied),
    };
    const fixture = create(api);

    expect(() => (fixture.componentInstance as any).select(child(true))).not.toThrow();
    expect((fixture.componentInstance as any).statement()).toBeNull();
    expect((fixture.componentInstance as any).health()).toBeNull();
  });

  it('does not probe staff school settings for a parent without SCHOOL_PROFILE_VIEW', () => {
    const ensureLoaded = vi.fn();
    TestBed.overrideProvider(SchoolService, {
      useValue: { ensureLoaded, profile: signal(null), location: vi.fn(() => '') },
    });
    create();
    expect(ensureLoaded).not.toHaveBeenCalled();
  });
});
