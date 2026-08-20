package com.bbc.sms.finance.payroll;

import com.bbc.sms.documents.OfficialDocumentService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static com.bbc.sms.finance.payroll.PayrollDtos.*;

/** Explicit payroll permissions and tenant-scoped endpoints for BAY-50. */
@RestController
@RequestMapping("/api/finance/v2/payroll")
public class PayrollController {
    private final PayrollService payroll;
    private final OfficialDocumentService documents;

    public PayrollController(PayrollService payroll, OfficialDocumentService documents) {
        this.payroll = payroll;
        this.documents = documents;
    }

    @GetMapping("/components")
    @PreAuthorize("@policy.canAction('PAYROLL_VIEW')")
    public List<ComponentView> components() { return payroll.components(); }

    @PostMapping("/components")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@policy.canAction('PAYROLL_COMPONENT_MANAGE')")
    public ComponentView createComponent(@Valid @RequestBody ComponentUpsert request) { return payroll.createComponent(request); }

    @PutMapping("/components/{id}")
    @PreAuthorize("@policy.canAction('PAYROLL_COMPONENT_MANAGE')")
    public ComponentView updateComponent(@PathVariable UUID id, @Valid @RequestBody ComponentUpsert request) {
        return payroll.updateComponent(id, request);
    }

    @GetMapping("/periods")
    @PreAuthorize("@policy.canAction('PAYROLL_VIEW')")
    public List<PeriodView> periods() { return payroll.periods(); }

    @GetMapping("/payment-options")
    @PreAuthorize("@policy.canAction('PAYROLL_VIEW')")
    public PaymentOptionsView paymentOptions() { return payroll.paymentOptions(); }

    @PostMapping("/periods")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@policy.canAction('PAYROLL_PERIOD_MANAGE')")
    public PeriodView createPeriod(@Valid @RequestBody PeriodRequest request) { return payroll.createPeriod(request); }

    @PutMapping("/periods/{id}")
    @PreAuthorize("@policy.canAction('PAYROLL_PERIOD_MANAGE')")
    public PeriodView updatePeriod(@PathVariable UUID id, @Valid @RequestBody PeriodRequest request) {
        return payroll.updatePeriod(id, request);
    }

    @PostMapping("/periods/{id}/close")
    @PreAuthorize("@policy.canAction('PAYROLL_PERIOD_MANAGE')")
    public PeriodView closePeriod(@PathVariable UUID id, @Valid @RequestBody ActionRequest request) {
        return payroll.closePeriod(id, request);
    }

    @GetMapping("/runs")
    @PreAuthorize("@policy.canAction('PAYROLL_VIEW')")
    public List<RunView> runs() { return payroll.runs(); }

    @PostMapping("/preview")
    @PreAuthorize("@policy.canAction('PAYROLL_CALCULATE')")
    public PreviewView preview(@Valid @RequestBody RunRequest request) { return payroll.preview(request); }

    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@policy.canAction('PAYROLL_CALCULATE')")
    public RunDetailView createRun(@Valid @RequestBody RunRequest request) { return payroll.createRun(request); }

    @GetMapping("/runs/{id}")
    @PreAuthorize("@policy.canAction('PAYROLL_VIEW')")
    public RunDetailView run(@PathVariable UUID id) { return payroll.detail(id); }

    @PostMapping("/runs/{id}/calculate")
    @PreAuthorize("@policy.canAction('PAYROLL_CALCULATE')")
    public RunDetailView calculate(@PathVariable UUID id,
                                   @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return payroll.calculate(id, key);
    }

    @PostMapping("/adjustments")
    @PreAuthorize("@policy.canAction('PAYROLL_ADJUST')")
    public RunDetailView adjust(@Valid @RequestBody AdjustmentRequest request) { return payroll.adjust(request); }

    @PostMapping("/runs/{id}/review")
    @PreAuthorize("@policy.canAction('PAYROLL_REVIEW')")
    public RunDetailView review(@PathVariable UUID id, @Valid @RequestBody ActionRequest request) {
        return payroll.review(id, request);
    }

    @PostMapping("/runs/{id}/approve")
    @PreAuthorize("@policy.canAction('PAYROLL_APPROVE')")
    public RunDetailView approve(@PathVariable UUID id, @Valid @RequestBody ActionRequest request) {
        return payroll.approve(id, request);
    }

