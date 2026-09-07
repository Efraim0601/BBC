import { afterEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of, Subject } from 'rxjs';
import { AttendanceRoster } from '../../core/models';
import { AttendanceComponent, attendanceRosterReadOnly } from './attendance';
import { AttendanceApi } from './attendance.api';
import { AuthService } from '../../core/auth.service';
import { FoundationApi } from '../../core/foundation.api';
import { I18nService } from '../../core/i18n.service';

const rosterWith = (canMark: boolean, canFinalize: boolean): AttendanceRoster => ({
  session: {
    id: 'session-1', classId: 'class-1', className: '6ème A', date: '2026-09-07',
    model: 'PERIOD', periodKey: 'P4', subjectCode: 'ANGLAIS', status: 'DRAFT',
    version: 0, total: 1, marked: 0,
  },
  marks: [],
  events: [],
  capabilities: { canMark, canFinalize, canReopen: false },
});

describe('attendance date and selection workflow', () => {
  afterEach(() => TestBed.resetTestingModule());

  function setup() {
    const api = {
      classes: vi.fn().mockReturnValue(of([{id:'class-1',model:'DAILY'}])),
      sessions: vi.fn().mockReturnValue(of([])),
      roster: vi.fn(),
    };
    TestBed.configureTestingModule({providers:[
      {provide:AttendanceApi,useValue:api},
      {provide:AuthService,useValue:{loadCapabilities:()=>of({}),canAction:()=>false}},
      {provide:FoundationApi,useValue:{currentSession:()=>of({id:'year',startDate:'2026-01-01',endDate:'2027-12-31'})}},
      {provide:I18nService,useValue:{lang:()=> 'en'}},
    ]});
    const component = TestBed.runInInjectionContext(() => new AttendanceComponent()) as any;
    return {api,component};
  }

  it('does not open a daily roster when the date has no school session', () => {
    const {api,component}=setup();
    component.setDate('2026-09-06');
    component.selectClass('class-1');
    expect(api.classes).toHaveBeenLastCalledWith('2026-09-06');
    expect(api.sessions).toHaveBeenLastCalledWith('class-1','2026-09-06');
    expect(api.roster).not.toHaveBeenCalled();
    expect(component.busy()).toBe(false);
  });

  it('ignores an earlier class-list response after the user changes date again', () => {
    const {api,component}=setup();
    const earlier=new Subject<any[]>();
    api.classes.mockReturnValueOnce(earlier).mockReturnValueOnce(of([{id:'new-class',model:'DAILY'}]));
    component.setDate('2026-09-07');
    component.setDate('2026-09-08');
    earlier.next([{id:'old-class',model:'DAILY'}]);
    expect(component.classes().map((c:any)=>c.id)).toEqual(['new-class']);
  });

  it('ignores an earlier roster after switching class', () => {
    const {api,component}=setup();
    const earlier=new Subject<AttendanceRoster>();
    api.sessions.mockReturnValueOnce(of([{id:'session-1'}]));
    api.roster.mockReturnValueOnce(earlier);
    component.selectClass('class-1');
    component.selectClass('');
    earlier.next(rosterWith(true,true));
    expect(component.roster()).toBeNull();
    expect(component.busy()).toBe(false);
  });
});

describe('attendance roster presentation', () => {
  it('uses contextual server capabilities for colleague read-only mode', () => {
    expect(attendanceRosterReadOnly(rosterWith(false, false))).toBe(true);
    expect(attendanceRosterReadOnly(rosterWith(true, true))).toBe(false);
    expect(attendanceRosterReadOnly(null)).toBe(false);
  });
});
