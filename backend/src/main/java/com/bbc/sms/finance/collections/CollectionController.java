package com.bbc.sms.finance.collections;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.bbc.sms.finance.collections.CollectionDtos.*;

@RestController
@RequestMapping("/api/finance/v2/collections")
public class CollectionController {
    private final CollectionService collections;

    public CollectionController(CollectionService collections) { this.collections = collections; }

    @GetMapping("/search")
    @PreAuthorize("@perm.canAction('PAYMENT_VIEW')")
    public List<StudentSearchView> search(@RequestParam(defaultValue = "") String q,
                                          @RequestParam(required = false) UUID sessionId) {
        return collections.search(q, sessionId);
    }

    @PostMapping("/quote")
    @PreAuthorize("@perm.canAction('PAYMENT_VIEW')")
    public PaymentQuoteView quote(@Valid @RequestBody QuoteRequest request) { return collections.quote(request); }

    @PostMapping
    @PreAuthorize("@perm.canAction('PAYMENT_COLLECT')")
    public PaymentView post(@Valid @RequestBody PaymentRequest request,
                            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return collections.post(request, idempotencyKey);
    }

    @GetMapping
    @PreAuthorize("@perm.canAction('PAYMENT_VIEW')")
    public List<PaymentView> list(@RequestParam(required = false) UUID academicSessionId,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(required = false) String channelCode,
                                  @RequestParam(required = false) LocalDate fromDate,
                                  @RequestParam(required = false) LocalDate toDate,
                                  @RequestParam(required = false) UUID studentId,
                                  @RequestParam(required = false) String reference,
                                  @RequestParam(required = false) UUID cashierSessionId) {
        return collections.list(new PaymentListFilters(academicSessionId, status, channelCode, fromDate, toDate,
                studentId, reference, cashierSessionId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.canAction('PAYMENT_VIEW')")
    public PaymentView detail(@PathVariable UUID id) { return collections.detail(id); }
}
