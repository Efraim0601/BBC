import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { FeeTypesApi } from './fee-types.api';
import { FeePlansApi } from './plans.api';
import { FinancePlansComponent } from './finance-plans';

describe('finance plan mutation action boundary', () => {
  afterEach(() => TestBed.resetTestingModule());

  function create(effect: 'ALLOW' | 'DENY'): { fixture: ComponentFixture<FinancePlansComponent>; auth: { canAction: ReturnType<typeof vi.fn> } } {
    const auth = { canAction: vi.fn(() => effect === 'ALLOW') };
    TestBed.configureTestingModule({
      imports: [FinancePlansComponent],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: I18nService, useValue: { lang: signal('fr') } },
        { provide: FeePlansApi, useValue: { context: vi.fn(() => of({ sessions: [], classes: [], plans: [] })), templates: vi.fn(() => of([])) } },
        { provide: FeeTypesApi, useValue: { list: vi.fn(() => of([])) } },
      ],
    });
    TestBed.overrideComponent(FinancePlansComponent, { set: { template: '' } });
    const fixture = TestBed.createComponent(FinancePlansComponent);
    fixture.detectChanges();
    return { fixture, auth };
  }

  it('keeps fee-plan mutations disabled when FEE_PLAN_DRAFT is denied', () => {
    const { fixture, auth } = create('DENY');

    expect((fixture.componentInstance as any).canWrite()).toBe(false);
    expect(auth.canAction).toHaveBeenCalledWith('FEE_PLAN_DRAFT');
  });

  it('allows fee-plan mutations only when FEE_PLAN_DRAFT is allowed', () => {
    const { fixture } = create('ALLOW');

    expect((fixture.componentInstance as any).canWrite()).toBe(true);
  });
});
