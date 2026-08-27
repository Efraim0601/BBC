package com.bbc.sms.finance.accounts;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static com.bbc.sms.finance.accounts.FinanceAccountDtos.*;

@RestController
@RequestMapping("/api/finance/v2/accounts")
public class FinanceAccountController {
    private final FinanceAccountService accounts;

    public FinanceAccountController(FinanceAccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/students/search")
    @PreAuthorize("@perm.canAction('FINANCE_STUDENT_ACCOUNT_VIEW')")
    public List<StudentAccountSearchView> searchStudents(@RequestParam(defaultValue = "") String q,
                                                         @RequestParam(required = false) UUID classId) {
        return accounts.search(q, classId);
    }

    @GetMapping("/context")
    @PreAuthorize("@perm.canAction('FINANCE_STUDENT_ACCOUNT_VIEW')")
    public StudentAccountContextView context() {
        return accounts.context();
    }

    @GetMapping("/students/{studentId}")
    @PreAuthorize("@perm.canAction('FINANCE_STUDENT_ACCOUNT_VIEW')")
    public StudentAccountView student(@PathVariable UUID studentId) {
        return accounts.student(studentId);
    }

    @PostMapping("/students/{studentId}/consolidated-receipt")
    @PreAuthorize("@perm.canAction('FINANCE_CONSOLIDATED_RECEIPT_CREATE')")
    public ConsolidatedReceiptView consolidatedReceipt(@PathVariable UUID studentId) {
        return accounts.createConsolidatedReceipt(studentId);
    }

    @GetMapping("/students/{studentId}/consolidated-receipt.pdf")
    @PreAuthorize("@perm.canAction('FINANCE_CONSOLIDATED_RECEIPT_CREATE')")
    public ResponseEntity<byte[]> consolidatedReceiptPdf(@PathVariable UUID studentId) {
        ConsolidatedReceiptPdf receipt = accounts.consolidatedReceiptPdf(studentId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(safeFilename(receipt.receiptNumber()) + ".pdf",
                                StandardCharsets.UTF_8).build().toString())
                .body(receipt.content());
    }

    private static String safeFilename(String value) {
        return value == null || value.isBlank() ? "student-account" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
