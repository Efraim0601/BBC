import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { environment } from '../../../environments/environment';
import { AccessControlApi } from './access-control.api';

describe('AccessControlApi safe-template preview', () => {
  it('uses the non-mutating empty payload expected by the V2 preview endpoint', () => {
    TestBed.configureTestingModule({
      providers: [AccessControlApi, provideHttpClient(), provideHttpClientTesting()],
    });
    const api = TestBed.inject(AccessControlApi);
    const http = TestBed.inject(HttpTestingController);

    api.previewTemplate('principal', 'principal_oversight').subscribe();

    const request = http.expectOne(
      `${environment.apiUrl}/access/roles/principal/template-preview/principal_oversight`,
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush({});
    http.verify();
  });
});
