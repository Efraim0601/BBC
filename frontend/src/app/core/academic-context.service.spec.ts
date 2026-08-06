import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { AcademicContextService } from './academic-context.service';
import { FoundationApi, AcademicSessionView } from './foundation.api';

const current: AcademicSessionView = {
  id: 's-current', code: '2026-2027', label: 'Session 2026-2027',
  startDate: '2026-09-01', endDate: '2027-07-31', status: 'OPEN', current: true,
  version: 0, gradeEntryOpensAt: null, gradeEntryClosesAt: null,
  bulletinPublishOpensAt: null, bulletinPublishClosesAt: null,
  terms: [{ id: 't1', code: 'T1', label: 'Term 1', sequenceNo: 1,
    startDate: '2026-09-01', endDate: '2026-12-20', gradeEntryOpensAt: null,
    gradeEntryClosesAt: null, bulletinPublishOpensAt: null,
    bulletinPublishClosesAt: null, version: 0 }],
};

describe('AcademicContextService', () => {
  it('selects the current session and first term, then marks historical selection', () => {
    const historical = { ...current, id: 's-old', current: false, status: 'CLOSED' as const, terms: [] };
    TestBed.configureTestingModule({ providers: [
      AcademicContextService,
      { provide: FoundationApi, useValue: { listSessions: vi.fn(() => of([historical, current])) } },
    ] });
    const service = TestBed.inject(AcademicContextService);
    service.load();
    expect(service.sessionId()).toBe('s-current');
    expect(service.termId()).toBe('t1');
    expect(service.historical()).toBe(false);
    service.selectSession('s-old');
    expect(service.historical()).toBe(true);
    expect(service.termId()).toBeNull();
  });
});
