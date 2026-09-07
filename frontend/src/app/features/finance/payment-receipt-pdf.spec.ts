import { PDFDocument } from 'pdf-lib';
import { describe, expect, it } from 'vitest';
import { renderPaymentReceiptPdf } from './payment-receipt-pdf';

describe('payment receipt PDF', () => {
  it.each(['REVERSED', 'VOID'])('does not issue valid proof for a %s payment', async status => {
    await expect(renderPaymentReceiptPdf({ french: false, school: null,
      payment: { id: 'p', receiptNo: 'V2-1', studentId: 's', amount: 10000, method: 'CASH', paidOn: '2026-09-07', status } as any,
    })).rejects.toThrow('not valid proof');
  });

  it('fits long student names and partially refunded payments on one page', async () => {
    const bytes = await renderPaymentReceiptPdf({ french: true, school: null,
      payment: { id: 'p', receiptNo: 'V2-2', studentId: 's', studentName: 'QA NOM COMPLET TRES LONG '.repeat(8),
        amount: 35000, refundedAmount: 5000, method: 'CASH', paidOn: '2026-09-07', status: 'PARTIALLY_REFUNDED' } as any,
    });
    expect((await PDFDocument.load(bytes)).getPageCount()).toBe(1);
  });

  it('creates one full A4 page with the real student identity', async () => {
    const bytes = await renderPaymentReceiptPdf({
      french: false,
      school: { code: 'BBC', name: 'Bayo Bilingual Complex', motto: null, city: 'Maroua', country: 'Cameroon', address: null, phone: '600000000', email: null, website: null, currency: 'XAF', authority: null, academicYear: '2026-2027' },
      payment: { id: 'payment-1', receiptNo: 'REC-2026-001', studentId: 'student-1', studentName: 'ABOUBAKAR Abdoul Aziz', matricule: 'BBC-1094', className: 'CE1 A', amount: 125000, method: 'CASH', methodLabelFr: 'Espèces', methodLabelEn: 'Cash', reference: 'TEST-REF', tranche: 2, paidOn: '2026-08-27', treasuryAccountId: 'cash-1', treasuryAccountName: 'Cash', journalEntryId: null },
    });
    const document = await PDFDocument.load(bytes);
    expect(document.getPageCount()).toBe(1);
    const { width, height } = document.getPage(0).getSize();
    expect(width).toBeCloseTo(595, 0);
    expect(height).toBeCloseTo(842, 0);
  });
});
