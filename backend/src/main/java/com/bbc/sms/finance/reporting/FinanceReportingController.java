package com.bbc.sms.finance.reporting;

import com.bbc.sms.platform.common.ApiException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static com.bbc.sms.finance.reporting.FinanceReportingDtos.*;

@RestController
@RequestMapping("/api/finance/v2/reports")
public class FinanceReportingController {
    private final FinanceReportingService service;

    public FinanceReportingController(FinanceReportingService service) {
        this.service = service;
    }

    @GetMapping("/context")
    @PreAuthorize("@perm.canAction('FINANCE_REPORT_VIEW')")
    public ReportContextView context() { return service.contextOptions(); }

    @GetMapping("/receivables")
    @PreAuthorize("@perm.canAction('FINANCE_REPORT_VIEW')")
    public ReportEnvelope<ReceivablesReport> receivables(@RequestParam Map<String, String> params) {
        return service.receivables(filters(params));
    }

    @GetMapping("/collections")
    @PreAuthorize("@perm.canAction('FINANCE_REPORT_VIEW')")
    public ReportEnvelope<CollectionsReport> collections(@RequestParam Map<String, String> params) {
        return service.collections(filters(params));
    }

    @GetMapping("/documents")
    @PreAuthorize("@perm.canAction('FINANCE_REPORT_VIEW')")
    public ReportEnvelope<DocumentsReport> documents(@RequestParam Map<String, String> params) {
        return service.documents(filters(params));
    }

    @GetMapping("/expenses")
    @PreAuthorize("@perm.canAction('FINANCE_REPORT_VIEW')")
    public ReportEnvelope<ExpensesReport> expenses(@RequestParam Map<String, String> params) {
        return service.expenses(filters(params));
    }

    @GetMapping("/payroll")
    @PreAuthorize("@perm.canAction('FINANCE_REPORT_VIEW') and @perm.canAction('PAYROLL_VIEW')")
    public ReportEnvelope<PayrollReport> payroll(@RequestParam Map<String, String> params) {
        return service.payroll(filters(params));
    }

    @GetMapping("/accounting")
    @PreAuthorize("@perm.canAction('FINANCE_REPORT_VIEW')")
    public ReportEnvelope<AccountingReport> accounting(@RequestParam Map<String, String> params) {
        return service.accounting(filters(params));
    }

    @GetMapping("/reconciliation")
    @PreAuthorize("@perm.canAction('FINANCE_REPORT_VIEW')")
    public ReportEnvelope<ReconciliationReport> reconciliation(@RequestParam Map<String, String> params) {
        return service.reconciliation(filters(params));
    }

    @GetMapping("/{report}/export")
    @PreAuthorize("@perm.canAction('FINANCE_EXPORT') and (#report != 'payroll' or @perm.canAction('PAYROLL_VIEW'))")
    public ResponseEntity<byte[]> export(@PathVariable String report, @RequestParam(defaultValue = "csv") String format,
                                         @RequestParam Map<String, String> params) {
        ExportPayload payload = service.export(report, filters(params), format);
        MediaType mediaType = payload.contentType().startsWith("text/csv")
                ? new MediaType("text", "csv", StandardCharsets.UTF_8) : MediaType.APPLICATION_PDF;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDisposition(ContentDisposition.attachment().filename(payload.filename(), StandardCharsets.UTF_8).build());
        headers.setCacheControl("no-store, max-age=0");
        headers.setPragma("no-cache");
        headers.add("X-Report-Row-Count", Integer.toString(payload.rowCount()));
        return ResponseEntity.ok().headers(headers).body(payload.bytes());
    }

    private static ReportFilters filters(Map<String, String> values) {
        return new ReportFilters(uuid(values, "sessionId", "academicSessionId"), date(values, "from", "fromDate"),
                date(values, "to", "toDate"), date(values, "asOfDate", "asOf"), uuid(values, "classId"),
                value(values, "level"), value(values, "feeType", "feeTypeCode"), value(values, "channel", "channelCode"),
                value(values, "status"), integer(values, "limit", 500), integer(values, "offset", 0));
    }

    private static String value(Map<String, String> values, String... keys) {
        for (String key : keys) if (values.get(key) != null && !values.get(key).isBlank()) return values.get(key).trim();
        return null;
    }

    private static UUID uuid(Map<String, String> values, String... keys) {
        String value = value(values, keys); if (value == null) return null;
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException ex) { throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST,
                "REPORT_FILTER_INVALID", "A report filter contains an invalid identifier.", Map.of(keys[0], "Choose a value from the list."), java.util.List.of()); }
    }

    private static LocalDate date(Map<String, String> values, String... keys) {
        String value = value(values, keys); if (value == null) return null;
        try { return LocalDate.parse(value); }
        catch (RuntimeException ex) { throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST,
                "REPORT_FILTER_INVALID", "A report date must use YYYY-MM-DD.", Map.of(keys[0], "Use YYYY-MM-DD."), java.util.List.of()); }
    }

    private static int integer(Map<String, String> values, String key, int fallback) {
        String value = value(values, key); if (value == null) return fallback;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException ex) { throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST,
                "REPORT_FILTER_INVALID", "A report paging value is invalid.", Map.of(key, "Use a whole number."), java.util.List.of()); }
    }
}
