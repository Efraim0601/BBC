import { APP_BASE_HREF } from '@angular/common';
import { signal, WritableSignal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { SchoolService } from '../../core/school.service';
import { PaymentView } from '../../core/models';
import { StudentApi } from '../students/students.api';
import { FinanceApi } from './finance.api';
import { FinanceComponent } from './finance';
import { TreasuryApi } from './treasury.api';

describe('finance base-path navigation', () => {
  afterEach(() => TestBed.resetTestingModule());

  it.each([
    { label: 'cashier', actions: ['TREASURY_ACCOUNT_VIEW', 'FINANCE_STUDENT_ACCOUNT_VIEW'], links: ['/app/finance/treasury', '/app/finance/student-accounts'] },
    { label: 'overview-only principal', actions: [], links: [] },
    { label: 'student-account viewer', actions: ['FINANCE_STUDENT_ACCOUNT_VIEW'], links: ['/app/finance/student-accounts'] },
  ])('shows only authorized links under the production /app base path for $label', ({actions, links: expected}) => {
    TestBed.configureTestingModule({
      imports: [FinanceComponent],
      providers: [
        provideRouter([]),
        { provide: APP_BASE_HREF, useValue: '/app/' },
        { provide: FinanceApi, useValue: {
          summary: vi.fn(() => of({ totalRevenue30d: 0, totalExpense30d: 0, balance30d: 0, paymentsCount: 0, revenueSeries: [] })),
          payments: vi.fn(() => of([])), context: vi.fn(() => of({ sessions: [], classes: [] })), channels: vi.fn(() => of([])),
        } },
        { provide: TreasuryApi, useValue: { accounts: vi.fn(() => of([])) } },
        { provide: StudentApi, useValue: {} },
        { provide: AuthService, useValue: { can: vi.fn(() => false), canAction: (action: string) => actions.includes(action) } },
        { provide: I18nService, useValue: { lang: signal('en'), t: (key: string) => key } },
        { provide: SchoolService, useValue: { ensureLoaded: vi.fn(), profile: signal({ name: 'BBC', academicYear: 'Année scolaire 2026-2027' }), location: () => 'Maroua' } },
      ],
    });
    const fixture = TestBed.createComponent(FinanceComponent);
    fixture.detectChanges();

    const links = [...fixture.nativeElement.querySelectorAll('a')].map((link: HTMLAnchorElement) => link.getAttribute('href'));
    expect(links).toEqual(expected);
    expect(links).not.toContain('/finance/treasury');

    const state = fixture.componentInstance as unknown as { receipt: WritableSignal<PaymentView | null> };
    state.receipt.set({ id: 'qa', receiptNo: 'QA-001', studentId: 'qa-pupil', studentName: 'QA Pupil', matricule: 'QA', className: '6ème A', amount: 1000, method: 'CASH', methodLabelFr: 'Espèces', methodLabelEn: 'Cash', reference: null, tranche: null, paidOn: '2026-09-07' });
    fixture.detectChanges();
    const receiptText = fixture.nativeElement.querySelector('[role="dialog"]').textContent;
    expect(receiptText).toContain('Année scolaire 2026-2027');
    expect(receiptText).not.toContain('Academic year Année scolaire');
    expect(receiptText).not.toContain('Année scolaire Année scolaire');
  });
});
