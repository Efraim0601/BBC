import { APP_BASE_HREF } from '@angular/common';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { SchoolService } from '../../core/school.service';
import { StudentApi } from '../students/students.api';
import { FinanceApi } from './finance.api';
import { FinanceComponent } from './finance';
import { TreasuryApi } from './treasury.api';

describe('finance base-path navigation', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('keeps internal finance links under the production /app base path', () => {
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
        { provide: AuthService, useValue: { can: vi.fn(() => false) } },
        { provide: I18nService, useValue: { lang: signal('en'), t: (key: string) => key } },
        { provide: SchoolService, useValue: { ensureLoaded: vi.fn(), profile: signal(null), location: () => '' } },
      ],
    });
    const fixture = TestBed.createComponent(FinanceComponent);
    fixture.detectChanges();

    const links = [...fixture.nativeElement.querySelectorAll('a')].map((link: HTMLAnchorElement) => link.getAttribute('href'));
    expect(links).toContain('/app/finance/treasury');
    expect(links).toContain('/app/finance/student-accounts');
    expect(links).not.toContain('/finance/treasury');
  });
});
