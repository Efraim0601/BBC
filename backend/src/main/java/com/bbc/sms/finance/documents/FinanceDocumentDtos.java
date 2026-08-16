package com.bbc.sms.finance.documents;

import com.bbc.sms.documents.OfficialDocumentDtos.GeneratedDocumentView;
import com.bbc.sms.foundation.audit.AuditDtos.AuditView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class FinanceDocumentDtos {
    private FinanceDocumentDtos() {}

    public record BlockerView(String code, String message, String actionLink) {}

    public record RecipientView(UUID guardianId, String name, String email, String phone,
                                String source, String warning, boolean selectionRequired) {}

    public record InvoiceLinePreview(UUID chargeId, UUID installmentId, String feeTypeCode,
                                     String feeTypeNameFr, String feeTypeNameEn,
                                     String descriptionFr, String descriptionEn, LocalDate dueDate,
                                     long amountMinor, long paidMinor, long outstandingMinor,
                                     String currency) {}

    public record InvoicePreview(UUID enrollmentId, UUID studentId, String studentName,
                                 String matricule, String className, UUID academicSessionId,
                                 String sessionLabel, LocalDate issueDate, LocalDate dueDate,
                                 RecipientView recipient, List<InvoiceLinePreview> lines,
                                 long totalMinor, long paidMinor, long outstandingMinor,
                                 String currency, boolean ready, boolean alreadyIssued,
                                 List<BlockerView> blockers) {}

    public record InvoiceRequest(@NotNull UUID enrollmentId, @NotNull LocalDate issueDate,
                                 @NotNull LocalDate dueDate, List<UUID> installmentIds,
                                 UUID recipientGuardianId, String locale) {}

    public record InvoiceLineView(UUID id, UUID chargeId, UUID installmentId, String feeTypeCode,
                                  String feeTypeNameFr, String feeTypeNameEn, String descriptionFr,
                                  String descriptionEn, LocalDate dueDate, long amountMinor,
                                  long paidMinor, long outstandingMinor, String currency) {}

    public record InvoiceView(UUID id, UUID studentId, UUID enrollmentId, UUID academicSessionId,
                              String studentName, String matricule, String className,
                              String sessionLabel, String invoiceNumber, String status,
                              LocalDate issueDate, LocalDate dueDate, String currency,
                              long totalMinor, long paidMinor, long outstandingMinor,
                              RecipientView recipient, String snapshotHash, UUID generatedDocumentId,
                              String generatedDocumentNumber, String generatedDocumentStatus,
                              UUID sourceJournalId, UUID supersededByInvoiceId, String voidReason,
                              long version, List<InvoiceLineView> lines) {}

    public record ReceiptLineView(UUID id, UUID allocationId, UUID chargeId, UUID installmentId,
                                  String feeTypeCode, String feeTypeNameFr, String feeTypeNameEn,
                                  LocalDate dueDate, long allocatedMinor,
                                  long installmentRemainingMinor, String currency) {}

    public record ReceiptView(UUID id, UUID paymentId, UUID studentId, UUID enrollmentId,
                              UUID academicSessionId, String studentName, String matricule,
                              String className, String sessionLabel, String receiptNumber,
                              String status, LocalDate issueDate, String currency, long amountMinor,
                              long allocatedMinor, long creditMinor, long outstandingMinor,
                              String channelCode, String reference, UUID cashierSessionId,
                              UUID journalEntryId, RecipientView recipient, String snapshotHash,
                              UUID generatedDocumentId, String generatedDocumentNumber,
                              String generatedDocumentStatus, String generationError,
                              long version, List<ReceiptLineView> lines) {}

    public record DocumentListFilters(String type, String number, String status, UUID sessionId,
                                     LocalDate fromDate, LocalDate toDate, UUID classId,
                                     UUID studentId, String recipient, Long minAmountMinor,
                                     Long maxAmountMinor) {}

    public record FinanceDocumentView(UUID id, String documentType, String documentNumber,
                                      String status, LocalDate issueDate, LocalDate dueDate,
                                      UUID studentId, UUID academicSessionId, UUID schoolClassId,
                                      String studentName, String className,
                                      String recipientName, long totalMinor, long paidMinor,
                                      long outstandingMinor, String currency,
                                      UUID generatedDocumentId, String generatedDocumentStatus,
                                      String sha256, UUID sourcePaymentId, UUID sourceJournalId,
                                      long version) {}

    public record DocumentDetailView(String documentType, InvoiceView invoice, ReceiptView receipt,
                                     GeneratedDocumentView generatedDocument, List<AuditView> audit) {}

    public record BatchInvoiceRequest(@NotNull UUID academicSessionId, UUID schoolClassId,
                                      @NotNull LocalDate issueDate, @NotNull LocalDate dueDate,
                                      String locale) {}

    public record BatchRowView(UUID enrollmentId, UUID studentId, String studentName,
                               String matricule, String className, String recipientName,
                               long amountMinor, String resultStatus, String blockerCode,
                               String blockerMessage, String actionLink, UUID invoiceId) {}

    public record BatchPreviewView(UUID academicSessionId, UUID schoolClassId,
                                   LocalDate issueDate, LocalDate dueDate, int affectedCount,
                                   long totalMinor, int alreadyIssuedCount, int blockedCount,
                                   List<BatchRowView> rows, List<BlockerView> blockers) {}

    public record BatchJobView(UUID id, UUID academicSessionId, UUID schoolClassId,
                               LocalDate issueDate, LocalDate dueDate, String status,
                               int enrollmentCount, int issuedCount, int alreadyIssuedCount,
                               int blockedCount, int failedCount, long totalAmountMinor,
                               String currency, String lastError, long version) {}

    public record BatchResultView(UUID id, UUID enrollmentId, UUID studentId, UUID invoiceId,
                                  String resultStatus, long amountMinor, String currency,
                                  String blockerCode, String blockerMessage, String actionLink,
                                  String errorDetail) {}

    public record VoidRequest(@NotBlank String reason, @NotNull Long version) {}

    public record SupersedeRequest(@Valid @NotNull InvoiceRequest replacement,
                                   @NotBlank String reason, @NotNull Long version) {}

    public record ParentInvoiceView(UUID id, String invoiceNumber, String status,
                                    LocalDate issueDate, LocalDate dueDate, long totalMinor,
                                    long paidMinor, long outstandingMinor, String currency,
                                    String recipientName, UUID generatedDocumentId) {}

    public record ParentReceiptView(UUID id, String receiptNumber, String status,
                                    LocalDate issueDate, long amountMinor, long allocatedMinor,
                                    long creditMinor, String currency, String channelCode,
                                    String reference, UUID generatedDocumentId) {}
}
