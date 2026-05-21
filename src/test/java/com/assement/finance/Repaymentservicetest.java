package com.assement.finance;


import com.assement.finance.DTO.ProcessRepaymentRequest;
import com.assement.finance.DTO.RepaymentResponse;
import com.assement.finance.DTO.ReversalRequest;
import com.assement.finance.Enums.AllocationStrategy;
import com.assement.finance.Enums.EmiStatus;
import com.assement.finance.Enums.LoanStatus;
import com.assement.finance.Enums.RepaymentStatus;
import com.assement.finance.domain.EmiSchedule;
import com.assement.finance.domain.Loan;
import com.assement.finance.domain.Repayment;
import com.assement.finance.exception.BusinessException;
import com.assement.finance.repository.*;
import com.assement.finance.service.RepaymentServiceImpl;
import com.assement.finance.stretegy.AllocationStrategyFactory;
import com.assement.finance.stretegy.CipAllocationStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepaymentServiceTest {

    @Mock
    LoanRepository loanRepository;
    @Mock
    EmiScheduleRepository emiScheduleRepository;
    @Mock
    LoanChargeRepository loanChargeRepository;
    @Mock
    RepaymentRepository repaymentRepository;
    @Mock
    RepaymentAllocationRepository allocationRepository;
    @Mock
    AllocationStrategyFactory strategyFactory;

    @InjectMocks
    RepaymentServiceImpl repaymentService;

    private Loan activeLoan;
    private EmiSchedule emi1;
    private ProcessRepaymentRequest repaymentRequest;

    @BeforeEach
    void setUp() {
        activeLoan = Loan.builder()
                .id(1L)
                .loanId("LN001")
                .principalAmount(BigDecimal.valueOf(90000))
                .interestAmount(BigDecimal.valueOf(18000))
                .tenureMonths(6)
                .status(LoanStatus.ACTIVE)
                .allocationStrategy(AllocationStrategy.CIP)
                .build();

        emi1 = EmiSchedule.builder()
                .emiId("LN001-EMI-1")
                .emiNumber(1)
                .loan(activeLoan)
                .principalDue(BigDecimal.valueOf(15000))
                .interestDue(BigDecimal.valueOf(3000))
                .dueDate(LocalDate.of(2026, 6, 5))
                .status(EmiStatus.PENDING)
                .build();

        repaymentRequest = new ProcessRepaymentRequest();
        repaymentRequest.setLoanId("LN001");
        repaymentRequest.setAmount(BigDecimal.valueOf(10000));
        repaymentRequest.setPaymentDate(LocalDateTime.of(2026, 5, 20, 10, 0));
        repaymentRequest.setReferenceId("PAY123");
    }

    @Test
    @DisplayName("processRepayment: idempotent — duplicate referenceId returns existing record")
    void processRepayment_duplicate_returnsExisting() {
        Repayment existing = Repayment.builder()
                .referenceId("PAY123")
                .loan(activeLoan)
                .amount(BigDecimal.valueOf(10000))
                .paymentDate(LocalDateTime.now())
                .status(RepaymentStatus.SUCCESS)
                .build();

        when(repaymentRepository.existsByReferenceId("PAY123")).thenReturn(true);
        when(repaymentRepository.findByReferenceId("PAY123")).thenReturn(Optional.of(existing));
        when(allocationRepository.findByRepaymentReferenceId("PAY123")).thenReturn(List.of());

        RepaymentResponse response = repaymentService.processRepayment(repaymentRequest);

        assertThat(response.getReferenceId()).isEqualTo("PAY123");
        verify(loanRepository, never()).findByLoanIdWithLock(any());
    }

    @Test
    @DisplayName("processRepayment: throws for closed loan")
    void processRepayment_closedLoan_throws() {
        activeLoan.setStatus(LoanStatus.CLOSED);
        when(repaymentRepository.existsByReferenceId("PAY123")).thenReturn(false);
        when(loanRepository.findByLoanIdWithLock("LN001")).thenReturn(Optional.of(activeLoan));

        assertThatThrownBy(() -> repaymentService.processRepayment(repaymentRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("closed");
    }

    @Test
    @DisplayName("processRepayment: processes successfully with CIP strategy")
    void processRepayment_success() {
        CipAllocationStrategy cipStrategy = new CipAllocationStrategy();
        when(repaymentRepository.existsByReferenceId("PAY123")).thenReturn(false);
        when(loanRepository.findByLoanIdWithLock("LN001")).thenReturn(Optional.of(activeLoan));
        when(emiScheduleRepository.findUnpaidByLoanIdOrderByDueDate("LN001")).thenReturn(List.of(emi1));
        when(loanChargeRepository.findOutstandingByLoanIdOrderByCreatedAt("LN001")).thenReturn(List.of());
        when(strategyFactory.resolve(AllocationStrategy.CIP)).thenReturn(cipStrategy);
        when(repaymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(emiScheduleRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(loanChargeRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(loanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repaymentRepository.findByReferenceId("PAY123")).thenAnswer(inv -> {
            Repayment r = Repayment.builder()
                    .referenceId("PAY123")
                    .loan(activeLoan)
                    .amount(BigDecimal.valueOf(10000))
                    .paymentDate(LocalDateTime.now())
                    .status(RepaymentStatus.SUCCESS)
                    .build();
            return Optional.of(r);
        });
        when(allocationRepository.findByRepaymentReferenceId("PAY123")).thenReturn(List.of());

        RepaymentResponse response = repaymentService.processRepayment(repaymentRequest);

        assertThat(response.getStatus()).isEqualTo(RepaymentStatus.SUCCESS);
        assertThat(response.getLoanId()).isEqualTo("LN001");
        // EMI interest should be fully paid, principal partially
        assertThat(emi1.getInterestPaid()).isEqualByComparingTo("3000");
        assertThat(emi1.getPrincipalPaid()).isEqualByComparingTo("7000");
    }

    @Test
    @DisplayName("reverseRepayment: throws if already reversed")
    void reverseRepayment_alreadyReversed_throws() {
        Repayment reversed = Repayment.builder()
                .referenceId("PAY123")
                .loan(activeLoan)
                .amount(BigDecimal.valueOf(10000))
                .paymentDate(LocalDateTime.now())
                .status(RepaymentStatus.REVERSED)
                .build();

        when(repaymentRepository.findByReferenceId("PAY123")).thenReturn(Optional.of(reversed));

        assertThatThrownBy(() -> repaymentService.reverseRepayment("PAY123", new ReversalRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already reversed");
    }

    @Test
    @DisplayName("reverseRepayment: throws if repayment not found")
    void reverseRepayment_notFound_throws() {
        when(repaymentRepository.findByReferenceId("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> repaymentService.reverseRepayment("UNKNOWN", new ReversalRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("processRepayment: loan status set to CLOSED when fully repaid")
    void processRepayment_fullRepayment_closesLoan() {
        // EMI with small amounts that will be fully covered
        EmiSchedule smallEmi = EmiSchedule.builder()
                .emiId("LN001-EMI-1")
                .emiNumber(1)
                .loan(activeLoan)
                .principalDue(BigDecimal.valueOf(1000))
                .interestDue(BigDecimal.valueOf(200))
                .dueDate(LocalDate.of(2026, 6, 5))
                .status(EmiStatus.PENDING)
                .build();

        repaymentRequest.setAmount(BigDecimal.valueOf(1200));

        when(repaymentRepository.existsByReferenceId("PAY123")).thenReturn(false);
        when(loanRepository.findByLoanIdWithLock("LN001")).thenReturn(Optional.of(activeLoan));
        when(emiScheduleRepository.findUnpaidByLoanIdOrderByDueDate("LN001")).thenReturn(List.of(smallEmi));
        when(loanChargeRepository.findOutstandingByLoanIdOrderByCreatedAt("LN001")).thenReturn(List.of());
        when(strategyFactory.resolve(AllocationStrategy.CIP)).thenReturn(new CipAllocationStrategy());
        when(repaymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(emiScheduleRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(loanChargeRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(loanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repaymentRepository.findByReferenceId("PAY123")).thenReturn(Optional.of(
                Repayment.builder().referenceId("PAY123").loan(activeLoan)
                        .amount(BigDecimal.valueOf(1200)).paymentDate(LocalDateTime.now())
                        .status(RepaymentStatus.SUCCESS).build()));
        when(allocationRepository.findByRepaymentReferenceId("PAY123")).thenReturn(List.of());

        repaymentService.processRepayment(repaymentRequest);

        assertThat(activeLoan.getStatus()).isEqualTo(LoanStatus.CLOSED);
    }
}