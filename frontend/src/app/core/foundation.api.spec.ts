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

  it('sends normalized workflow-window rules and configuration-copy idempotency', () => {
    TestBed.configureTestingModule({ providers: [FoundationApi, provideHttpClient(), provideHttpClientTesting()] });
    const api = TestBed.inject(FoundationApi);
    const http = TestBed.inject(HttpTestingController);
    api.saveWorkflowWindowRule('session', {
      scopeType: 'SESSION', action: 'TEACHER_SUBMISSION', mode: 'LIMITED',
      opensAt: null, closesAt: '2026-10-01T16:00:00Z', timezone: 'Africa/Douala', version: 2,
    }).subscribe();
    const rule = http.expectOne(`${environment.apiUrl}/settings/academic-sessions/session/window-rules`);
    expect(rule.request.method).toBe('PUT');
    expect(rule.request.body.mode).toBe('LIMITED');
    rule.flush({});

    api.applyConfigurationCopy('target', {
      sourceSessionId: 'source', mergeMode: 'FILL_MISSING', selectedKeys: [], edits: [],
      reason: 'Reuse approved structure', previewFingerprint: 'fingerprint',
    }, 'session-key').subscribe();
    const copy = http.expectOne(`${environment.apiUrl}/settings/academic-sessions/target/configuration-copy/apply`);
    expect(copy.request.headers.get('Idempotency-Key')).toBe('session-key');
    copy.flush({});
    http.verify();
  });

  it('lists and updates one trimester management window through the new endpoints', () => {
    TestBed.configureTestingModule({ providers: [FoundationApi, provideHttpClient(), provideHttpClientTesting()] });
    const api = TestBed.inject(FoundationApi);
    const http = TestBed.inject(HttpTestingController);

    api.termManagementWindows('session').subscribe();
    const list = http.expectOne(`${environment.apiUrl}/settings/academic-sessions/session/term-management-windows`);
    expect(list.request.method).toBe('GET');
    list.flush([]);

    api.updateTermManagementWindow('session', 'term', {
      limited: true, opensAt: '2026-09-15T08:00:00Z', closesAt: null, version: 4,
    }).subscribe();
    const update = http.expectOne(`${environment.apiUrl}/settings/academic-sessions/session/terms/term/management-window`);
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toEqual({
      limited: true, opensAt: '2026-09-15T08:00:00Z', closesAt: null, version: 4,
    });
    update.flush({});
    http.verify();
  });

  it('previews and installs tenant-scoped standard report-card families', () => {
    TestBed.configureTestingModule({ providers: [FoundationApi, provideHttpClient(), provideHttpClientTesting()] });
    const api = TestBed.inject(FoundationApi);
    const http = TestBed.inject(HttpTestingController);

    api.standardReportTemplatePreview().subscribe();
    const preview = http.expectOne(`${environment.apiUrl}/settings/document-design/standard-templates/preview`);
    expect(preview.request.method).toBe('GET');
    preview.flush({});

    api.installStandardReportTemplates('Install standard families').subscribe();
    const install = http.expectOne(`${environment.apiUrl}/settings/document-design/standard-templates/install`);
    expect(install.request.method).toBe('POST');
    expect(install.request.body).toEqual({ reason: 'Install standard families' });
    install.flush({});
    http.verify();
  });
});
