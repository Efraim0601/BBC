import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { AuthService } from '../core/auth.service';
import { AcademicContextService } from '../core/academic-context.service';
import { FoundationApi } from '../core/foundation.api';
import { I18nService } from '../core/i18n.service';
import { SetupApi } from '../core/setup.api';
import { AcademicApi } from './academic/academic.api';
import { AcademicComponent } from './academic/academic';
import { AttendanceApi } from './attendance/attendance.api';
import { AttendanceComponent } from './attendance/attendance';
import { TimetableApi } from './timetable/timetable.api';
import { TimetableComponent } from './timetable/timetable';
import { StudentApi } from './students/students.api';
import { ScopeService } from '../core/scope.service';
import { ActivatedRoute, Router } from '@angular/router';
import { PhotoApi } from '../core/photo.api';

describe('teacher-scoped UI data loading', () => {
  it('loads academic classes from the filtered teacher scope instead of admin setup', () => {
    const academicApi = {
      academicMyScope: vi.fn(() => of({
        academicSessionId: 'session-1', reportingPeriodId: 'period-1', periodCode: 'S1', periodLabel: 'S1',
        subjects: [
          { code: 'FRANCAIS', label: 'Francais', classId: 'class-1', className: 'SEC-FR-4E-A', level: 'secondary', source: 'SECONDARY_RESPONSIBLE', assignmentId: 'assignment-1', assignmentVersion: 0, capabilities: {} },
          { code: 'MATHS', label: 'Mathematiques', classId: 'class-1', className: 'SEC-FR-4E-A', level: 'secondary', source: 'SECONDARY_RESPONSIBLE', assignmentId: 'assignment-2', assignmentVersion: 0, capabilities: {} },
          { code: 'BIOLOGY', label: 'Biologie', classId: 'class-2', className: 'SEC-EN-F1-A', level: 'secondary', source: 'SECONDARY_RESPONSIBLE', assignmentId: 'assignment-3', assignmentVersion: 0, capabilities: {} },
        ],
        classOverviews: [],
      })),
    } as unknown as AcademicApi;
    const setupApi = { listClasses: vi.fn(() => of([])) } as unknown as SetupApi;
    const auth = { user: signal({ role: 'form_teacher' }), can: vi.fn(() => false) };

    TestBed.configureTestingModule({
      imports: [AcademicComponent],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: AcademicApi, useValue: academicApi },
        { provide: SetupApi, useValue: setupApi },
        { provide: StudentApi, useValue: {} },
        { provide: FoundationApi, useValue: {
          currentSession: vi.fn(() => of({ id: 'session-1' })),
          reportingDependencies: vi.fn(() => of([])),
          reportingPeriods: vi.fn(() => of([])),
        } },
        { provide: ScopeService, useValue: { scope: vi.fn(() => null) } },
        { provide: I18nService, useValue: { lang: signal('fr'), t: vi.fn((key: string) => key) } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => null } } } },
        { provide: Router, useValue: { navigate: vi.fn() } },
        { provide: PhotoApi, useValue: {} },
      ],
    });
    TestBed.overrideComponent(AcademicComponent, { set: { template: '' } });

    const fixture: ComponentFixture<AcademicComponent> = TestBed.createComponent(AcademicComponent);
    fixture.detectChanges();

    expect(academicApi.academicMyScope).toHaveBeenCalledTimes(1);
    expect(setupApi.listClasses).not.toHaveBeenCalled();
    expect((fixture.componentInstance as any).classes()).toEqual([
      expect.objectContaining({ id: 'class-1', name: 'SEC-FR-4E-A', level: 'secondary' }),
      expect.objectContaining({ id: 'class-2', name: 'SEC-EN-F1-A', level: 'secondary' }),
    ]);
  });

  it('opens a teacher timetable without loading admin-only timetable resources', () => {
    const api = {
      classes: vi.fn(() => of([])),
      periods: vi.fn(() => of([])),
      rooms: vi.fn(() => of([])),
      roomsV2: vi.fn(() => of([])),
      mySchedule: vi.fn(() => of({ teacherName: 'Teacher', sessionLabel: '2026-2027', slots: [] })),
    } as unknown as TimetableApi;
    const auth = {
      can: vi.fn(() => false),
      canAction: vi.fn(() => false),
      capabilities: signal({ actions: [] }),
      loadCapabilities: vi.fn(() => of({ actions: [] })),
    };

    TestBed.configureTestingModule({
      imports: [TimetableComponent],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: TimetableApi, useValue: api },
        { provide: I18nService, useValue: { lang: vi.fn(() => 'en'), t: vi.fn((key: string) => key) } },
        { provide: SetupApi, useValue: {} },
        { provide: AcademicContextService, useValue: { load: vi.fn(), sessionId: vi.fn(() => 'session-1') } },
      ],
    });
    TestBed.overrideComponent(TimetableComponent, { set: { template: '' } });
    const fixture: ComponentFixture<TimetableComponent> = TestBed.createComponent(TimetableComponent);
    fixture.detectChanges();

    expect(api.mySchedule).toHaveBeenCalledTimes(1);
    expect(api.classes).not.toHaveBeenCalled();
    expect(api.periods).not.toHaveBeenCalled();
    expect(api.rooms).not.toHaveBeenCalled();
    expect(api.roomsV2).not.toHaveBeenCalled();
  });

  it('does not request admin attendance policies for a teacher roster page', () => {
    const api = {
      classes: vi.fn(() => of([])),
      policies: vi.fn(() => of([])),
    } as unknown as AttendanceApi;
    const auth = {
      loadCapabilities: vi.fn(() => of({ actions: [] })),
      canAction: vi.fn(() => false),
      user: vi.fn(() => ({ role: 'teacher' })),
    };

    TestBed.configureTestingModule({
      imports: [AttendanceComponent],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: AttendanceApi, useValue: api },
        { provide: FoundationApi, useValue: { currentSession: vi.fn(() => of({ startDate: '2026-09-01', endDate: '2027-07-16' } as any)) } },
        { provide: I18nService, useValue: { lang: vi.fn(() => 'en') } },
      ],
    });
    TestBed.overrideComponent(AttendanceComponent, { set: { template: '' } });
    const fixture: ComponentFixture<AttendanceComponent> = TestBed.createComponent(AttendanceComponent);
    fixture.detectChanges();

    expect(api.classes).toHaveBeenCalledTimes(1);
    expect(auth.loadCapabilities).toHaveBeenCalledTimes(1);
    expect(api.policies).not.toHaveBeenCalled();
  });
});
