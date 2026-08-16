import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { environment } from '../../../environments/environment';
import { RegistrationRequest, StudentApi } from './students.api';

describe('StudentApi family management', () => {
  function setup() {
    TestBed.configureTestingModule({ providers: [StudentApi, provideHttpClient(), provideHttpClientTesting()] });
    return { api: TestBed.inject(StudentApi), http: TestBed.inject(HttpTestingController) };
  }

  it('registers the student, enrollment and guardians through the atomic endpoint', () => {
    const { api, http } = setup();
    const body: RegistrationRequest = {
      student: { firstName: 'Awa', lastName: 'Test', classId: 'class-1' },
      guardians: [{ displayName: 'Parent Test', email: 'parent@example.test', relationshipType: 'MOTHER', accessMode: 'SEND_INVITE' }],
    };

    api.register(body).subscribe();
    const request = http.expectOne(`${environment.apiUrl}/student-registrations`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush({});
    http.verify();
  });

  it('normalizes guardian search into a query parameter', () => {
    const { api, http } = setup();
    api.searchGuardians('NGONO').subscribe();
    const request = http.expectOne(req => req.url === `${environment.apiUrl}/guardians/search`);
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('q')).toBe('NGONO');
    request.flush([]);
    http.verify();
  });

  it('requests the academic roster by session and class, not legacy class name', () => {
    const { api, http } = setup();
    api.listRoster('session-2026', 'class-ce1').subscribe();
    const request = http.expectOne(`${environment.apiUrl}/students/roster?sessionId=session-2026&classId=class-ce1`);
    expect(request.request.method).toBe('GET');
    request.flush([]);
    http.verify();
  });

  it('loads registrar class options through the student-profile read path', () => {
    const { api, http } = setup();
    api.listClassOptions().subscribe();
    const request = http.expectOne(`${environment.apiUrl}/students/class-options`);
    expect(request.request.method).toBe('GET');
    request.flush([]);
    http.verify();
  });

  it('keeps import preview and commit as separate calls', () => {
    const { api, http } = setup();
    api.familyImportDryRun([], 'families.csv').subscribe();
    const preview = http.expectOne(`${environment.apiUrl}/family-imports/dry-run`);
    expect(preview.request.body).toEqual({ sourceName: 'families.csv', rows: [] });
    preview.flush({});

    api.familyImportCommit('job-1').subscribe();
    const commit = http.expectOne(`${environment.apiUrl}/family-imports/job-1/commit`);
    expect(commit.request.method).toBe('POST');
    commit.flush({});
    http.verify();
  });
});
