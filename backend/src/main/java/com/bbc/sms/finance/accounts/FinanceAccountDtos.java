package com.bbc.sms.finance.accounts;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class FinanceAccountDtos {
    private FinanceAccountDtos() {}

    public record AccountPaymentView(UUID id, String source, String receiptNo,
                                     LocalDate paymentDate, long amountMinor,
                                     long refundedMinor, long netAmountMinor,
                                     String currency, String channelCode,
                                     String channelLabel, String treasuryAccountName,
                                     String reference, long allocatedMinor,
                                     long creditMinor, String status,
                                     UUID journalEntryId) {}

    public record StudentAccountView(UUID studentId, String studentName,
                                     String matricule, String className,
                                     String sessionLabel, long billedMinor,
                                     long paidMinor, long outstandingMinor,
                                     long creditMinor, String currency,
                                     String snapshotHash,
                                     List<AccountPaymentView> payments) {}

    public record ConsolidatedReceiptView(UUID studentId, String studentName,
                                          String matricule, String className,
                                          String sessionLabel, String receiptNumber,
                                          LocalDate issueDate, long billedMinor,
                                          long paidMinor, long outstandingMinor,
                                          long creditMinor, String currency,
                                          String status, String snapshotHash,
                                          UUID generatedDocumentId,
                                          String generatedDocumentNumber,
                                          String generatedDocumentStatus,
                                          List<AccountPaymentView> payments) {}

    public record ConsolidatedReceiptPdf(String receiptNumber, byte[] content) {}
}
