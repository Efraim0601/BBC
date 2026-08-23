import { describe, expect, it } from 'vitest';
import { selectableTimetableClasses, timetableInitialView } from './timetable';
import { ClassRef } from './timetable.api';

describe('timetable scope UI', () => {
  it('opens the read-only master view for Direction', () => {
    expect(timetableInitialView(false, true)).toBe('master');
  });

  it('keeps ordinary staff on their personal schedule when master read is denied', () => {
    expect(timetableInitialView(false, false)).toBe('teachers');
  });

  it('shows a linked bilingual timetable once while retaining both programme classes', () => {
    const base = { sectionId: 'primary', level: 'primary', model: 'HOMEROOM', status: 'DRAFT',
      homeroomTeacherId: null, homeroomTeacherName: null, version: 0, scheduleGroupId: 'group-1',
      scheduleOwnerId: 'fr-1', scheduleDisplayName: 'SIL A / Class 1 A', sharedSchedule: true,
      scheduleClasses: [] } as unknown as ClassRef;
    const classes = [
      { ...base, id: 'en-1', name: 'Class 1 A', subsystem: 'EN' },
      { ...base, id: 'fr-1', name: 'SIL A', subsystem: 'FR' },
    ];

    expect(selectableTimetableClasses(classes).map(c => c.id)).toEqual(['fr-1']);
    expect(classes).toHaveLength(2);
  });
});
