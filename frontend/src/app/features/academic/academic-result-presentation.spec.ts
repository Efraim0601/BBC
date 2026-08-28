import { describe, expect, it } from 'vitest';
import { academicBulletinTitle, canReviewGradePacket, computedPeriodCodes, formatAcademicMark, isReadOnlyGradeOversight } from './academic';

describe('computed bulletin presentation', () => {
  it('keeps missing current marks visible instead of rendering zero', () => {
    expect(formatAcademicMark(null)).toBe('—');
    expect(formatAcademicMark(12.805555555)).toBe('12.81');
  });

  it('uses the configured product and period label in titles', () => {
    expect(academicBulletinTitle({ product: 'TERM', reportingPeriodType: 'TERM_RESULT', reportingPeriodCode: 'T2', reportingPeriodLabel: '2e trimestre', sequence: 2 }, true))
      .toBe('BULLETIN — 2e trimestre');
    expect(academicBulletinTitle({ product: 'ANNUAL', reportingPeriodType: 'ANNUAL_RESULT', reportingPeriodCode: 'ANNUAL', sequence: 1 }, false))
      .toBe('ANNUAL REPORT CARD');
  });

  it('derives computed columns from dependency evidence rather than hard-coded pairs', () => {
    expect(computedPeriodCodes([
      { subjectCode: 'MATH', subjectLabel: 'Maths', coef: 2, mark: 12, weighted: 24, periodMarks: [
        { periodCode: 'S3', mark: 11 }, { periodCode: 'S4', mark: null }, { periodCode: 'S5', mark: 14 },
      ] },
    ])).toEqual(['S3', 'S4', 'S5']);
  });

  it('uses the scoped server decision for grade-packet review instead of a role name', () => {
    expect(canReviewGradePacket({
      packetStatus: 'SUBMITTED',
      capabilities: { canEditDraft: false, canSubmit: false, canReview: true, restrictedTeacher: true, oversightOnly: true },
    })).toBe(true);
    expect(canReviewGradePacket({
      packetStatus: 'SUBMITTED',
      capabilities: { canEditDraft: false, canSubmit: false, canReview: false, restrictedTeacher: true, oversightOnly: true },
    })).toBe(false);
    expect(canReviewGradePacket({
      packetStatus: 'DRAFT',
      capabilities: { canEditDraft: true, canSubmit: true, canReview: true, restrictedTeacher: true, oversightOnly: false },
    })).toBe(false);
  });

  it('labels only server-declared colleague oversight as read-only', () => {
    expect(isReadOnlyGradeOversight({
      capabilities: { canEditDraft: false, canSubmit: false, canReview: false, restrictedTeacher: true, oversightOnly: true },
    })).toBe(true);
    expect(isReadOnlyGradeOversight({
      capabilities: { canEditDraft: false, canSubmit: false, canReview: false, restrictedTeacher: true, oversightOnly: false },
    })).toBe(false);
    expect(isReadOnlyGradeOversight(null)).toBe(false);
  });
});
