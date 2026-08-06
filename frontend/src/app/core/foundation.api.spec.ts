import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { describe, expect, it } from 'vitest';
import { FoundationApi } from './foundation.api';
import { environment } from '../../environments/environment';

describe('FoundationApi', () => {
  it('sends Idempotency-Key for document generation', () => {
    TestBed.configureTestingModule({ providers: [FoundationApi, provideHttpClient(), provideHttpClientTesting()] });
    const api = TestBed.inject(FoundationApi);
    const http = TestBed.inject(HttpTestingController);
    api.generateDocument({ documentType: 'GENERIC' }, 'stable-key').subscribe();
    const req = http.expectOne(`${environment.apiUrl}/official-documents/generate`);
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Idempotency-Key')).toBe('stable-key');
    req.flush({});
    http.verify();
  });
});
