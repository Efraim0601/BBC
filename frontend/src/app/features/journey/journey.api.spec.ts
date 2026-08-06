import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { environment } from '../../../environments/environment';
import { JourneyApi } from './journey.api';

describe('JourneyApi promotion workflow', () => {
  function setup() {
    TestBed.configureTestingModule({ providers: [JourneyApi, provideHttpClient(), provideHttpClientTesting()] });
    return { api: TestBed.inject(JourneyApi), http: TestBed.inject(HttpTestingController) };
  }

  it('keeps preview separate from irreversible batch commit', () => {
    const { api, http } = setup();
    api.previewPromotion({ sourceSessionId: 's1', targetSessionId: 's2', name: 'Promotion', idempotencyKey: 'key-1' }).subscribe();
    const preview = http.expectOne(`${environment.apiUrl}/journey/progression/batches/preview`);
    expect(preview.request.method).toBe('POST');
    expect(preview.request.body.idempotencyKey).toBe('key-1');
    preview.flush({ id: 'batch-1' });

    api.commitPromotion('batch-1', 'Council approved', 0).subscribe();
    const commit = http.expectOne(`${environment.apiUrl}/journey/progression/batches/batch-1/commit`);
    expect(commit.request.body).toEqual({ reason: 'Council approved', version: 0 });
    commit.flush({});
    http.verify();
  });

  it('sends an explicit reason and optimistic version for manual override', () => {
    const { api, http } = setup();
    api.overrideDecision('decision-1', { finalDecision: 'HOLD', targetClassId: 'class-1', reason: 'Council decision', version: 3 }).subscribe();
    const request = http.expectOne(`${environment.apiUrl}/journey/progression/decisions/decision-1`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ finalDecision: 'HOLD', targetClassId: 'class-1', reason: 'Council decision', version: 3 });
    request.flush({});
    http.verify();
  });
});
