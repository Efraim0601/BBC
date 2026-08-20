import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { StudentApi } from '../students/students.api';
import { FinanceApi } from './finance.api';
import { FinanceComponent } from './finance';

describe('finance class-filter scope boundary', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('loads class options from the finance-scoped context, not academic setup', () => {
    const api = {
      summary: vi.fn(() => of({ totalRevenue30d: 0, totalExpense30d: 0, balance30d: 0, paymentsCount: 0, revenueSeries: [] })),
      payments: vi.fn(() => of([])),
      context: vi.fn(() => of({ sessions: [], classes: [{ id: 'class-1', code: 'section-1', name: 'PRI-FR-CE1-A', level: 'primary', subsystem: 'FR' }] })),
      channels: vi.fn(() => of([])),
      situation: vi.fn(() => of([])),
      expenses: vi.fn(() => of([])),
      feeConfig: vi.fn(() => of([])),
    };
    TestBed.configureTestingModule({
      imports: [FinanceComponent],
      providers: [
        { provide: FinanceApi, useValue: api },
        { provide: StudentApi, useValue: {} },
        { provide: AuthService, useValue: { can: vi.fn(() => false) } },
        { provide: I18nService, useValue: { lang: signal('fr') } },
      ],
    });
    TestBed.overrideComponent(FinanceComponent, { set: { template: '' } });
    const fixture: ComponentFixture<FinanceComponent> = TestBed.createComponent(FinanceComponent);
    fixture.detectChanges();

    expect(api.context).toHaveBeenCalledTimes(1);
    expect((fixture.componentInstance as any).setupClasses()[0]).toMatchObject({
      id: 'class-1', name: 'PRI-FR-CE1-A', sectionLabel: 'primary / FR',
    });
  });
});
