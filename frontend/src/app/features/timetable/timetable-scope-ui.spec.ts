import { describe, expect, it } from 'vitest';
import { timetableInitialView } from './timetable';

describe('timetable scope UI', () => {
  it('opens the read-only master view for Direction', () => {
    expect(timetableInitialView(false, true)).toBe('master');
  });

  it('keeps ordinary staff on their personal schedule when master read is denied', () => {
    expect(timetableInitialView(false, false)).toBe('teachers');
  });
});
