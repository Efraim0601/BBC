import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AcademicContextService } from '../../core/academic-context.service';
import { AuthService } from '../../core/auth.service';
import { FoundationApi } from '../../core/foundation.api';
import { I18nService } from '../../core/i18n.service';
import { StudentEnrollmentPanelComponent } from './student-enrollment-panel';

describe('Student enrollment panel scope boundary', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('contains denied optional permission reads and keeps the panel read-only', () => {
    const api = {
      enrollmentHistory: vi.fn(() => of([])),
      listDocuments: vi.fn(() => of([])),
      actionPermissions: vi.fn(() => throwError(() => new Error('403 FORBIDDEN'))),
      listSessions: vi.fn(() => of([])),
    } as unknown as FoundationApi;
    const context = {
      load: vi.fn(),
      sessions: signal([]),
      sessionId: signal(null),
    } as unknown as AcademicContextService;

    TestBed.configureTestingModule({
      imports: [StudentEnrollmentPanelComponent],
      providers: [
        { provide: FoundationApi, useValue: api },
        { provide: AcademicContextService, useValue: context },
        { provide: AuthService, useValue: { can: vi.fn(() => false) } },
        { provide: I18nService, useValue: { lang: signal('fr') } },
      ],
    });
    TestBed.overrideComponent(StudentEnrollmentPanelComponent, { set: { template: '' } });

    const fixture: ComponentFixture<StudentEnrollmentPanelComponent> = TestBed.createComponent(StudentEnrollmentPanelComponent);
    fixture.componentRef.setInput('student', { id: 'student-1', name: 'Student' } as any);
    fixture.componentRef.setInput('classes', []);
    expect(() => fixture.detectChanges()).not.toThrow();

    expect(api.actionPermissions).toHaveBeenCalledTimes(1);
    expect((fixture.componentInstance as any).permissions()).toEqual({});
  });
});