    @PostMapping("/runs/{id}/void")
    @PreAuthorize("@policy.canAction('PAYROLL_VOID')")
    public RunDetailView voidRun(@PathVariable UUID id, @Valid @RequestBody ActionRequest request,
                                 @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return payroll.voidRun(id, request, key);
    }

    @PostMapping("/runs/{id}/pay")
    @PreAuthorize("@policy.canAction('PAYROLL_PAY')")
    public PayResultView pay(@PathVariable UUID id, @Valid @RequestBody PayRequest request,
                             @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return payroll.pay(id, request, key);
    }

    @GetMapping("/runs/{id}/employees/{employeePayrollId}")
    @PreAuthorize("@policy.canAction('PAYROLL_VIEW')")
    public EmployeeView employee(@PathVariable UUID employeePayrollId) { return payroll.employee(employeePayrollId); }

    @PostMapping("/runs/{id}/payslips")
    @PreAuthorize("@policy.canAction('PAYSLIP_REGENERATE')")
    public PayslipJobView generatePayslips(@PathVariable UUID id,
                                           @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return payroll.generatePayslips(id, key);
    }

    @GetMapping("/payslip-jobs/{id}")
    @PreAuthorize("@policy.canAction('PAYSLIP_VIEW_ALL')")
    public PayslipJobView payslipJob(@PathVariable UUID id) { return payroll.payslipJob(id); }

    @GetMapping("/payslip-jobs/{id}/results")
    @PreAuthorize("@policy.canAction('PAYSLIP_VIEW_ALL')")
    public List<PayslipJobResultView> payslipJobResults(@PathVariable UUID id) { return payroll.payslipJobResults(id); }

    @PostMapping("/payslip-jobs/{id}/retry")
    @PreAuthorize("@policy.canAction('PAYSLIP_REGENERATE')")
    public PayslipJobView retryPayslipJob(@PathVariable UUID id,
                                          @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return payroll.retryPayslipJob(id, key);
    }

    @GetMapping("/payslips")
    @PreAuthorize("@policy.canAction('PAYSLIP_VIEW_ALL')")
    public List<PayslipView> payslips() { return payroll.payslips(); }

    @GetMapping("/payslips/{id}")
    @PreAuthorize("@policy.canAction('PAYSLIP_VIEW_ALL')")
    public PayslipView payslip(@PathVariable UUID id) { return payroll.payslip(id, false); }

    @GetMapping("/payslips/{id}/download")
    @PreAuthorize("@policy.canAction('PAYSLIP_VIEW_ALL')")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        PayslipView slip = payroll.payslip(id, false);
        UUID documentId = payroll.payslipDocument(id, false);
        return pdfResponse(documentId, slip.payslipNumber());
    }

    @PostMapping("/payslips/{id}/regenerate")
    @PreAuthorize("@policy.canAction('PAYSLIP_REGENERATE')")
    public PayslipView regenerate(@PathVariable UUID id,
                                  @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return payroll.regeneratePayslip(id, key);
    }

    /** Employee self-service is ownership checked in the service, not by a caller-supplied employee id. */
    @GetMapping("/self/payslips")
    @PreAuthorize("@perm.canAction('PAYSLIP_VIEW_SELF')")
    public List<PayslipView> selfPayslips() { return payroll.selfPayslips(); }

    @GetMapping("/self/payslips/{id}")
    @PreAuthorize("@perm.canAction('PAYSLIP_VIEW_SELF')")
    public PayslipView selfPayslip(@PathVariable UUID id) { return payroll.payslip(id, true); }

    @GetMapping("/self/payslips/{id}/download")
    @PreAuthorize("@perm.canAction('PAYSLIP_VIEW_SELF')")
    public ResponseEntity<byte[]> downloadSelf(@PathVariable UUID id) {
        PayslipView slip = payroll.payslip(id, true);
        UUID documentId = payroll.payslipDocument(id, true);
        return pdfResponse(documentId, slip.payslipNumber());
    }

    private ResponseEntity<byte[]> pdfResponse(UUID documentId, String number) {
        String safe = number == null || number.isBlank() ? "payslip" : number.replaceAll("[^A-Za-z0-9._-]", "_");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(safe + ".pdf", StandardCharsets.UTF_8).build().toString())
                .body(documents.content(documentId));
    }
}
