package com.bbc.sms.finance.collections;

import com.bbc.sms.finance.FinancePolicyService;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentCorrectionPreviewTest {
    final UUID school = UUID.randomUUID(), maker = UUID.randomUUID();
    final FinancePaymentRepository payments = mock(FinancePaymentRepository.class);
    final PaymentAllocationRepository allocations = mock(PaymentAllocationRepository.class);
    final StudentCreditLedgerRepository credits = mock(StudentCreditLedgerRepository.class);
    final RefundTransactionRepository refunds = mock(RefundTransactionRepository.class);
    final PaymentCorrectionService service = new PaymentCorrectionService(payments, allocations, credits,
            null, null, refunds, null, null, null, null, null, null, null, null, null, null, null,
            mock(FinancePolicyService.class));
    final FinancePayment payment = new FinancePayment();

    @BeforeEach void setup() {
        TenantContext.set(school);
        payment.setId(UUID.randomUUID()); payment.setSchoolId(school);
        payment.setStudentId(UUID.randomUUID()); payment.setCreatedBy(maker);
        payment.setStatus("POSTED"); payment.setAmountMinor(10000);
        when(payments.findByIdAndSchoolId(payment.getId(), school)).thenReturn(Optional.of(payment));
    }
    @AfterEach void clear() { TenantContext.clear(); SecurityContextHolder.clearContext(); }
    void authenticate(UUID id) {
        var p = new AppUserPrincipal(id, school, "audit", "accountant", "Audit", "QA");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }
    @Test void makerPreviewExplainsWhyCorrectionIsForbidden() {
        authenticate(maker);
        var result = service.reversalPreview(payment.getId());
        assertThat(result.allowed()).isFalse();
        assertThat(result.blockers()).extracting(CollectionDtos.BlockerView::code)
                .contains("PAYMENT_MAKER_CANNOT_REVERSE");
    }
    @Test void independentAuthorizedReviewerCanPreviewCorrection() {
        authenticate(UUID.randomUUID());
        assertThat(service.reversalPreview(payment.getId()).allowed()).isTrue();
    }
}
