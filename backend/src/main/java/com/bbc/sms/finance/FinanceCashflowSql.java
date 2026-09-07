package com.bbc.sms.finance;

/** Shared, tenant-bound cash movements. Four school-id parameters, one per source. */
public final class FinanceCashflowSql {
    private FinanceCashflowSql() {}

    /** Reversed originals remain posted history; the inverse entry offsets them on its own date. */
    public static final String EXPENSE_MOVEMENTS = """
            SELECT j.entry_date AS movement_date,l.debit_minor-l.credit_minor AS amount
              FROM journal_line l JOIN journal_entry j ON j.school_id=l.school_id AND j.id=l.journal_entry_id
              JOIN chart_of_account a ON a.school_id=l.school_id AND a.id=l.account_id
             WHERE l.school_id=? AND j.status IN ('POSTED','REVERSED') AND a.account_type='EXPENSE'
            UNION ALL
            SELECT spent_on,amount FROM expense
             WHERE school_id=? AND status='POSTED' AND journal_entry_id IS NULL
            """;

    public static final String MOVEMENTS = """
            SELECT p.student_id,p.paid_on AS movement_date,p.amount FROM payment p WHERE p.school_id=?
            UNION ALL
            SELECT p.student_id,p.payment_date,p.amount_minor FROM finance_payment p
             WHERE p.school_id=? AND p.status IN ('POSTED','PARTIALLY_REFUNDED','REFUNDED','REVERSED')
            UNION ALL
            SELECT p.student_id,coalesce(j.entry_date,r.posted_at::date),-r.amount_minor
              FROM refund_transaction r JOIN finance_payment p ON p.school_id=r.school_id AND p.id=r.payment_id
              LEFT JOIN journal_entry j ON j.school_id=r.school_id AND j.id=r.journal_entry_id
             WHERE r.school_id=?
            UNION ALL
            SELECT p.student_id,coalesce(j.entry_date,r.posted_at::date),-p.amount_minor
              FROM payment_reversal_request r JOIN finance_payment p ON p.school_id=r.school_id AND p.id=r.payment_id
              LEFT JOIN journal_entry j ON j.school_id=r.school_id AND j.id=r.journal_entry_id
             WHERE r.school_id=? AND r.status='POSTED'
            """;
}
