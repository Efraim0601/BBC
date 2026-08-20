-- Keep operational invoice balances aligned with the source installment
-- balances for invoices issued before the payment-allocation synchronization
-- was added to the collection and reversal services.

WITH invoice_totals AS (
    SELECT l.school_id,
           l.invoice_id,
           COALESCE(SUM(LEAST(l.amount_minor, GREATEST(0, COALESCE(i.paid_minor, 0)))), 0) AS paid_minor,
           COALESCE(SUM(LEAST(l.amount_minor, GREATEST(0, COALESCE(i.outstanding_minor, 0)))), 0) AS outstanding_minor
    FROM finance_invoice_line l
    JOIN charge_installment i
      ON l.school_id = i.school_id
     AND l.source_installment_id = i.id
    GROUP BY l.school_id, l.invoice_id
)
UPDATE finance_invoice i
SET paid_minor = t.paid_minor,
    outstanding_minor = t.outstanding_minor,
    status = CASE
        WHEN t.outstanding_minor = 0 THEN 'PAID'
        WHEN t.paid_minor > 0 THEN 'PARTIALLY_PAID'
        ELSE 'ISSUED'
    END,
    updated_at = now()
FROM invoice_totals t
WHERE i.school_id = t.school_id
  AND i.id = t.invoice_id
  AND i.status NOT IN ('VOIDED', 'SUPERSEDED');
