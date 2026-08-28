import { describe, expect, it } from 'vitest';
import { formatStudentDate, maskStudentDateInput, parseStudentDate } from './student-date';

describe('student date formatting', () => {
  it('parses DD/MM/YYYY into the ISO API value', () => {
    expect(parseStudentDate('15/04/2018')).toBe('2018-04-15');
    expect(parseStudentDate('04/15/2018')).toBeNull();
    expect(parseStudentDate('31/02/2018')).toBeNull();
  });

  it('formats existing ISO dates as DD/MM/YYYY', () => {
    expect(formatStudentDate('2018-04-15')).toBe('15/04/2018');
    expect(formatStudentDate(null)).toBe('');
  });

  it('adds both separators for a numeric mobile keyboard', () => {
    expect(maskStudentDateInput('1504')).toBe('15/04/');
    expect(maskStudentDateInput('15042018')).toBe('15/04/2018');
  });
});
