package com.bbc.sms.finance.collections;

import java.util.List;
import java.util.UUID;

/** Integer-XAF allocation policy shared by quote/post flows: oldest due first, then credit. */
public final class CollectionAllocationPolicy {
    private CollectionAllocationPolicy() { }

    public record DueInstallment(UUID installmentId, long outstandingMinor) { }
    public record Allocation(UUID installmentId, long amountMinor) { }
    public record Proposal(List<Allocation> allocations, long allocatedMinor, long creditMinor) { }

    public static Proposal oldestDue(long receivedMinor, List<DueInstallment> installments) {
        if (receivedMinor < 0) throw new IllegalArgumentException("receivedMinor must not be negative");
        long remaining = receivedMinor;
        long allocated = 0;
        java.util.ArrayList<Allocation> result = new java.util.ArrayList<>();
        for (DueInstallment installment : installments == null ? List.<DueInstallment>of() : installments) {
            if (installment == null || installment.installmentId() == null || installment.outstandingMinor() < 0) {
                throw new IllegalArgumentException("installment must have a non-negative outstanding amount");
            }
            if (remaining == 0) break;
            long applied = Math.min(remaining, installment.outstandingMinor());
            if (applied > 0) {
                result.add(new Allocation(installment.installmentId(), applied));
                allocated += applied;
                remaining -= applied;
            }
        }
        return new Proposal(List.copyOf(result), allocated, remaining);
    }
}
