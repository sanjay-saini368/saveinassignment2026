package com.assement.finance.stretegy;

import com.assement.finance.Enums.AllocationComponent;
import com.assement.finance.Enums.AllocationEntityType;
import com.assement.finance.Enums.ChargeStatus;
import com.assement.finance.Enums.EmiStatus;
import com.assement.finance.domain.EmiSchedule;
import com.assement.finance.domain.LoanCharge;
import com.assement.finance.domain.RepaymentAllocation;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

@Slf4j
public abstract class BaseAllocationStrategy implements RepaymentAllocationStrategy {

    /**
     * Apply remaining funds to all outstanding charges in chronological order.
     */
    protected void allocateToCharges(AllocationContext ctx) {
        for (LoanCharge charge : ctx.getOutstandingCharges()) {
            if (!ctx.hasRemaining()) break;

            BigDecimal outstanding = charge.getOutstandingAmount();
            if (outstanding.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal toApply = outstanding.min(ctx.getRemaining());

            charge.setAmountPaid(charge.getAmountPaid().add(toApply));
            charge.setStatus(charge.isFullyPaid() ? ChargeStatus.PAID : ChargeStatus.PARTIAL);
            ctx.deductRemaining(toApply);

            ctx.addLedgerEntry(RepaymentAllocation.builder()
                    .loanId(ctx.getLoanId())
                    .entityType(AllocationEntityType.LOAN_CHARGE)
                    .entityId(charge.getChargeId())
                    .component(AllocationComponent.CHARGE)
                    .amount(toApply)
                    .build());

            log.debug("Allocated {} to charge {} ({})", toApply, charge.getChargeId(), charge.getChargeType());
        }
    }

    /**
     * Apply remaining funds to EMI interest across unpaid EMIs (chronological).
     */
    protected void allocateToInterest(AllocationContext ctx) {
        for (EmiSchedule emi : ctx.getUnpaidEmis()) {
            if (!ctx.hasRemaining()) break;

            BigDecimal interestOutstanding = emi.getInterestOutstanding();
            if (interestOutstanding.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal toApply = interestOutstanding.min(ctx.getRemaining());

            emi.setInterestPaid(emi.getInterestPaid().add(toApply));
            ctx.deductRemaining(toApply);
            updateEmiStatus(emi);

            ctx.addLedgerEntry(RepaymentAllocation.builder()
                    .loanId(ctx.getLoanId())
                    .entityType(AllocationEntityType.EMI)
                    .entityId(emi.getEmiId())
                    .component(AllocationComponent.INTEREST)
                    .amount(toApply)
                    .build());

            log.debug("Allocated {} to EMI {} interest", toApply, emi.getEmiId());
        }
    }

    /**
     * Apply remaining funds to EMI principal across unpaid EMIs (chronological).
     */
    protected void allocateToPrincipal(AllocationContext ctx) {
        for (EmiSchedule emi : ctx.getUnpaidEmis()) {
            if (!ctx.hasRemaining()) break;

            BigDecimal principalOutstanding = emi.getPrincipalOutstanding();
            if (principalOutstanding.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal toApply = principalOutstanding.min(ctx.getRemaining());

            emi.setPrincipalPaid(emi.getPrincipalPaid().add(toApply));
            ctx.deductRemaining(toApply);
            updateEmiStatus(emi);

            ctx.addLedgerEntry(RepaymentAllocation.builder()
                    .loanId(ctx.getLoanId())
                    .entityType(AllocationEntityType.EMI)
                    .entityId(emi.getEmiId())
                    .component(AllocationComponent.PRINCIPAL)
                    .amount(toApply)
                    .build());

            log.debug("Allocated {} to EMI {} principal", toApply, emi.getEmiId());
        }
    }

    private void updateEmiStatus(EmiSchedule emi) {
        if (emi.isFullyPaid()) {
            emi.setStatus(EmiStatus.PAID);
        } else if (emi.getPrincipalPaid().compareTo(BigDecimal.ZERO) > 0
                || emi.getInterestPaid().compareTo(BigDecimal.ZERO) > 0) {
            emi.setStatus(EmiStatus.PARTIAL);
        }
    }
}