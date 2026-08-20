import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';
import { of } from 'rxjs';
import { FoundationApi } from '../../core/foundation.api';
import { SessionConfigurationCopyComponent } from './session-configuration-copy';

describe('SessionConfigurationCopyComponent', () => {
  it('re-previews edited fields and applies only the edited proposal', () => {
    const preview = {
      sourceSessionId: 'source', targetSessionId: 'target', sourceLabel: 'Previous', targetLabel: 'Target',
      dateStrategy: 'SHIFT_FROM_SESSION_START', mergeMode: 'FILL_MISSING',
      scopes: { terms: true, reportingPeriods: true, dependencies: true, termManagementWindows: true },
      terms: [{ key: 'TERM:T1', kind: 'TERM', code: 'T1', label: 'Term 1', status: 'CREATE', source: {}, proposed: { startDate: '2026-09-01' }, existing: null, warnings: [], blockers: [] }],
      reportingPeriods: [], dependencies: [], termManagementWindows: [], warnings: [], blockers: [],
      fingerprint: 'fingerprint', createCount: 1, updateCount: 0, keepCount: 0,
    };
    const api = {
      previewConfigurationCopy: vi.fn(() => of(preview)),
      applyConfigurationCopy: vi.fn(() => of(preview)),
    };
    TestBed.configureTestingModule({
      imports: [SessionConfigurationCopyComponent],
      providers: [{ provide: FoundationApi, useValue: api }],
    });
    const fixture: ComponentFixture<SessionConfigurationCopyComponent> = TestBed.createComponent(SessionConfigurationCopyComponent);
    const component = fixture.componentInstance as any;
    component.target = { id: 'target' };
    component.sessions = [{ id: 'source', label: 'Previous', startDate: '2025-09-01' }, { id: 'target', label: 'Target', startDate: '2026-09-01' }];
    component.sourceId = 'source';
    component.reason = 'Reuse approved structure';
    fixture.detectChanges();

    component.preview();
    component.edit(preview.terms[0], 'startDate', '2026-09-08');
    component.apply();

    expect(api.previewConfigurationCopy).toHaveBeenCalledTimes(2);
    const previewCalls = (api.previewConfigurationCopy as any).mock.calls as any[][];
    const applyCalls = (api.applyConfigurationCopy as any).mock.calls as any[][];
    expect(previewCalls[1][1].edits).toEqual([
      { key: 'TERM:T1', field: 'startDate', value: '2026-09-08' },
    ]);
    expect(applyCalls[0][1].edits).toEqual([
      { key: 'TERM:T1', field: 'startDate', value: '2026-09-08' },
    ]);
  });
});
