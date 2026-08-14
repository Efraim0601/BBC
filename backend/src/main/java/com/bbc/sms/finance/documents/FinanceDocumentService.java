package com.bbc.sms.finance.documents;

import com.bbc.sms.documents.OfficialDocumentDtos.GeneratedDocumentView;
import com.bbc.sms.documents.OfficialDocumentService;
import com.bbc.sms.finance.accounting.DocumentSequenceService;
import com.bbc.sms.finance.charges.ChargeInstallment;
import com.bbc.sms.finance.charges.ChargeInstallmentRepository;
import com.bbc.sms.finance.charges.StudentCharge;
import com.bbc.sms.finance.charges.StudentChargeRepository;
import com.bbc.sms.finance.collections.FinancePayment;
import com.bbc.sms.finance.collections.FinancePaymentRepository;
import com.bbc.sms.finance.collections.PaymentAllocation;
import com.bbc.sms.finance.collections.PaymentAllocationRepository;
import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.foundation.enrollment.StudentEnrollment;
import com.bbc.sms.foundation.enrollment.StudentEnrollmentRepository;
import com.bbc.sms.foundation.idempotency.IdempotencyService;
import com.bbc.sms.foundation.session.AcademicSession;
import com.bbc.sms.foundation.session.AcademicSessionRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.bbc.sms.finance.documents.FinanceDocumentDtos.*;

/** BAY-48 document orchestration. All financial documents are tenant-scoped snapshots. */
@Service
public class FinanceDocumentService {
    private static final String CURRENCY = "XAF";

