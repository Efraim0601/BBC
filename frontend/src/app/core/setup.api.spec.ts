import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { describe, expect, it } from 'vitest';
import { SetupApi } from './setup.api';
import { environment } from '../../environments/environment';

describe('SetupApi curriculum reuse', () => {
  it('keeps preview read-only and sends selected edits with an idempotency key on apply', () => {
    TestBed.configureTestingModule({ providers: [SetupApi, provideHttpClient(), provideHttpClientTesting()] });
    const api = TestBed.inject(SetupApi);
    const http = TestBed.inject(HttpTestingController);
    const body = {
      sourceSessionId: 'source', targetSessionId: 'target', classIds: ['class'],
      allMatchingClasses: false, includeGroups: true, includeTeachers: true,
      mergeMode: 'UPDATE_SELECTED', selectedKeys: ['SUBJECT:class:FRANC'],
      edits: [{ key: 'SUBJECT:class:FRANC', field: 'coefficient', value: '4' }],
    };

    api.previewCurriculumCopy(body).subscribe();
    const preview = http.expectOne(`${environment.apiUrl}/setup/curriculum/copy/preview`);
    expect(preview.request.method).toBe('POST');
    expect(preview.request.body.edits[0].field).toBe('coefficient');
    expect(preview.request.headers.has('Idempotency-Key')).toBe(false);
    preview.flush({});

    api.applyCurriculumCopy({ ...body, reason: 'Reuse approved curriculum', previewFingerprint: 'fingerprint' }, 'curriculum-key').subscribe();
    const apply = http.expectOne(`${environment.apiUrl}/setup/curriculum/copy/apply`);
    expect(apply.request.method).toBe('POST');
    expect(apply.request.headers.get('Idempotency-Key')).toBe('curriculum-key');
    apply.flush({});
    http.verify();
  });
});
