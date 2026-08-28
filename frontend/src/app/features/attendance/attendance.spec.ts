import { describe, expect, it } from 'vitest';
import { AttendanceRoster } from '../../core/models';
import { attendanceRosterReadOnly } from './attendance';

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

describe('attendance roster presentation', () => {
  it('uses contextual server capabilities for colleague read-only mode', () => {
    expect(attendanceRosterReadOnly(rosterWith(false, false))).toBe(true);
    expect(attendanceRosterReadOnly(rosterWith(true, true))).toBe(false);
    expect(attendanceRosterReadOnly(null)).toBe(false);
  });
});
