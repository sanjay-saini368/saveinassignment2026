package com.assement.finance;


import com.assement.finance.Enums.*;
import com.assement.finance.domain.EmiSchedule;
import com.assement.finance.domain.LoanCharge;
import com.assement.finance.stretegy.AllocationContext;
import com.assement.finance.stretegy.CipAllocationStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CipAllocationStrategyTest {

    private CipAllocationStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new CipAllocationStrategy();
    }

    private EmiSchedule emi(String id, double principal, double interest) {
        return EmiSchedule.builder()
                .emiId(id)
                .emiNumber(1)
                .principalDue(BigDecimal.valueOf(principal))
                .interestDue(BigDecimal.valueOf(interest))
                .dueDate(LocalDate.now().plusMonths(1))
                .status(EmiStatus.PENDING)
                .build();
    }

    private LoanCharge charge(String id, double amount) {
        return LoanCharge.builder()
                .chargeId(id)
                .chargeType(ChargeType.BOUNCE_CHARGE)
                .amount(BigDecimal.valueOf(amount))
                .status(ChargeStatus.OUTSTANDING)
                .build();
    }

    @Test
    @DisplayName("CIP: full payment covers charges → interest → principal")
    void fullPayment_coversCIP() {
        LoanCharge c1 = charge("CHG-1", 500);
        LoanCharge c2 = charge("CHG-2", 1000);
        EmiSchedule e1 = emi("EMI-1", 15000, 3000);

        AllocationContext ctx = AllocationContext.builder()
                .loanId("LN001")
                .repaymentReferenceId("PAY1")
                .remaining(BigDecimal.valueOf(10000))
                .outstandingCharges(List.of(c1, c2))
                .unpaidEmis(List.of(e1))
                .build();

        strategy.allocate(ctx);

        // Charges fully paid
        assertThat(c1.getAmountPaid()).isEqualByComparingTo("500");
        assertThat(c1.getStatus()).isEqualTo(ChargeStatus.PAID);
        assertThat(c2.getAmountPaid()).isEqualByComparingTo("1000");
        assertThat(c2.getStatus()).isEqualTo(ChargeStatus.PAID);

        // EMI-1 interest fully paid
        assertThat(e1.getInterestPaid()).isEqualByComparingTo("3000");

        // EMI-1 principal partially paid: 10000 - 1500 (charges) - 3000 (interest) = 5500
        assertThat(e1.getPrincipalPaid()).isEqualByComparingTo("5500");
        assertThat(e1.getStatus()).isEqualTo(EmiStatus.PARTIAL);

        // Nothing remaining
        assertThat(ctx.getRemaining()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("CIP: partial payment — only covers charges and part of interest")
    void partialPayment_coversChargesAndPartialInterest() {
        LoanCharge c1 = charge("CHG-1", 500);
        EmiSchedule e1 = emi("EMI-1", 15000, 3000);

        AllocationContext ctx = AllocationContext.builder()
                .loanId("LN001")
                .repaymentReferenceId("PAY2")
                .remaining(BigDecimal.valueOf(2000))
                .outstandingCharges(List.of(c1))
                .unpaidEmis(List.of(e1))
                .build();

        strategy.allocate(ctx);

        assertThat(c1.getAmountPaid()).isEqualByComparingTo("500");
        assertThat(e1.getInterestPaid()).isEqualByComparingTo("1500"); // 2000 - 500
        assertThat(e1.getPrincipalPaid()).isEqualByComparingTo("0");
        assertThat(ctx.getRemaining()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("CIP: overpayment — unallocated remainder stays in context")
    void overpayment_leavesRemainder() {
        EmiSchedule e1 = emi("EMI-1", 5000, 1000);

        AllocationContext ctx = AllocationContext.builder()
                .loanId("LN001")
                .repaymentReferenceId("PAY3")
                .remaining(BigDecimal.valueOf(10000))
                .outstandingCharges(List.of())
                .unpaidEmis(List.of(e1))
                .build();

        strategy.allocate(ctx);

        assertThat(e1.isFullyPaid()).isTrue();
        assertThat(ctx.getRemaining()).isEqualByComparingTo("4000"); // 10000 - 6000
    }

    @Test
    @DisplayName("CIP: allocation across multiple EMIs in chronological order")
    void multipleEmis_allocatedChronologically() {
        EmiSchedule e1 = emi("EMI-1", 15000, 3000);
        EmiSchedule e2 = emi("EMI-2", 15000, 3000);

        AllocationContext ctx = AllocationContext.builder()
                .loanId("LN001")
                .repaymentReferenceId("PAY4")
                .remaining(BigDecimal.valueOf(22000))
                .outstandingCharges(List.of())
                .unpaidEmis(List.of(e1, e2))
                .build();

        strategy.allocate(ctx);

        // EMI-1 fully paid (18000), EMI-2 gets remaining 4000
        assertThat(e1.isFullyPaid()).isTrue();
        assertThat(e2.getInterestPaid()).isEqualByComparingTo("3000"); // interest first
        assertThat(e2.getPrincipalPaid()).isEqualByComparingTo("1000");
        assertThat(ctx.getRemaining()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("CIP: ledger entries created correctly")
    void ledgerEntries_areCreated() {
        LoanCharge c1 = charge("CHG-1", 500);
        EmiSchedule e1 = emi("EMI-1", 15000, 3000);

        AllocationContext ctx = AllocationContext.builder()
                .loanId("LN001")
                .repaymentReferenceId("PAY5")
                .remaining(BigDecimal.valueOf(10000))
                .outstandingCharges(List.of(c1))
                .unpaidEmis(List.of(e1))
                .build();

        strategy.allocate(ctx);

        assertThat(ctx.getLedgerEntries()).hasSize(3);
        assertThat(ctx.getLedgerEntries().get(0).getComponent()).isEqualTo(AllocationComponent.CHARGE);
        assertThat(ctx.getLedgerEntries().get(1).getComponent()).isEqualTo(AllocationComponent.INTEREST);
        assertThat(ctx.getLedgerEntries().get(2).getComponent()).isEqualTo(AllocationComponent.PRINCIPAL);
    }

    @Test
    @DisplayName("CIP: zero payment produces no ledger entries")
    void zeroPayment_noEntries() {
        EmiSchedule e1 = emi("EMI-1", 15000, 3000);

        AllocationContext ctx = AllocationContext.builder()
                .loanId("LN001")
                .repaymentReferenceId("PAY6")
                .remaining(BigDecimal.ZERO)
                .outstandingCharges(List.of())
                .unpaidEmis(List.of(e1))
                .build();

        strategy.allocate(ctx);

        assertThat(ctx.getLedgerEntries()).isEmpty();
        assertThat(e1.getPrincipalPaid()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Strategy type is CIP")
    void strategyType() {
        assertThat(strategy.getStrategy()).isEqualTo(AllocationStrategy.CIP);
    }
}