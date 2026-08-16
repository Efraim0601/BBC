package com.bbc.sms.finance.collections;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectionAllocationPolicyTest {
    @Test
    void allocatesOldestInstallmentsWithoutFloatingPointMoney() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        var proposal = CollectionAllocationPolicy.oldestDue(1_250,
                List.of(new CollectionAllocationPolicy.DueInstallment(first, 1_000),
                        new CollectionAllocationPolicy.DueInstallment(second, 500)));

        assertThat(proposal.allocations()).containsExactly(
                new CollectionAllocationPolicy.Allocation(first, 1_000),
                new CollectionAllocationPolicy.Allocation(second, 250));
        assertThat(proposal.allocatedMinor()).isEqualTo(1_250);
        assertThat(proposal.creditMinor()).isZero();
    }

    @Test
    void reportsOverpaymentAsStudentCredit() {
        UUID installment = UUID.randomUUID();

        var proposal = CollectionAllocationPolicy.oldestDue(1_500,
                List.of(new CollectionAllocationPolicy.DueInstallment(installment, 1_000)));

        assertThat(proposal.allocatedMinor()).isEqualTo(1_000);
        assertThat(proposal.creditMinor()).isEqualTo(500);
    }

    @Test
    void rejectsNegativeAmounts() {
        assertThatThrownBy(() -> CollectionAllocationPolicy.oldestDue(-1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