    private final FinanceInvoiceRepository invoices;
    private final FinanceInvoiceLineRepository invoiceLines;
    private final FinanceInvoiceBatchJobRepository batchJobs;
    private final FinanceInvoiceBatchResultRepository batchResults;
    private final FinanceReceiptRepository receipts;
    private final FinanceReceiptLineRepository receiptLines;
    private final FinancePaymentRepository payments;
    private final PaymentAllocationRepository allocations;
    private final StudentChargeRepository charges;
    private final ChargeInstallmentRepository installments;
    private final StudentEnrollmentRepository enrollments;
    private final StudentRepository students;
    private final AcademicSessionRepository sessions;
    private final DocumentSequenceService sequences;
    private final OfficialDocumentService officialDocuments;
    private final FinancePdfRenderer pdf;
    private final IdempotencyService idempotency;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public FinanceDocumentService(FinanceInvoiceRepository invoices,
                                  FinanceInvoiceLineRepository invoiceLines,
                                  FinanceInvoiceBatchJobRepository batchJobs,
                                  FinanceInvoiceBatchResultRepository batchResults,
                                  FinanceReceiptRepository receipts,
                                  FinanceReceiptLineRepository receiptLines,
                                  FinancePaymentRepository payments,
                                  PaymentAllocationRepository allocations,
                                  StudentChargeRepository charges,
                                  ChargeInstallmentRepository installments,
                                  StudentEnrollmentRepository enrollments,
                                  StudentRepository students,
                                  AcademicSessionRepository sessions,
                                  DocumentSequenceService sequences,
                                  OfficialDocumentService officialDocuments,
                                  FinancePdfRenderer pdf,
                                  IdempotencyService idempotency,
                                  AuditService audit,
                                  JdbcTemplate jdbc) {
        this.invoices = invoices;
        this.invoiceLines = invoiceLines;
        this.batchJobs = batchJobs;
        this.batchResults = batchResults;
        this.receipts = receipts;
        this.receiptLines = receiptLines;
        this.payments = payments;
        this.allocations = allocations;
        this.charges = charges;
        this.installments = installments;
        this.enrollments = enrollments;
        this.students = students;
        this.sessions = sessions;
        this.sequences = sequences;
        this.officialDocuments = officialDocuments;
        this.pdf = pdf;
        this.idempotency = idempotency;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public InvoicePreview previewInvoice(InvoiceRequest request) {
        InvoiceContext context = invoiceContext(request);
        return context.preview();
    }

    @Transactional
    public InvoiceView issueInvoice(InvoiceRequest request, String idempotencyKey) {
        requireKey(idempotencyKey);
        return idempotency.execute("finance-v2/documents/invoices/issue", idempotencyKey.trim(), request,
                InvoiceView.class, () -> issueInvoiceNow(request, idempotencyKey.trim()));
    }

    /**
     * Keeps the mutable payment summary on issued invoices aligned with the
     * source charge installments. Invoice PDFs remain immutable snapshots, but
     * their operational status and balance must change when a payment is
     * allocated, reversed, or otherwise corrected.
     */
    @Transactional
    public void refreshInvoiceBalancesForInstallments(Iterable<UUID> installmentIds) {
        UUID schoolId = TenantContext.get();
        Set<UUID> invoiceIds = new LinkedHashSet<>();
        for (UUID installmentId : installmentIds) {
            if (installmentId == null) continue;
            invoiceIds.addAll(jdbc.queryForList("SELECT DISTINCT l.invoice_id FROM finance_invoice_line l "
                            + "JOIN finance_invoice i ON i.school_id=l.school_id AND i.id=l.invoice_id "
                            + "WHERE l.school_id=? AND l.source_installment_id=? "
                    + "AND i.status NOT IN ('VOIDED','SUPERSEDED')",
                    UUID.class, schoolId, installmentId));
        }
        for (UUID invoiceId : invoiceIds) {
            Map<String, Object> totals = jdbc.queryForMap("""
                    SELECT COALESCE(SUM(LEAST(l.amount_minor, GREATEST(0, COALESCE(ci.paid_minor, 0)))), 0) AS paid_minor,
                           COALESCE(SUM(LEAST(l.amount_minor, GREATEST(0, COALESCE(ci.outstanding_minor, 0)))), 0) AS outstanding_minor
                    FROM finance_invoice_line l
                    JOIN charge_installment ci
                      ON ci.school_id=l.school_id AND ci.id=l.source_installment_id
                    WHERE l.school_id=? AND l.invoice_id=?
                    """, schoolId, invoiceId);
            long paidMinor = ((Number) totals.get("paid_minor")).longValue();
            long outstandingMinor = ((Number) totals.get("outstanding_minor")).longValue();
            String status = outstandingMinor == 0
                    ? "PAID"
                    : paidMinor > 0 ? "PARTIALLY_PAID" : "ISSUED";
            jdbc.update("""
                    UPDATE finance_invoice
                    SET paid_minor=?, outstanding_minor=?, status=?, updated_at=now()
                    WHERE school_id=? AND id=? AND status NOT IN ('VOIDED','SUPERSEDED')
                    """, paidMinor, outstandingMinor, status, schoolId, invoiceId);
        }
    }

    @Transactional
    public InvoiceView issueInvoiceNow(InvoiceRequest request, String idempotencyKey) {
        UUID schoolId = TenantContext.get();
        InvoiceContext context = invoiceContext(request);
        if (!context.preview().ready()) throw blocked("INVOICE_NOT_READY", "La facture ne peut pas être émise.", context.preview().blockers());
        String sourceKey = sourceKey(context, request);
        FinanceInvoice existing = invoices.findBySchoolIdAndSourceEventKey(schoolId, sourceKey).orElse(null);
        if (existing != null) return invoiceView(existing);

        String prefix = "INV/" + context.session().getCode() + "/";
        String number = sequences.allocate("INVOICE", context.session().getCode(), prefix, 6);
        FinanceInvoice invoice = new FinanceInvoice();
        invoice.setSchoolId(schoolId);
        invoice.setStudentId(context.enrollment().getStudentId());
        invoice.setStudentEnrollmentId(context.enrollment().getId());
        invoice.setAcademicSessionId(context.enrollment().getAcademicSessionId());
        invoice.setSchoolClassIdSnapshot(context.enrollment().getSchoolClassId());
        invoice.setClassNameSnapshot(context.enrollment().getClassNameSnapshot());
        invoice.setInvoiceNumber(number);
        invoice.setStatus("DRAFT");
        invoice.setIssueDate(request.issueDate());
        invoice.setDueDate(request.dueDate());
        invoice.setCurrency(CURRENCY);
        invoice.setTotalMinor(context.preview().totalMinor());
        invoice.setPaidMinor(context.preview().paidMinor());
        invoice.setOutstandingMinor(context.preview().outstandingMinor());
        invoice.setRecipientGuardianId(context.recipient().guardianId());
        invoice.setRecipientName(context.recipient().name());
        invoice.setRecipientEmail(context.recipient().email());
        invoice.setRecipientPhone(context.recipient().phone());
        invoice.setRecipientSource(context.recipient().source());
        invoice.setRecipientWarning(context.recipient().warning());
        invoice.setSnapshotHash(invoiceHash(context, number));
        invoice.setSourceEventKey(sourceKey);
        invoice.setIdempotencyKey(idempotencyKey);
        invoice.setCreatedBy(currentUserId());
        invoice = invoices.saveAndFlush(invoice);
        int no = 1;
        for (InvoiceLinePreview line : context.preview().lines()) {
            FinanceInvoiceLine entity = new FinanceInvoiceLine();
            entity.setSchoolId(schoolId);
            entity.setInvoiceId(invoice.getId());
            entity.setLineNo(no++);
            entity.setSourceChargeId(line.chargeId());
            entity.setSourceInstallmentId(line.installmentId());
            entity.setFeeTypeCode(line.feeTypeCode());
            entity.setFeeTypeNameFr(line.feeTypeNameFr());
            entity.setFeeTypeNameEn(line.feeTypeNameEn());
            entity.setDescriptionFr(line.descriptionFr());
            entity.setDescriptionEn(line.descriptionEn());
            entity.setDueDate(line.dueDate());
            entity.setAmountMinor(line.amountMinor());
            entity.setPaidMinor(line.paidMinor());
            entity.setOutstandingMinor(line.outstandingMinor());
            entity.setCurrency(line.currency());
            invoiceLines.save(entity);
        }
        invoiceLines.flush();
        InvoiceView beforeDocument = invoiceView(invoice);
        byte[] content = pdf.invoice(beforeDocument, schoolSnapshot(), verificationBase());
        GeneratedDocumentView generated = officialDocuments.registerPdf(
                "INVOICE", "FinanceInvoice", invoice.getId().toString(), String.valueOf(invoice.getVersion()),
                locale(request.locale()), "Facture / Invoice " + invoice.getInvoiceNumber(), "PARENT", content,
                "INVOICE_PDF:" + invoice.getId(), invoice.getInvoiceNumber());
        invoice.setGeneratedDocumentId(generated.id());
        invoice.setStatus(invoice.getOutstandingMinor() == 0 ? "PAID" : invoice.getPaidMinor() > 0 ? "PARTIALLY_PAID" : "ISSUED");
        invoice.setIssuedBy(currentUserId());
        invoice.setIssuedAt(Instant.now());
        invoice = invoices.saveAndFlush(invoice);
        InvoiceView result = invoiceView(invoice);
        audit.record("FINANCE_INVOICE_ISSUED", "FinanceInvoice", invoice.getId().toString(), null, result, null);
        return result;
    }

    @Transactional(readOnly = true)
    public BatchPreviewView previewBatch(BatchInvoiceRequest request) {
        UUID schoolId = TenantContext.get();
        List<StudentEnrollment> candidates = batchEnrollments(request);
        List<BatchRowView> rows = new ArrayList<>();
        int blocked = 0;
        int already = 0;
        long total = 0;
        for (StudentEnrollment enrollment : candidates) {
            InvoicePreview preview = previewInvoice(new InvoiceRequest(enrollment.getId(), request.issueDate(), request.dueDate(), null, null, request.locale()));
            String status = preview.ready() ? (preview.alreadyIssued() ? "ALREADY_ISSUED" : "READY") : "BLOCKED";
            if ("BLOCKED".equals(status)) blocked++;
            if ("ALREADY_ISSUED".equals(status)) already++;
            total += preview.totalMinor();
            rows.add(new BatchRowView(enrollment.getId(), enrollment.getStudentId(), preview.studentName(), preview.matricule(),
                    preview.className(), preview.recipient().name(), preview.totalMinor(), status,
                    preview.blockers().isEmpty() ? null : preview.blockers().getFirst().code(),
                    preview.blockers().isEmpty() ? null : preview.blockers().getFirst().message(),
                    preview.blockers().isEmpty() ? null : preview.blockers().getFirst().actionLink(), null));
        }
        return new BatchPreviewView(request.academicSessionId(), request.schoolClassId(), request.issueDate(), request.dueDate(),
                candidates.size(), total, already, blocked, rows, List.of());
    }

    @Transactional
    public BatchJobView issueBatch(BatchInvoiceRequest request, String idempotencyKey) {
        requireKey(idempotencyKey);
        return idempotency.execute("finance-v2/documents/invoices/batch", idempotencyKey.trim(), request,
                BatchJobView.class, () -> issueBatchNow(request, idempotencyKey.trim()));
    }

    @Transactional
    public BatchJobView issueBatchNow(BatchInvoiceRequest request, String idempotencyKey) {
        UUID schoolId = TenantContext.get();
        FinanceInvoiceBatchJob job = new FinanceInvoiceBatchJob();
        job.setSchoolId(schoolId); job.setAcademicSessionId(request.academicSessionId());
        job.setSchoolClassId(request.schoolClassId()); job.setIssueDate(request.issueDate()); job.setDueDate(request.dueDate());
        job.setStatus("RUNNING"); job.setIdempotencyKey(idempotencyKey); job.setRequestedBy(currentUserId()); job.setStartedAt(Instant.now());
        job = batchJobs.saveAndFlush(job);
        List<StudentEnrollment> candidates = batchEnrollments(request);
        job.setEnrollmentCount(candidates.size());
        for (StudentEnrollment enrollment : candidates) {
            FinanceInvoiceBatchResult result = new FinanceInvoiceBatchResult();
            result.setSchoolId(schoolId); result.setJobId(job.getId()); result.setStudentEnrollmentId(enrollment.getId()); result.setStudentId(enrollment.getStudentId());
            try {
                InvoicePreview preview = previewInvoice(new InvoiceRequest(enrollment.getId(), request.issueDate(), request.dueDate(), null, null, request.locale()));
                result.setAmountMinor(preview.totalMinor());
                if (!preview.ready()) {
                    result.setResultStatus("BLOCKED"); result.setBlockerCode(preview.blockers().getFirst().code()); result.setBlockerMessage(preview.blockers().getFirst().message()); result.setActionLink(preview.blockers().getFirst().actionLink()); job.setBlockedCount(job.getBlockedCount() + 1);
                } else if (preview.alreadyIssued()) {
                    result.setResultStatus("ALREADY_ISSUED"); job.setAlreadyIssuedCount(job.getAlreadyIssuedCount() + 1);
                } else {
                    InvoiceView invoice = issueInvoiceNow(new InvoiceRequest(enrollment.getId(), request.issueDate(), request.dueDate(), null, null, request.locale()), "BATCH:" + job.getId() + ":" + enrollment.getId());
                    result.setResultStatus("ISSUED"); result.setFinanceInvoiceId(invoice.id()); job.setIssuedCount(job.getIssuedCount() + 1); job.setTotalAmountMinor(job.getTotalAmountMinor() + invoice.totalMinor());
                }
            } catch (RuntimeException ex) {
                result.setResultStatus("FAILED"); result.setErrorDetail(message(ex)); job.setFailedCount(job.getFailedCount() + 1);
            }
            batchResults.save(result);
        }
        job.setStatus(job.getBlockedCount() > 0 || job.getFailedCount() > 0 ? "COMPLETED_WITH_BLOCKERS" : "COMPLETED");
        job.setCompletedAt(Instant.now());
        job = batchJobs.saveAndFlush(job);
        audit.record("FINANCE_INVOICE_BATCH_COMPLETED", "FinanceInvoiceBatchJob", job.getId().toString(), null, batchJobView(job), null);
        return batchJobView(job);
    }

    @Transactional
    public BatchJobView retryFailed(UUID jobId, String idempotencyKey) {
        requireKey(idempotencyKey);
        return idempotency.execute("finance-v2/documents/invoices/batch-retry", idempotencyKey.trim(), jobId,
                BatchJobView.class, () -> retryFailedNow(jobId));
    }

    @Transactional
    public BatchJobView retryFailedNow(UUID jobId) {
        FinanceInvoiceBatchJob job = batchJobs.findByIdAndSchoolId(jobId, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Lot de factures"));
        List<FinanceInvoiceBatchResult> failed = batchResults.findBySchoolIdAndJobIdAndResultStatus(TenantContext.get(), jobId, "FAILED");
        for (FinanceInvoiceBatchResult row : failed) {
            try {
                StudentEnrollment enrollment = enrollments.findByIdAndSchoolId(row.getStudentEnrollmentId(), TenantContext.get()).orElseThrow();
                InvoiceView invoice = issueInvoiceNow(new InvoiceRequest(enrollment.getId(), job.getIssueDate(), job.getDueDate(), null, null, "fr"), "BATCH:" + jobId + ":" + enrollment.getId());
                row.setResultStatus("ISSUED"); row.setFinanceInvoiceId(invoice.id()); row.setErrorDetail(null); job.setFailedCount(Math.max(0, job.getFailedCount() - 1)); job.setIssuedCount(job.getIssuedCount() + 1);
            } catch (RuntimeException ex) { row.setErrorDetail(message(ex)); }
            batchResults.save(row);
        }
        job.setStatus(job.getFailedCount() > 0 || job.getBlockedCount() > 0 ? "COMPLETED_WITH_BLOCKERS" : "COMPLETED");
        return batchJobs.saveAndFlush(job) == null ? batchJobView(job) : batchJobView(job);
    }

    @Transactional(readOnly = true)
    public List<BatchResultView> batchResults(UUID jobId) {
        return batchResults.findBySchoolIdAndJobIdOrderByCreatedAtAsc(TenantContext.get(), jobId).stream().map(this::batchResultView).toList();
    }

    @Transactional(readOnly = true)
    public BatchJobView batchJob(UUID jobId) {
        return batchJobView(batchJobs.findByIdAndSchoolId(jobId, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Lot de factures")));
    }

    @Transactional(readOnly = true)
    public List<FinanceDocumentView> list(DocumentListFilters filters) {
        UUID schoolId = TenantContext.get();
        List<FinanceDocumentView> result = new ArrayList<>();
        if (filters == null || filters.type() == null || filters.type().isBlank() || "INVOICE".equalsIgnoreCase(filters.type())) {
            invoices.findBySchoolIdOrderByIssueDateDescCreatedAtDesc(schoolId).stream().map(this::invoiceListView).forEach(result::add);
        }
        if (filters == null || filters.type() == null || filters.type().isBlank() || "RECEIPT".equalsIgnoreCase(filters.type())) {
            receipts.findBySchoolIdOrderByIssueDateDescCreatedAtDesc(schoolId).stream().map(this::receiptListView).forEach(result::add);
        }
        return result.stream().filter(d -> filters == null || matches(d, filters)).sorted(Comparator.comparing(FinanceDocumentView::issueDate, Comparator.nullsLast(Comparator.reverseOrder()))).toList();
    }

    @Transactional(readOnly = true)
    public DocumentDetailView detail(String type, UUID id) {
        if ("INVOICE".equalsIgnoreCase(type)) {
            FinanceInvoice invoice = invoices.findByIdAndSchoolId(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Facture"));
            GeneratedDocumentView document = invoice.getGeneratedDocumentId() == null ? null : officialDocuments.byId(invoice.getGeneratedDocumentId());
            return new DocumentDetailView("INVOICE", invoiceView(invoice), null, document, audit.forAggregate("FinanceInvoice", id.toString(), 100));
        }
        if ("RECEIPT".equalsIgnoreCase(type)) {
            FinanceReceipt receipt = receipts.findByIdAndSchoolId(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Reçu"));
            GeneratedDocumentView document = receipt.getGeneratedDocumentId() == null ? null : officialDocuments.byId(receipt.getGeneratedDocumentId());
            return new DocumentDetailView("RECEIPT", null, receiptView(receipt), document, audit.forAggregate("FinanceReceipt", id.toString(), 100));
        }
        throw ApiException.badRequest("Type de document financier invalide.");
    }

    @Transactional
    public InvoiceView voidInvoice(UUID id, VoidRequest request) {
        FinanceInvoice invoice = invoices.findByIdAndSchoolId(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Facture"));
        requireVersion(request.version(), invoice.getVersion(), "facture");
        if ("VOIDED".equals(invoice.getStatus())) return invoiceView(invoice);
        if ("SUPERSEDED".equals(invoice.getStatus())) throw ApiException.conflict("Une facture remplacée ne peut plus être annulée.");
        invoice.setStatus("VOIDED"); invoice.setVoidReason(request.reason().trim()); invoice.setVoidedAt(Instant.now()); invoice.setVoidedBy(currentUserId());
        invoice = invoices.saveAndFlush(invoice);
        if (invoice.getGeneratedDocumentId() != null) officialDocuments.revoke(invoice.getGeneratedDocumentId(), new com.bbc.sms.documents.OfficialDocumentDtos.RevokeRequest(request.reason()));
        audit.record("FINANCE_INVOICE_VOIDED", "FinanceInvoice", id.toString(), null, invoiceView(invoice), request.reason());
        return invoiceView(invoice);
    }

    @Transactional
    public InvoiceView supersedeInvoice(UUID id, SupersedeRequest request, String idempotencyKey) {
        requireKey(idempotencyKey);
        return idempotency.execute("finance-v2/documents/invoices/supersede", idempotencyKey.trim(), request,
                InvoiceView.class, () -> supersedeNow(id, request));
    }

    @Transactional
    public InvoiceView supersedeNow(UUID id, SupersedeRequest request) {
        FinanceInvoice old = invoices.findByIdAndSchoolId(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Facture"));
        requireVersion(request.version(), old.getVersion(), "facture");
        if ("VOIDED".equals(old.getStatus()) || "SUPERSEDED".equals(old.getStatus())) throw ApiException.conflict("Cette facture ne peut plus être remplacée.");
        InvoiceView replacement = issueInvoiceNow(request.replacement(), "SUPERSEDE:" + id + ":" + request.replacement().issueDate());
        old.setStatus("SUPERSEDED"); old.setSupersededByInvoiceId(replacement.id()); old.setSupersededAt(Instant.now()); old.setSupersededBy(currentUserId());
        old = invoices.saveAndFlush(old);
        if (old.getGeneratedDocumentId() != null && replacement.generatedDocumentId() != null) officialDocuments.supersede(old.getGeneratedDocumentId(), replacement.generatedDocumentId(), request.reason());
        audit.record("FINANCE_INVOICE_SUPERSEDED", "FinanceInvoice", id.toString(), null, replacement, request.reason());
        return replacement;
    }

    /** Called by BAY-47 only after the payment journal has posted. Generation failure is retained, not hidden. */
    @Transactional
    public ReceiptView createReceiptForPayment(UUID paymentId) {
        UUID schoolId = TenantContext.get();
        FinanceReceipt existing = receipts.findBySchoolIdAndFinancePaymentId(schoolId, paymentId).orElse(null);
        if (existing != null) return receiptView(existing);
        FinancePayment payment = payments.findByIdAndSchoolId(paymentId, schoolId).orElseThrow(() -> ApiException.notFound("Encaissement"));
        StudentEnrollment enrollment = enrollments.findByIdAndSchoolId(payment.getStudentEnrollmentId(), schoolId).orElseThrow(() -> ApiException.notFound("Inscription"));
        Student student = students.findByIdAndSchoolId(payment.getStudentId(), schoolId).orElseThrow(() -> ApiException.notFound("Élève"));
        AcademicSession session = sessions.findByIdAndSchoolId(payment.getAcademicSessionId(), schoolId).orElseThrow(() -> ApiException.notFound("Session académique"));
        List<PaymentAllocation> paymentAllocations = allocations.findBySchoolIdAndPaymentIdOrderByCreatedAtAsc(schoolId, paymentId);
        RecipientChoice recipient = recipientFor(student, null, false);
        long allocated = paymentAllocations.stream().mapToLong(PaymentAllocation::getAllocatedMinor).sum();
        long outstanding = charges.findBySchoolIdAndStudentEnrollmentIdOrderByChargeDateAscCreatedAtAsc(schoolId, enrollment.getId()).stream().mapToLong(StudentCharge::getOutstandingMinor).sum();
        String number = payment.getReceiptNo() == null ? sequences.allocate("RECEIPT", String.valueOf(payment.getPaymentDate().getYear()), "RCT/" + payment.getPaymentDate().getYear() + "/", 6) : payment.getReceiptNo();
        FinanceReceipt receipt = new FinanceReceipt();
        receipt.setSchoolId(schoolId); receipt.setFinancePaymentId(paymentId); receipt.setStudentId(payment.getStudentId()); receipt.setStudentEnrollmentId(enrollment.getId()); receipt.setAcademicSessionId(payment.getAcademicSessionId());
        receipt.setSchoolClassIdSnapshot(enrollment.getSchoolClassId()); receipt.setClassNameSnapshot(enrollment.getClassNameSnapshot()); receipt.setReceiptNumber(number); receipt.setStatus("GENERATION_FAILED"); receipt.setIssueDate(payment.getPaymentDate()); receipt.setCurrency(payment.getCurrency());
        receipt.setAmountMinor(payment.getAmountMinor()); receipt.setAllocatedMinor(allocated); receipt.setCreditMinor(Math.max(0, payment.getAmountMinor() - allocated)); receipt.setOutstandingMinor(outstanding); receipt.setChannelCodeSnapshot(payment.getChannelCodeSnapshot()); receipt.setPaymentReference(payment.getReference()); receipt.setCashierSessionId(payment.getCashierSessionId()); receipt.setJournalEntryId(payment.getJournalEntryId());
        receipt.setRecipientGuardianId(recipient.guardianId()); receipt.setRecipientName(recipient.name()); receipt.setRecipientEmail(recipient.email()); receipt.setRecipientPhone(recipient.phone()); receipt.setRecipientSource(recipient.source()); receipt.setRecipientWarning(recipient.warning()); receipt.setSourceEventKey("RECEIPT:" + paymentId); receipt.setIdempotencyKey("PAYMENT:" + paymentId); receipt.setIssuedBy(currentUserId()); receipt.setIssuedAt(Instant.now());
        receipt.setSnapshotHash(receiptHash(payment, enrollment, paymentAllocations));
        receipt = receipts.saveAndFlush(receipt);
        for (PaymentAllocation allocation : paymentAllocations) {
            ChargeInstallment installment = installments.findByIdAndSchoolId(allocation.getChargeInstallmentId(), schoolId).orElse(null);
            StudentCharge charge = installment == null ? null : charges.findByIdAndSchoolId(installment.getChargeId(), schoolId).orElse(null);
            if (installment == null || charge == null) continue;
            FinanceReceiptLine line = new FinanceReceiptLine(); line.setSchoolId(schoolId); line.setReceiptId(receipt.getId()); line.setAllocationId(allocation.getId()); line.setSourceChargeId(charge.getId()); line.setSourceInstallmentId(installment.getId()); line.setFeeTypeCode(charge.getFeeTypeCode()); line.setFeeTypeNameFr(charge.getFeeTypeNameFr()); line.setFeeTypeNameEn(charge.getFeeTypeNameEn()); line.setDueDate(installment.getDueDate()); line.setAllocatedMinor(allocation.getAllocatedMinor()); line.setInstallmentRemainingMinor(installment.getOutstandingMinor()); line.setCurrency(payment.getCurrency()); receiptLines.save(line);
        }
        try {
            ReceiptView beforeDocument = receiptView(receipt);
            byte[] content = pdf.receipt(beforeDocument, schoolSnapshot(), verificationBase());
            GeneratedDocumentView generated = officialDocuments.registerPdf("RECEIPT", "FinanceReceipt", receipt.getId().toString(), String.valueOf(receipt.getVersion()), "fr", "Reçu / Receipt " + receipt.getReceiptNumber(), "PARENT", content, "RECEIPT_PDF:" + paymentId, receipt.getReceiptNumber());
            receipt.setGeneratedDocumentId(generated.id()); receipt.setStatus("ISSUED"); receipt.setGenerationError(null); receipt = receipts.saveAndFlush(receipt);
            ReceiptView result = receiptView(receipt); audit.record("FINANCE_RECEIPT_ISSUED", "FinanceReceipt", receipt.getId().toString(), null, result, null); return result;
        } catch (RuntimeException ex) {
            receipt.setGenerationError(message(ex)); receipt.setStatus("GENERATION_FAILED"); receipt = receipts.saveAndFlush(receipt);
            audit.record("FINANCE_RECEIPT_GENERATION_FAILED", "FinanceReceipt", receipt.getId().toString(), null, receiptView(receipt), message(ex));
            return receiptView(receipt);
        }
    }

    @Transactional(readOnly = true)
    public List<ParentInvoiceView> parentInvoices(UUID studentId) {
        return invoices.findBySchoolIdAndStudentIdOrderByIssueDateDescCreatedAtDesc(TenantContext.get(), studentId).stream()
                .filter(i -> "ISSUED".equals(i.getStatus()) || "PARTIALLY_PAID".equals(i.getStatus()) || "PAID".equals(i.getStatus()))
                .filter(i -> i.getGeneratedDocumentId() != null && parentVisible(i.getGeneratedDocumentId()))
                .map(i -> new ParentInvoiceView(i.getId(), i.getInvoiceNumber(), i.getStatus(), i.getIssueDate(), i.getDueDate(), i.getTotalMinor(), i.getPaidMinor(), i.getOutstandingMinor(), i.getCurrency(), i.getRecipientName(), i.getGeneratedDocumentId())).toList();
    }

    @Transactional(readOnly = true)
    public List<ParentReceiptView> parentReceipts(UUID studentId) {
        return receipts.findBySchoolIdAndStudentIdOrderByIssueDateDescCreatedAtDesc(TenantContext.get(), studentId).stream()
                .filter(r -> "ISSUED".equals(r.getStatus()) && r.getGeneratedDocumentId() != null && parentVisible(r.getGeneratedDocumentId()))
                .map(r -> new ParentReceiptView(r.getId(), r.getReceiptNumber(), r.getStatus(), r.getIssueDate(), r.getAmountMinor(), r.getAllocatedMinor(), r.getCreditMinor(), r.getCurrency(), r.getChannelCodeSnapshot(), r.getPaymentReference(), r.getGeneratedDocumentId())).toList();
    }

    @Transactional(readOnly = true)
    public UUID parentDocumentId(String type, UUID id, UUID studentId) {
        UUID documentId;
        if ("INVOICE".equalsIgnoreCase(type)) {
            documentId = invoices.findByIdAndSchoolId(id, TenantContext.get()).filter(i -> studentId.equals(i.getStudentId()) && "ISSUED".equals(i.getStatus())).map(FinanceInvoice::getGeneratedDocumentId).orElseThrow(() -> ApiException.notFound("Document parent"));
        } else {
            documentId = receipts.findByIdAndSchoolId(id, TenantContext.get()).filter(r -> studentId.equals(r.getStudentId()) && "ISSUED".equals(r.getStatus())).map(FinanceReceipt::getGeneratedDocumentId).orElseThrow(() -> ApiException.notFound("Document parent"));
        }
        if (!parentVisible(documentId)) throw ApiException.notFound("Document parent");
        return documentId;
    }

    private boolean parentVisible(UUID documentId) {
        GeneratedDocumentView document = officialDocuments.byId(documentId);
        return "ISSUED".equals(document.status()) && "PARENT".equalsIgnoreCase(document.visibility());
    }

    private InvoiceContext invoiceContext(InvoiceRequest request) {
        if (request.issueDate() == null || request.dueDate() == null || request.dueDate().isBefore(request.issueDate())) throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST, "INVOICE_DATES_INVALID", "La date d'échéance doit être postérieure ou égale à la date d'émission.", Map.of("dueDate", "Choisissez une date valide."), List.of());
        StudentEnrollment enrollment = enrollments.findByIdAndSchoolId(request.enrollmentId(), TenantContext.get()).orElseThrow(() -> ApiException.notFound("Inscription"));
        Student student = students.findByIdAndSchoolId(enrollment.getStudentId(), TenantContext.get()).orElseThrow(() -> ApiException.notFound("Élève"));
        AcademicSession session = sessions.findByIdAndSchoolId(enrollment.getAcademicSessionId(), TenantContext.get()).orElseThrow(() -> ApiException.notFound("Session académique"));
        Set<UUID> wanted = request.installmentIds() == null ? Set.of() : new LinkedHashSet<>(request.installmentIds());
        List<InvoiceLinePreview> lines = new ArrayList<>();
        for (StudentCharge charge : charges.findBySchoolIdAndStudentEnrollmentIdOrderByChargeDateAscCreatedAtAsc(TenantContext.get(), enrollment.getId())) {
            if (Set.of("DRAFT", "REVERSED").contains(charge.getStatus())) continue;
            for (ChargeInstallment installment : installments.findBySchoolIdAndChargeIdOrderByInstallmentNo(TenantContext.get(), charge.getId())) {
                if (!wanted.isEmpty() && !wanted.contains(installment.getId())) continue;
                lines.add(new InvoiceLinePreview(charge.getId(), installment.getId(), charge.getFeeTypeCode(), charge.getFeeTypeNameFr(), charge.getFeeTypeNameEn(), installment.getLabelFr(), installment.getLabelEn(), installment.getDueDate(), installment.getAmountMinor(), installment.getPaidMinor(), installment.getOutstandingMinor(), CURRENCY));
            }
        }
        RecipientChoice recipient = recipientFor(student, request.recipientGuardianId(), true);
        List<BlockerView> blockers = new ArrayList<>();
        if (lines.isEmpty()) blockers.add(new BlockerView("NO_POSTED_INSTALLMENTS", "Aucune échéance issue d'une charge postée n'est disponible pour cette facture.", "/finance/charges"));
        if (recipient.selectionRequired()) blockers.add(new BlockerView("RECIPIENT_SELECTION_REQUIRED", "Plusieurs responsables financiers sont actifs; sélectionnez le destinataire.", "/students/" + student.getId() + "/guardians"));
        if (recipient.name() == null || recipient.name().isBlank()) blockers.add(new BlockerView("RECIPIENT_MISSING", "Le destinataire financier est introuvable.", "/students/" + student.getId() + "/guardians"));
        boolean alreadyIssued = !lines.isEmpty() && lines.stream().allMatch(line -> invoiceLineExists(line.installmentId()));
        return new InvoiceContext(enrollment, student, session, recipient.view(), new InvoicePreview(enrollment.getId(), student.getId(), studentName(student), student.getMatricule(), enrollment.getClassNameSnapshot(), session.getId(), session.getLabel(), request.issueDate(), request.dueDate(), recipient.view(), lines, lines.stream().mapToLong(InvoiceLinePreview::amountMinor).sum(), lines.stream().mapToLong(InvoiceLinePreview::paidMinor).sum(), lines.stream().mapToLong(InvoiceLinePreview::outstandingMinor).sum(), CURRENCY, blockers.isEmpty(), alreadyIssued, blockers));
    }

    private boolean invoiceLineExists(UUID installmentId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM finance_invoice_line l JOIN finance_invoice i ON i.school_id=l.school_id AND i.id=l.invoice_id WHERE l.school_id=? AND l.source_installment_id=? AND i.status NOT IN ('VOIDED','SUPERSEDED')", Integer.class, TenantContext.get(), installmentId);
        return count != null && count > 0;
    }

    private List<StudentEnrollment> batchEnrollments(BatchInvoiceRequest request) {
        List<StudentEnrollment> rows = enrollments.findBySchoolIdAndAcademicSessionIdAndStatusOrderByClassNameSnapshotAsc(TenantContext.get(), request.academicSessionId(), "ACTIVE");
        if (request.schoolClassId() != null) rows = rows.stream().filter(e -> request.schoolClassId().equals(e.getSchoolClassId())).toList();
        return rows;
    }

    private RecipientChoice recipientFor(Student student, UUID selectedGuardianId, boolean requireSelection) {
        UUID schoolId = TenantContext.get();
        List<RecipientChoice> finance = jdbc.query("SELECT g.id,g.display_name,g.email,g.phone,sg.finance_responsible,sg.legal_guardian FROM student_guardian sg JOIN guardian g ON g.school_id=sg.school_id AND g.id=sg.guardian_id WHERE sg.school_id=? AND sg.student_id=? AND sg.effective_to IS NULL AND g.status='ACTIVE' AND sg.portal_access=true AND (sg.finance_responsible=true OR sg.legal_guardian=true) ORDER BY sg.finance_responsible DESC, sg.legal_guardian DESC, g.display_name", (rs, n) -> new RecipientChoice((UUID) rs.getObject(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5), rs.getBoolean(6), false), schoolId, student.getId());
        if (selectedGuardianId != null) {
            return finance.stream().filter(r -> selectedGuardianId.equals(r.guardianId())).findFirst().map(r -> r.withSelectionRequired(false)).orElseThrow(() -> ApiException.badRequest("Le responsable financier sélectionné n'est pas autorisé pour cet élève."));
        }
        if (!finance.isEmpty()) {
            RecipientChoice first = finance.getFirst();
            boolean multipleFinance = finance.stream().filter(RecipientChoice::financeResponsible).count() > 1;
            return first.withSelectionRequired(requireSelection && multipleFinance).withWarning(multipleFinance ? "Plusieurs responsables financiers actifs; le premier est affiché à titre de prévisualisation." : null);
        }
        String legacy = firstNonBlank(student.getParentName(), student.getGuardianName(), student.getFatherName(), student.getMotherName());
        if (legacy != null) {
            return new RecipientChoice(null, legacy,
                    firstNonBlank(student.getGuardianEmail(), student.getFatherEmail(), student.getMotherEmail()),
                    firstNonBlank(student.getParentPhone(), student.getGuardianPhone(), student.getFatherPhone(), student.getMotherPhone()),
                    false, true, false)
                    .withWarning("Destinataire issu des anciennes coordonnées élève; vérifiez le responsable financier.")
                    .withSource("LEGACY_STUDENT_CONTACT");
        }
        if (!requireSelection) {
            // A collection may be posted before a portal-enabled financial guardian exists.
            // Receipts still require a non-null immutable recipient snapshot, so use the
            // student's name and keep an explicit warning instead of failing at the DB layer.
            return new RecipientChoice(null, studentName(student), null, null, false, false, false)
                    .withWarning("Aucun destinataire financier configuré; le reçu est établi au nom de l'élève.")
                    .withSource("STUDENT_FALLBACK");
        }
        return new RecipientChoice(null, null, null, null, false, false, false)
                .withWarning("Aucun destinataire financier configuré.")
                .withSource("UNCONFIGURED");
    }

    private InvoiceView invoiceView(FinanceInvoice invoice) {
        Student student = students.findByIdAndSchoolId(invoice.getStudentId(), TenantContext.get()).orElse(null);
        AcademicSession session = sessions.findByIdAndSchoolId(invoice.getAcademicSessionId(), TenantContext.get()).orElse(null);
        GeneratedDocumentView document = invoice.getGeneratedDocumentId() == null ? null : officialDocuments.byId(invoice.getGeneratedDocumentId());
        RecipientView recipient = new RecipientView(invoice.getRecipientGuardianId(), invoice.getRecipientName(), invoice.getRecipientEmail(), invoice.getRecipientPhone(), invoice.getRecipientSource(), invoice.getRecipientWarning(), false);
        return new InvoiceView(invoice.getId(), invoice.getStudentId(), invoice.getStudentEnrollmentId(), invoice.getAcademicSessionId(), studentName(student), student == null ? null : student.getMatricule(), invoice.getClassNameSnapshot(), session == null ? null : session.getLabel(), invoice.getInvoiceNumber(), invoice.getStatus(), invoice.getIssueDate(), invoice.getDueDate(), invoice.getCurrency(), invoice.getTotalMinor(), invoice.getPaidMinor(), invoice.getOutstandingMinor(), recipient, invoice.getSnapshotHash(), invoice.getGeneratedDocumentId(), document == null ? null : document.documentNumber(), document == null ? null : document.status(), null, invoice.getSupersededByInvoiceId(), invoice.getVoidReason(), invoice.getVersion(), invoiceLines.findBySchoolIdAndInvoiceIdOrderByLineNo(invoice.getSchoolId(), invoice.getId()).stream().map(this::invoiceLineView).toList());
    }

    private ReceiptView receiptView(FinanceReceipt receipt) {
        Student student = students.findByIdAndSchoolId(receipt.getStudentId(), TenantContext.get()).orElse(null);
        AcademicSession session = sessions.findByIdAndSchoolId(receipt.getAcademicSessionId(), TenantContext.get()).orElse(null);
        GeneratedDocumentView document = receipt.getGeneratedDocumentId() == null ? null : officialDocuments.byId(receipt.getGeneratedDocumentId());
        RecipientView recipient = new RecipientView(receipt.getRecipientGuardianId(), receipt.getRecipientName(), receipt.getRecipientEmail(), receipt.getRecipientPhone(), receipt.getRecipientSource(), receipt.getRecipientWarning(), false);
        return new ReceiptView(receipt.getId(), receipt.getFinancePaymentId(), receipt.getStudentId(), receipt.getStudentEnrollmentId(), receipt.getAcademicSessionId(), studentName(student), student == null ? null : student.getMatricule(), receipt.getClassNameSnapshot(), session == null ? null : session.getLabel(), receipt.getReceiptNumber(), receipt.getStatus(), receipt.getIssueDate(), receipt.getCurrency(), receipt.getAmountMinor(), receipt.getAllocatedMinor(), receipt.getCreditMinor(), receipt.getOutstandingMinor(), receipt.getChannelCodeSnapshot(), receipt.getPaymentReference(), receipt.getCashierSessionId(), receipt.getJournalEntryId(), recipient, receipt.getSnapshotHash(), receipt.getGeneratedDocumentId(), document == null ? null : document.documentNumber(), document == null ? null : document.status(), receipt.getGenerationError(), receipt.getVersion(), receiptLines.findBySchoolIdAndReceiptIdOrderByIdAsc(receipt.getSchoolId(), receipt.getId()).stream().map(this::receiptLineView).toList());
    }

    private InvoiceLineView invoiceLineView(FinanceInvoiceLine line) {
        ChargeInstallment source = installments.findByIdAndSchoolId(line.getSourceInstallmentId(), line.getSchoolId()).orElse(null);
        long paidMinor = source == null ? line.getPaidMinor() : Math.min(line.getAmountMinor(), Math.max(0, source.getPaidMinor()));
        long outstandingMinor = source == null ? line.getOutstandingMinor() : Math.min(line.getAmountMinor(), Math.max(0, source.getOutstandingMinor()));
        return new InvoiceLineView(line.getId(), line.getSourceChargeId(), line.getSourceInstallmentId(), line.getFeeTypeCode(), line.getFeeTypeNameFr(), line.getFeeTypeNameEn(), line.getDescriptionFr(), line.getDescriptionEn(), line.getDueDate(), line.getAmountMinor(), paidMinor, outstandingMinor, line.getCurrency());
    }
    private ReceiptLineView receiptLineView(FinanceReceiptLine line) { return new ReceiptLineView(line.getId(), line.getAllocationId(), line.getSourceChargeId(), line.getSourceInstallmentId(), line.getFeeTypeCode(), line.getFeeTypeNameFr(), line.getFeeTypeNameEn(), line.getDueDate(), line.getAllocatedMinor(), line.getInstallmentRemainingMinor(), line.getCurrency()); }
    private FinanceDocumentView invoiceListView(FinanceInvoice invoice) { GeneratedDocumentView d = invoice.getGeneratedDocumentId() == null ? null : officialDocuments.byId(invoice.getGeneratedDocumentId()); Student student = students.findByIdAndSchoolId(invoice.getStudentId(), TenantContext.get()).orElse(null); return new FinanceDocumentView(invoice.getId(), "INVOICE", invoice.getInvoiceNumber(), invoice.getStatus(), invoice.getIssueDate(), invoice.getDueDate(), invoice.getStudentId(), invoice.getAcademicSessionId(), invoice.getSchoolClassIdSnapshot(), studentName(student), invoice.getClassNameSnapshot(), invoice.getRecipientName(), invoice.getTotalMinor(), invoice.getPaidMinor(), invoice.getOutstandingMinor(), invoice.getCurrency(), invoice.getGeneratedDocumentId(), d == null ? null : d.status(), d == null ? invoice.getSnapshotHash() : d.sha256(), null, null, invoice.getVersion()); }
    private FinanceDocumentView receiptListView(FinanceReceipt receipt) { GeneratedDocumentView d = receipt.getGeneratedDocumentId() == null ? null : officialDocuments.byId(receipt.getGeneratedDocumentId()); Student student = students.findByIdAndSchoolId(receipt.getStudentId(), TenantContext.get()).orElse(null); return new FinanceDocumentView(receipt.getId(), "RECEIPT", receipt.getReceiptNumber(), receipt.getStatus(), receipt.getIssueDate(), null, receipt.getStudentId(), receipt.getAcademicSessionId(), receipt.getSchoolClassIdSnapshot(), studentName(student), receipt.getClassNameSnapshot(), receipt.getRecipientName(), receipt.getAmountMinor(), receipt.getAllocatedMinor(), receipt.getOutstandingMinor(), receipt.getCurrency(), receipt.getGeneratedDocumentId(), d == null ? null : d.status(), d == null ? receipt.getSnapshotHash() : d.sha256(), receipt.getFinancePaymentId(), receipt.getJournalEntryId(), receipt.getVersion()); }
    private BatchJobView batchJobView(FinanceInvoiceBatchJob job) { return new BatchJobView(job.getId(), job.getAcademicSessionId(), job.getSchoolClassId(), job.getIssueDate(), job.getDueDate(), job.getStatus(), job.getEnrollmentCount(), job.getIssuedCount(), job.getAlreadyIssuedCount(), job.getBlockedCount(), job.getFailedCount(), job.getTotalAmountMinor(), job.getCurrency(), job.getLastError(), job.getVersion()); }
    private BatchResultView batchResultView(FinanceInvoiceBatchResult row) { return new BatchResultView(row.getId(), row.getStudentEnrollmentId(), row.getStudentId(), row.getFinanceInvoiceId(), row.getResultStatus(), row.getAmountMinor(), row.getCurrency(), row.getBlockerCode(), row.getBlockerMessage(), row.getActionLink(), row.getErrorDetail()); }

    private boolean matches(FinanceDocumentView d, DocumentListFilters f) {
        return (f.number() == null || f.number().isBlank() || d.documentNumber().toLowerCase(Locale.ROOT).contains(f.number().toLowerCase(Locale.ROOT)))
                && (f.status() == null || f.status().isBlank() || d.status().equalsIgnoreCase(f.status()))
                && (f.sessionId() == null || f.sessionId().equals(d.academicSessionId()))
                && (f.classId() == null || f.classId().equals(d.schoolClassId()))
                && (f.fromDate() == null || !d.issueDate().isBefore(f.fromDate()))
                && (f.toDate() == null || !d.issueDate().isAfter(f.toDate()))
                && (f.studentId() == null || f.studentId().equals(d.studentId()))
                && (f.recipient() == null || f.recipient().isBlank() || d.recipientName().toLowerCase(Locale.ROOT).contains(f.recipient().toLowerCase(Locale.ROOT)))
                && (f.minAmountMinor() == null || d.totalMinor() >= f.minAmountMinor())
                && (f.maxAmountMinor() == null || d.totalMinor() <= f.maxAmountMinor());
    }

    private String sourceKey(InvoiceContext context, InvoiceRequest request) { return "INVOICE:" + context.enrollment().getId() + ":" + request.issueDate() + ":" + request.dueDate() + ":" + context.preview().lines().stream().map(line -> line.installmentId().toString()).sorted().reduce((a, b) -> a + "," + b).orElse("none"); }
    private String invoiceHash(InvoiceContext c, String number) { return sha256(number + "|" + c.preview().studentName() + "|" + c.preview().lines().stream().map(Object::toString).reduce("", (a, b) -> a + b)); }
    private String receiptHash(FinancePayment p, StudentEnrollment e, List<PaymentAllocation> rows) { return sha256(p.getId() + "|" + p.getReceiptNo() + "|" + p.getAmountMinor() + "|" + e.getClassNameSnapshot() + "|" + rows); }
    private FinancePdfRenderer.SchoolSnapshot schoolSnapshot() { return jdbc.queryForObject("SELECT code,name,authority,address,city,country,phone,email FROM school WHERE id=?", (rs, n) -> new FinancePdfRenderer.SchoolSnapshot(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8)), TenantContext.get()); }
    private String verificationBase() { return "/api/official-documents/verify/"; }
    private static String locale(String value) { return "en".equalsIgnoreCase(value) ? "en" : "fr"; }
    private static String studentName(Student s) { return s == null ? "—" : (s.getLastName() + " " + s.getFirstName()).trim(); }
    private static String firstNonBlank(String... values) { for (String value : values) if (value != null && !value.isBlank()) return value.trim(); return null; }
    private static String message(Throwable ex) { return ex.getMessage() == null || ex.getMessage().isBlank() ? ex.getClass().getSimpleName() : ex.getMessage(); }
    private static UUID currentUserId() { var authentication = SecurityContextHolder.getContext().getAuthentication(); return authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal ? principal.userId() : null; }
    private static void requireKey(String key) { if (key == null || key.isBlank()) throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "Une clé d'idempotence est obligatoire.", Map.of("idempotencyKey", "Fournissez Idempotency-Key."), List.of()); }
    private static void requireVersion(long expected, long actual, String label) { if (expected != actual) throw ApiException.conflict("La version de " + label + " a changé. Actualisez avant de continuer."); }
    private static ApiException blocked(String code, String message, List<BlockerView> blockers) { return ApiException.structured(org.springframework.http.HttpStatus.CONFLICT, code, message, Map.of(), blockers.stream().map(b -> new ApiException.Blocker("FINANCE_DOCUMENT", b.code(), b.message(), b.actionLink())).toList()); }
    private static String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception ex) { throw new IllegalStateException(ex); } }

    private record InvoiceContext(StudentEnrollment enrollment, Student student, AcademicSession session, RecipientView recipient, InvoicePreview preview) {}
    private record RecipientChoice(UUID guardianId, String name, String email, String phone, boolean financeResponsible, boolean legalGuardian, boolean selectionRequired, String warning, String source) {
        RecipientChoice(UUID guardianId, String name, String email, String phone, boolean financeResponsible, boolean legalGuardian, boolean selectionRequired) { this(guardianId, name, email, phone, financeResponsible, legalGuardian, selectionRequired, null, financeResponsible ? "FINANCE_RESPONSIBLE" : "LEGAL_GUARDIAN"); }
        RecipientChoice withSelectionRequired(boolean value) { return new RecipientChoice(guardianId, name, email, phone, financeResponsible, legalGuardian, value, warning, source); }
        RecipientChoice withWarning(String value) { return new RecipientChoice(guardianId, name, email, phone, financeResponsible, legalGuardian, selectionRequired, value, source); }
        RecipientChoice withSource(String value) { return new RecipientChoice(guardianId, name, email, phone, financeResponsible, legalGuardian, selectionRequired, warning, value); }
        RecipientView view() { return new RecipientView(guardianId, name, email, phone, source, warning, selectionRequired); }
    }
}
