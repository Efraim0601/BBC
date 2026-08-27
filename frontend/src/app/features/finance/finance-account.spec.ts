import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { I18nService } from '../../core/i18n.service';
import { FinanceAccountApi, StudentFinanceAccount } from './finance-account.api';
import { FinanceAccountComponent } from './finance-account';

describe('student finance accounts', () => {
  afterEach(() => TestBed.resetTestingModule());

  function create(apiOverrides: Record<string, unknown> = {}): { fixture: ComponentFixture<FinanceAccountComponent>; api: any } {
    const api = {
      context: vi.fn(() => of({ classes: [{ id: 'class-1', name: 'CE1 A', level: 'primary', subsystem: 'FR', studentCount: 3 }] })),
      search: vi.fn(() => of([
        { studentId: 'due', studentName: 'Due Student', matricule: 'BBC-1', enrollmentId: 'e-1', academicSessionId: 's-1', className: 'CE1 A', enrolledOn: '2026-09-01', exitedOn: null, billedMinor: 100000, paidMinor: 50000, outstandingMinor: 50000, creditMinor: 0, paymentCount: 1 },
        { studentId: 'paid', studentName: 'Paid Student', matricule: 'BBC-2', enrollmentId: 'e-2', academicSessionId: 's-1', className: 'CE1 A', enrolledOn: '2026-09-01', exitedOn: null, billedMinor: 100000, paidMinor: 100000, outstandingMinor: 0, creditMinor: 0, paymentCount: 2 },
        { studentId: 'none', studentName: 'No Fee Student', matricule: 'BBC-3', enrollmentId: 'e-3', academicSessionId: 's-1', className: 'CE1 A', enrolledOn: '2026-09-01', exitedOn: null, billedMinor: 0, paidMinor: 0, outstandingMinor: 0, creditMinor: 0, paymentCount: 0 },
      ])),
      student: vi.fn(), consolidatedReceipt: vi.fn(), consolidatedReceiptPdf: vi.fn(),
      ...apiOverrides,
    };
    TestBed.configureTestingModule({
      imports: [FinanceAccountComponent],
      providers: [
        provideRouter([]),
        { provide: FinanceAccountApi, useValue: api },
        { provide: I18nService, useValue: { lang: signal('en') } },
      ],
    });
    const fixture = TestBed.createComponent(FinanceAccountComponent);
    fixture.detectChanges();
    return { fixture, api };
  }

  it('loads a selected class and explains each balance instead of showing an unlabeled zero', () => {
    const { fixture, api } = create();
    (fixture.componentInstance as any).classChanged('class-1');
    fixture.detectChanges();

    expect(api.search).toHaveBeenCalledWith('', 'class-1');
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Balance due');
    expect(text).toContain('50,000 XAF');
    expect(text).toContain('Paid in full');
    expect(text).toContain('No fees configured');
  });

  it('prepares a printable consolidated receipt with student identity and separate PDF action', () => {
    const account: StudentFinanceAccount = { studentId: 'paid', studentName: 'Paid Student', matricule: 'BBC-2', className: 'CE1 A', sessionLabel: '2026-2027', billedMinor: 100000, paidMinor: 100000, outstandingMinor: 0, creditMinor: 0, currency: 'XAF', snapshotHash: 'abc123456789012345', payments: [] };
    const receipt = { ...account, receiptNumber: 'CR/2026/000001', issueDate: '2026-08-27', status: 'ISSUED', generatedDocumentId: 'doc-1', generatedDocumentNumber: 'CR/2026/000001', generatedDocumentStatus: 'ISSUED' };
    const { fixture, api } = create({ consolidatedReceipt: vi.fn(() => of(receipt)) });
    (fixture.componentInstance as any).prepareConsolidated(account);
    fixture.detectChanges();

    expect(api.consolidatedReceipt).toHaveBeenCalledWith('paid');
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Paid Student');
    expect(text).toContain('BBC-2');
    expect(text).toContain('Download PDF');
    expect(text).toContain('Print');
  });
});
