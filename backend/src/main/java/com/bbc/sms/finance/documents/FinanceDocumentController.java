package com.bbc.sms.finance.documents;

import com.bbc.sms.documents.OfficialDocumentService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.bbc.sms.finance.documents.FinanceDocumentDtos.*;

/** Staff-facing immutable invoice and receipt endpoints. */
@RestController
@RequestMapping("/api/finance/v2/documents")
public class FinanceDocumentController {
    private final FinanceDocumentService documents;
    private final OfficialDocumentService officialDocuments;

    public FinanceDocumentController(FinanceDocumentService documents, OfficialDocumentService officialDocuments) {
        this.documents = documents;
        this.officialDocuments = officialDocuments;
    }

    @GetMapping
    @PreAuthorize("@perm.canAction('FINANCE_DOCUMENT_VIEW')")
    public List<FinanceDocumentView> list(@RequestParam(required = false) String type,
                                          @RequestParam(required = false) String number,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) UUID sessionId,
                                          @RequestParam(required = false) LocalDate fromDate,
                                          @RequestParam(required = false) LocalDate toDate,
                                          @RequestParam(required = false) UUID classId,
                                          @RequestParam(required = false) UUID studentId,
                                          @RequestParam(required = false) String recipient,
                                          @RequestParam(required = false) Long minAmountMinor,
                                          @RequestParam(required = false) Long maxAmountMinor) {
        return documents.list(new DocumentListFilters(type, number, status, sessionId, fromDate, toDate,
                classId, studentId, recipient, minAmountMinor, maxAmountMinor));
    }

    @PostMapping("/invoices/preview")
    @PreAuthorize("@perm.canAction('FINANCE_DOCUMENT_VIEW')")
    public InvoicePreview previewInvoice(@Valid @RequestBody InvoiceRequest request) {
        return documents.previewInvoice(request);
    }

    @PostMapping("/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canAction('FINANCE_DOCUMENT_GENERATE')")
    public InvoiceView issueInvoice(@Valid @RequestBody InvoiceRequest request,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return documents.issueInvoice(request, key);
    }

    @PostMapping("/invoices/batch/preview")
    @PreAuthorize("@perm.canAction('FINANCE_DOCUMENT_VIEW')")
    public BatchPreviewView previewBatch(@Valid @RequestBody BatchInvoiceRequest request) {
        return documents.previewBatch(request);
    }

    @PostMapping("/invoices/batch")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("@perm.canAction('FINANCE_DOCUMENT_BATCH')")
    public BatchJobView issueBatch(@Valid @RequestBody BatchInvoiceRequest request,
                                   @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return documents.issueBatch(request, key);
    }

    @GetMapping("/invoices/batch/{jobId}")
    @PreAuthorize("@perm.canAction('FINANCE_DOCUMENT_VIEW')")
    public BatchJobView batchJob(@PathVariable UUID jobId) { return documents.batchJob(jobId); }

    @GetMapping("/invoices/batch/{jobId}/results")
    @PreAuthorize("@perm.canAction('FINANCE_DOCUMENT_VIEW')")
    public List<BatchResultView> batchResults(@PathVariable UUID jobId) { return documents.batchResults(jobId); }

    @GetMapping("/invoices/batch/{jobId}/failures.csv")
    @PreAuthorize("@perm.canAction('FINANCE_DOCUMENT_VIEW')")
    public ResponseEntity<byte[]> batchFailures(@PathVariable UUID jobId) {
        StringBuilder csv = new StringBuilder("enrollmentId,studentId,status,amountMinor,blockerCode,blockerMessage,actionLink,errorDetail\n");
        documents.batchResults(jobId).stream().filter(row -> "BLOCKED".equals(row.resultStatus()) || "FAILED".equals(row.resultStatus()))
                .forEach(row -> csv.append(csv(row.enrollmentId())).append(',').append(csv(row.studentId())).append(',')
                        .append(csv(row.resultStatus())).append(',').append(row.amountMinor()).append(',').append(csv(row.blockerCode()))
                        .append(',').append(csv(row.blockerMessage())).append(',').append(csv(row.actionLink())).append(',').append(csv(row.errorDetail())).append('\n'));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv"))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=invoice-batch-" + jobId + "-failures.csv")
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/invoices/batch/{jobId}/retry-failed")
    @PreAuthorize("@perm.canAction('FINANCE_DOCUMENT_BATCH')")
    public BatchJobView retryFailed(@PathVariable UUID jobId,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return documents.retryFailed(jobId, key);
    }

    @GetMapping("/{type}/{id}")
    @PreAuthorize("@perm.canAction('FINANCE_DOCUMENT_VIEW')")
    public DocumentDetailView detail(@PathVariable String type, @PathVariable UUID id) {
        return documents.detail(type, id);
    }

    @GetMapping("/{type}/{id}/download")
    @PreAuthorize("@perm.canAction('FINANCE_DOCUMENT_VIEW')")
    public ResponseEntity<byte[]> download(@PathVariable String type, @PathVariable UUID id) {
        DocumentDetailView detail = documents.detail(type, id);
        if (detail.generatedDocument() == null || !"ISSUED".equals(detail.generatedDocument().status())) {
            throw com.bbc.sms.platform.common.ApiException.conflict("Ce document financier ne possède pas encore de PDF délivré.");
        }
        String number = detail.generatedDocument().documentNumber();
        String filename = safeFilename(number) + ".pdf";
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(officialDocuments.content(detail.generatedDocument().id()));
    }

    @PostMapping("/invoices/{id}/void")
    @PreAuthorize("@perm.canAction('FINANCE_DOCUMENT_VOID')")
    public InvoiceView voidInvoice(@PathVariable UUID id, @Valid @RequestBody VoidRequest request) {
        return documents.voidInvoice(id, request);
    }

    @PostMapping("/invoices/{id}/supersede")
    @PreAuthorize("@perm.canAction('FINANCE_DOCUMENT_SUPERSEDE')")
    public InvoiceView supersede(@PathVariable UUID id, @Valid @RequestBody SupersedeRequest request,
                                 @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return documents.supersedeInvoice(id, request, key);
    }

    private static String csv(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value).replace("\"", "\"\"");
        return text.contains(",") || text.contains("\n") ? "\"" + text + "\"" : text;
    }

    private static String safeFilename(String value) {
        return value == null || value.isBlank() ? "financial-document" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
