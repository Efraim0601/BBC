import { describe, expect, it } from 'vitest';
import {
  batchBlockedRows,
  batchFormatBytes,
  batchHeadlineText,
  batchItemText,
  batchReasonText,
  batchRepairUrl,
  batchResultCategory,
} from './academic-batch';
import { BulletinBatchJobView, BulletinBatchPreviewRow } from './academic.api';

const job = (patch: Partial<BulletinBatchJobView> = {}): BulletinBatchJobView => ({
  id: 'job', academicSessionId: 'session', reportingPeriodId: 'period', classId: 'class', locale: 'en',
  status: 'COMPLETED_ERRORS', totalItems: 2, processedItems: 2, publishedItems: 0, blockedItems: 2,
  errorItems: 0, progressPercent: 100, requestedAt: '', archiveAvailable: false, version: 1, ...patch,
});

const row = (studentId: string, code = 'REPORT_NOT_CREATED'): BulletinBatchPreviewRow => ({
  studentId, studentName: `Student ${studentId}`, matricule: `BBC-${studentId}`, eligibility: 'BLOCKED',
  code, category: 'BUSINESS_BLOCKER', messageKey: 'academic.batch.reportNotCreated', messageArgs: {},
  currentState: 'NONE', retryableNow: false,
  repairTarget: { route: '/academic', query: { mode: 'bulletin', classId: 'class', reportingPeriodId: 'period', studentId } },
});

describe('published-only batch presentation', () => {
  it('maps stable blocker codes to actionable English and French copy', () => {
    expect(batchReasonText('REPORT_NOT_CREATED', false, 'S1', 'AMANTA')).toContain('S1');
    expect(batchReasonText('REPORT_NOT_CREATED', false, 'S1', 'AMANTA')).toContain('AMANTA');
    expect(batchReasonText('REPORT_VALIDATED_NOT_PUBLISHED', true, 'S1', 'AMANTA')).toContain('publi');
  });

  it('does not turn a published T1 into an S1-ready result', () => {
    const message = batchReasonText('REPORT_NOT_CREATED', false, 'S1', 'AMANTA EBOLO MARIE');
    expect(message).toContain('S1');
    expect(message).not.toContain('T1');
  });

  it('classifies legacy blocker-only completed jobs as blocked', () => {
    expect(batchResultCategory(job({ totalItems: 1, blockedItems: 1 }))).toBe('BLOCKED');
    expect(batchResultCategory(job({ resultCategory: 'CANCELLED', status: 'CANCELLED' }))).toBe('CANCELLED');
    expect(batchResultCategory(job({ resultCategory: 'SUCCESS', status: 'COMPLETED', totalItems: 1, publishedItems: 1, blockedItems: 0 }))).toBe('SUCCESS');
  });

  it('renders completion guidance before raw technical details', () => {
    expect(batchHeadlineText(job({ resultCategory: 'PARTIAL', publishedItems: 1, blockedItems: 1, totalItems: 2 }), false))
      .toContain('partially complete');
    expect(batchItemText({ resultCode: 'PDF_RENDER_FAILED', studentName: 'AMANTA', messageArgs: {} } as any, false, 'S1'))
      .toContain('could not be created');
  });

  it('builds exact repair navigation and keeps blocked rows readable', () => {
    const rows = Array.from({ length: 10 }, (_, index) => row(String(index + 1)));
    expect(batchBlockedRows(rows, false)).toHaveLength(8);
    expect(batchBlockedRows(rows, true)).toHaveLength(10);
    expect(batchRepairUrl(rows[0].repairTarget!)).toBe('/academic?mode=bulletin&classId=class&reportingPeriodId=period&studentId=1');
  });

  it('labels diagnostic/archive sizes distinctly', () => {
    expect(batchFormatBytes(0)).toBe('0 B');
    expect(batchFormatBytes(1536)).toBe('1.5 KB');
    expect(batchFormatBytes(2 * 1024 * 1024)).toBe('2 MB');
  });
});
