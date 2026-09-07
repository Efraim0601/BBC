import { describe, expect, it } from 'vitest';
import { validPaymentReceipt, netPaymentAmount, paymentStatusLabel } from './payment-state';

describe('payment history and receipt validity',()=>{
  it('preserves old posted receipts without an explicit status',()=>{
    expect(validPaymentReceipt({})).toBe(true);
    expect(netPaymentAmount({amount:1000})).toBe(1000);
  });
  it.each(['REVERSED','VOID'])('does not issue %s receipts as proof of payment',(status)=>{
    expect(validPaymentReceipt({status})).toBe(false);
    expect(netPaymentAmount({amount:1000,status})).toBe(0);
    expect(paymentStatusLabel({status},false)).toBe('Reversed');
  });
  it('shows original and refunded amounts without pretending all money is still paid',()=>{
    const p={amount:35000,refundedAmount:5000,status:'PARTIALLY_REFUNDED'};
    expect(netPaymentAmount(p)).toBe(30000);
    expect(paymentStatusLabel(p,false)).toBe('Partially refunded');
    expect(netPaymentAmount({...p,status:'REFUNDED',refundedAmount:35000})).toBe(0);
  });
});
