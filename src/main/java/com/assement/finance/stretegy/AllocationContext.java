package com.assement.finance.stretegy;

import com.assement.finance.domain.EmiSchedule;
import com.assement.finance.domain.LoanCharge;
import com.assement.finance.domain.RepaymentAllocation;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Mutable context passed through the allocation strategy pipeline.
 * Holds the remaining amount to allocate and accumulates ledger entries.
 */
@Getter
@Builder
public class AllocationContext {

    private final String loanId;
    private final String repaymentReferenceId;

    /** Remaining unallocated amount — decremented as allocation proceeds */
    private BigDecimal remaining;

    private final List<EmiSchedule> unpaidEmis;
    private final List<LoanCharge> outstandingCharges;

    @Builder.Default
    private final List<RepaymentAllocation> ledgerEntries = new ArrayList<>();

    public void deductRemaining(BigDecimal amount) {
        this.remaining = this.remaining.subtract(amount);
    }

    public void addLedgerEntry(RepaymentAllocation entry) {
        ledgerEntries.add(entry);
    }

    public boolean hasRemaining() {
        return remaining.compareTo(BigDecimal.ZERO) > 0;
    }
}