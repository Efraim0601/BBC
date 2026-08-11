import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { describe, expect, it } from 'vitest';
import { environment } from '../../../environments/environment';
import { AcademicApi } from './academic.api';

describe('AcademicApi batch diagnostics contract', () => {
  it('previews without creating a job and sends the published-only scope', () => {
    TestBed.configureTestingModule({ providers: [AcademicApi, provideHttpClient(), provideHttpClientTesting()] });
    const api = TestBed.inject(AcademicApi);
    const http = TestBed.inject(HttpTestingController);
    api.previewBulletinBatch({ classId: 'class', reportingPeriodId: 'period', locale: 'en' }).subscribe();
    const request = http.expectOne(`${environment.apiUrl}/academic/bulletin-batch-jobs/preview`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ classId: 'class', reportingPeriodId: 'period', locale: 'en' });
    request.flush({ policy: 'PUBLISHED_ONLY', rows: [], readyStudents: 0, blockedStudents: 0, totalStudents: 0 });
    http.verify();
  });

  it('keeps partial creation, blocker recheck, technical retry and diagnostic download separate', () => {
    TestBed.configureTestingModule({ providers: [AcademicApi, provideHttpClient(), provideHttpClientTesting()] });
    const api = TestBed.inject(AcademicApi);
    const http = TestBed.inject(HttpTestingController);

    api.createBulletinBatchJob({ classId: 'class', reportingPeriodId: 'period', locale: 'en', scopeFingerprint: 'fingerprint', includeReadyStudentsWhenPartiallyBlocked: true }).subscribe();
    const create = http.expectOne(`${environment.apiUrl}/academic/bulletin-batch-jobs`);
    expect(create.request.body.includeReadyStudentsWhenPartiallyBlocked).toBe(true);
    expect(create.request.body.scopeFingerprint).toBe('fingerprint');
    create.flush({});

    api.recheckBlockedBatchItems('job').subscribe();
    const recheck = http.expectOne(`${environment.apiUrl}/academic/bulletin-batch-jobs/job/recheck-blocked`);
    expect(recheck.request.method).toBe('POST');
    recheck.flush({});

    api.retryBatchErrors('job').subscribe();
    const retry = http.expectOne(`${environment.apiUrl}/academic/bulletin-batch-jobs/job/retry-errors`);
    expect(retry.request.method).toBe('POST');
    retry.flush({});

    api.downloadBulletinBatchDiagnostic('job').subscribe();
    const diagnostic = http.expectOne(`${environment.apiUrl}/academic/bulletin-batch-jobs/job/diagnostic`);
    expect(diagnostic.request.method).toBe('GET');
    diagnostic.flush(new Blob(['csv'], { type: 'text/csv' }));
    http.verify();
  });
});
