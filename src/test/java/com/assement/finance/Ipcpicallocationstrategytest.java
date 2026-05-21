package com.assement.finance;


import com.assement.finance.Enums.*;
import com.assement.finance.domain.EmiSchedule;
import com.assement.finance.domain.LoanCharge;
import com.assement.finance.stretegy.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IpcPicAllocationStrategyTest {

    private EmiSchedule emi(String id, double principal, double interest) {
        return EmiSchedule.builder()
                .emiId(id).emiNumber(1)
                .principalDue(BigDecimal.valueOf(principal))
                .interestDue(BigDecimal.valueOf(interest))
                .dueDate(LocalDate.now().plusMonths(1))
                .status(EmiStatus.PENDING)
                .build();
    }

    private LoanCharge charge(String id, double amount) {
        return LoanCharge.builder()
                .chargeId(id).chargeType(ChargeType.BOUNCE_CHARGE)
                .amount(BigDecimal.valueOf(amount))
                .status(ChargeStatus.OUTSTANDING)
                .build();
    }

    @Test
    @DisplayName("IPC: interest first, then principal, then charges")
    void ipc_allocatesInterestFirst() {
        LoanCharge c1 = charge("CHG-1", 500);
        EmiSchedule e1 = emi("EMI-1", 15000, 3000);

        AllocationContext ctx = AllocationContext.builder()
                .loanId("LN001").repaymentReferenceId("PAY-IPC")
                .remaining(BigDecimal.valueOf(5000))
                .outstandingCharges(List.of(c1))
                .unpaidEmis(List.of(e1))
                .build();

        new IpcAllocationStrategy().allocate(ctx);

        // Interest fully covered (3000), then principal (2000), charges last
        assertThat(e1.getInterestPaid()).isEqualByComparingTo("3000");
        assertThat(e1.getPrincipalPaid()).isEqualByComparingTo("2000");
        assertThat(c1.getAmountPaid()).isEqualByComparingTo("0"); // nothing left for charges
        assertThat(ctx.getRemaining()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("IPC: charges paid after EMI components")
    void ipc_chargesPaidLast() {
        LoanCharge c1 = charge("CHG-1", 500);
        EmiSchedule e1 = emi("EMI-1", 2000, 1000);

        AllocationContext ctx = AllocationContext.builder()
                .loanId("LN001").repaymentReferenceId("PAY-IPC2")
                .remaining(BigDecimal.valueOf(4000))
                .outstandingCharges(List.of(c1))
                .unpaidEmis(List.of(e1))
                .build();

        new IpcAllocationStrategy().allocate(ctx);

        assertThat(e1.isFullyPaid()).isTrue(); // 3000 total
        assertThat(c1.getAmountPaid()).isEqualByComparingTo("500");
        assertThat(ctx.getRemaining()).isEqualByComparingTo("500"); // 4000 - 3000 - 500
    }

    @Test
    @DisplayName("PIC: principal first, then interest, then charges")
    void pic_allocatesPrincipalFirst() {
        LoanCharge c1 = charge("CHG-1", 500);
        EmiSchedule e1 = emi("EMI-1", 15000, 3000);

        AllocationContext ctx = AllocationContext.builder()
                .loanId("LN001").repaymentReferenceId("PAY-PIC")
                .remaining(BigDecimal.valueOf(10000))
                .outstandingCharges(List.of(c1))
                .unpaidEmis(List.of(e1))
                .build();

        new PicAllocationStrategy().allocate(ctx);

        // Principal covered first (10000), interest next (0 left), charges last (0 left)
        assertThat(e1.getPrincipalPaid()).isEqualByComparingTo("10000");
        assertThat(e1.getInterestPaid()).isEqualByComparingTo("0");
        assertThat(c1.getAmountPaid()).isEqualByComparingTo("0");
        assertThat(ctx.getRemaining()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Strategy factory resolves all three strategies")
    void strategyFactory_resolvesAllStrategies() {
        AllocationStrategyFactory factory = new AllocationStrategyFactory(
                List.of(new CipAllocationStrategy(), new IpcAllocationStrategy(), new PicAllocationStrategy()));

        assertThat(factory.resolve(AllocationStrategy.CIP)).isInstanceOf(CipAllocationStrategy.class);
        assertThat(factory.resolve(AllocationStrategy.IPC)).isInstanceOf(IpcAllocationStrategy.class);
        assertThat(factory.resolve(AllocationStrategy.PIC)).isInstanceOf(PicAllocationStrategy.class);
    }
}