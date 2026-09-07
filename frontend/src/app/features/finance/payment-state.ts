import { PaymentView } from '../../core/models';

export function validPaymentReceipt(payment: Pick<PaymentView, 'status'> | null): boolean {
  return !!payment && !['REVERSED', 'VOID'].includes(payment.status ?? 'POSTED');
}

export function netPaymentAmount(payment: Pick<PaymentView, 'amount' | 'refundedAmount' | 'status'>): number {
  return validPaymentReceipt(payment) ? Math.max(0, payment.amount - (payment.refundedAmount ?? 0)) : 0;
}

export function paymentStatusLabel(payment: Pick<PaymentView, 'status' | 'refundedAmount'>, french: boolean): string {
  if (!validPaymentReceipt(payment)) return french ? 'Annulé' : 'Reversed';
  if (payment.status === 'REFUNDED') return french ? 'Remboursé' : 'Refunded';
  if ((payment.refundedAmount ?? 0) > 0) return french ? 'Partiellement remboursé' : 'Partially refunded';
  return french ? 'Enregistré' : 'Posted';
}
