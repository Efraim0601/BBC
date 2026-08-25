import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { describe, expect, it } from 'vitest';
import { AcademicApi } from './academic.api';
import { environment } from '../../../environments/environment';

describe('AcademicApi access-control surfaces', () => {
  it('loads the filtered teacher scope with the session and period context', () => {
    TestBed.configureTestingModule({ providers: [AcademicApi, provideHttpClient(), provideHttpClientTesting()] });
    const api = TestBed.inject(AcademicApi);
    const http = TestBed.inject(HttpTestingController);

    api.academicMyScope('session-1', 'period-1').subscribe();
    const request = http.expectOne((item) => item.url === `${environment.apiUrl}/academic/me/scope`);
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('sessionId')).toBe('session-1');
    expect(request.request.params.get('periodId')).toBe('period-1');
    request.flush({ academicSessionId: 'session-1', reportingPeriodId: 'period-1', subjects: [], classOverviews: [] });
    http.verify();
  });

  it('previews and creates a scoped, dated delegation through separate calls', () => {
    TestBed.configureTestingModule({ providers: [AcademicApi, provideHttpClient(), provideHttpClientTesting()] });
    const api = TestBed.inject(AcademicApi);
    const http = TestBed.inject(HttpTestingController);
    const body = {
      academicSessionId: 'session-1', employeeId: 'employee-1', classId: 'class-1',
      subjectCode: 'MATH', subjectId: 'subject-1', capabilityCode: 'SUBJECT_GRADE_EDIT',
      effectiveFrom: '2026-09-01', effectiveTo: '2026-10-15', reason: 'Dated substitution', source: 'MANUAL',
    };

    api.previewAcademicDelegation(body).subscribe();
    const preview = http.expectOne(`${environment.apiUrl}/academic-access/delegations/preview`);
    expect(preview.request.method).toBe('POST');
    expect(preview.request.body.effectiveTo).toBe('2026-10-15');
    preview.flush({ blockers: [], warnings: [], fingerprint: 'impact-fingerprint' });

    api.createAcademicDelegation(body).subscribe();
    const create = http.expectOne(`${environment.apiUrl}/academic-access/delegations`);
    expect(create.request.method).toBe('POST');
    expect(create.request.body.capabilityCode).toBe('SUBJECT_GRADE_EDIT');
    create.flush({});
    http.verify();
  });

  it('downloads an official report through the matching bulletin scope', () => {
    TestBed.configureTestingModule({ providers: [AcademicApi, provideHttpClient(), provideHttpClientTesting()] });
    const api = TestBed.inject(AcademicApi);
    const http = TestBed.inject(HttpTestingController);

    api.reportCardDocumentContent('snapshot-1', 'document-1').subscribe();
    const request = http.expectOne(
      `${environment.apiUrl}/academic/bulletin-snapshots/snapshot-1/documents/document-1/content`,
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['pdf'], { type: 'application/pdf' }));
    http.verify();
  });
});
