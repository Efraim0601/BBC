import { describe, expect, it } from 'vitest';
import { hasAttendanceCorrection } from './academic';

describe('attendance council correction policy', () => {
  it('does not require a correction workflow when every value is zero', () => {
    expect(hasAttendanceCorrection({
      justifiedAbsenceHours: 0,
      unjustifiedAbsenceHours: 0,
      lateMinutes: 0,
    })).toBe(false);
  });

  it.each([
    { justifiedAbsenceHours: 0.25, unjustifiedAbsenceHours: 0, lateMinutes: 0 },
    { justifiedAbsenceHours: 0, unjustifiedAbsenceHours: 1, lateMinutes: 0 },
    { justifiedAbsenceHours: 0, unjustifiedAbsenceHours: 0, lateMinutes: 5 },
  ])('detects a manual correction: %o', (input) => {
    expect(hasAttendanceCorrection(input)).toBe(true);
  });
});
