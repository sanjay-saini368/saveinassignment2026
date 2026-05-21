package com.assement.finance;


import com.assement.finance.DTO.AddChargeRequest;
import com.assement.finance.DTO.CreateLoanRequest;
import com.assement.finance.DTO.EmiScheduleRequest;
import com.assement.finance.DTO.LoanSummaryResponse;
import com.assement.finance.Enums.AllocationStrategy;
import com.assement.finance.Enums.ChargeType;
import com.assement.finance.Enums.EmiStatus;
import com.assement.finance.Enums.LoanStatus;
import com.assement.finance.domain.EmiSchedule;
import com.assement.finance.domain.Loan;
import com.assement.finance.exception.BusinessException;
import com.assement.finance.exception.LoanNotFoundException;
import com.assement.finance.repository.EmiScheduleRepository;
import com.assement.finance.repository.LoanChargeRepository;
import com.assement.finance.repository.LoanRepository;
import com.assement.finance.service.LoanServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    @Mock
    LoanRepository loanRepository;
    @Mock
    EmiScheduleRepository emiScheduleRepository;
    @Mock
    LoanChargeRepository loanChargeRepository;

    @InjectMocks
    LoanServiceImpl loanService;

    private CreateLoanRequest validCreateRequest;

    @BeforeEach
    void setUp() {
        EmiScheduleRequest emi1 = new EmiScheduleRequest();
        emi1.setPrincipalDue(BigDecimal.valueOf(15000));
        emi1.setInterestDue(BigDecimal.valueOf(3000));
        emi1.setDueDate(LocalDate.of(2026, 6, 5));

        validCreateRequest = new CreateLoanRequest();
        validCreateRequest.setLoanId("LN001");
        validCreateRequest.setBorrowerName("John Doe");
        validCreateRequest.setPrincipalAmount(BigDecimal.valueOf(90000));
        validCreateRequest.setInterestAmount(BigDecimal.valueOf(18000));
        validCreateRequest.setTenureMonths(6);
        validCreateRequest.setAllocationStrategy(AllocationStrategy.CIP);
        validCreateRequest.setEmiSchedules(List.of(emi1));
    }

    @Test
    @DisplayName("createLoan: saves loan with EMI schedules")
    void createLoan_savesCorrectly() {
        when(loanRepository.existsByLoanId("LN001")).thenReturn(false);
        when(loanRepository.save(any(Loan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(emiScheduleRepository.findByLoanLoanIdOrderByDueDateAsc(any())).thenReturn(List.of());
        when(loanChargeRepository.findByLoanLoanId(any())).thenReturn(List.of());

        LoanSummaryResponse response = loanService.createLoan(validCreateRequest);

        assertThat(response.getLoanId()).isEqualTo("LN001");
        assertThat(response.getStatus()).isEqualTo(LoanStatus.ACTIVE);

        ArgumentCaptor<Loan> captor = ArgumentCaptor.forClass(Loan.class);
        verify(loanRepository).save(captor.capture());
        Loan saved = captor.getValue();
        assertThat(saved.getEmiSchedules()).hasSize(1);
        assertThat(saved.getEmiSchedules().get(0).getEmiId()).isEqualTo("LN001-EMI-1");
    }

    @Test
    @DisplayName("createLoan: throws when loan ID already exists")
    void createLoan_duplicateLoanId_throws() {
        when(loanRepository.existsByLoanId("LN001")).thenReturn(true);

        assertThatThrownBy(() -> loanService.createLoan(validCreateRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");

        verify(loanRepository, never()).save(any());
    }

    @Test
    @DisplayName("getLoanSummary: throws when loan not found")
    void getLoanSummary_notFound_throws() {
        when(loanRepository.findByLoanId("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.getLoanSummary("UNKNOWN"))
                .isInstanceOf(LoanNotFoundException.class);
    }

    @Test
    @DisplayName("getLoanSummary: calculates outstanding correctly")
    void getLoanSummary_calculatesOutstanding() {
        Loan loan = Loan.builder()
                .loanId("LN001")
                .principalAmount(BigDecimal.valueOf(90000))
                .interestAmount(BigDecimal.valueOf(18000))
                .tenureMonths(6)
                .status(LoanStatus.ACTIVE)
                .allocationStrategy(AllocationStrategy.CIP)
                .build();

        EmiSchedule emi = EmiSchedule.builder()
                .emiId("LN001-EMI-1")
                .emiNumber(1)
                .principalDue(BigDecimal.valueOf(15000))
                .interestDue(BigDecimal.valueOf(3000))
                .dueDate(LocalDate.of(2026, 6, 5))
                .status(EmiStatus.PENDING)
                .build();

        when(loanRepository.findByLoanId("LN001")).thenReturn(Optional.of(loan));
        when(emiScheduleRepository.findByLoanLoanIdOrderByDueDateAsc("LN001")).thenReturn(List.of(emi));
        when(loanChargeRepository.findByLoanLoanId("LN001")).thenReturn(List.of());

        LoanSummaryResponse summary = loanService.getLoanSummary("LN001");

        assertThat(summary.getPrincipalOutstanding()).isEqualByComparingTo("15000");
        assertThat(summary.getInterestOutstanding()).isEqualByComparingTo("3000");
        assertThat(summary.getTotalOutstanding()).isEqualByComparingTo("18000");
    }

    @Test
    @DisplayName("addCharge: throws when loan is CLOSED")
    void addCharge_closedLoan_throws() {
        Loan loan = Loan.builder()
                .loanId("LN001")
                .status(LoanStatus.CLOSED)
                .build();
        when(loanRepository.findByLoanId("LN001")).thenReturn(Optional.of(loan));

        AddChargeRequest req = new AddChargeRequest();
        req.setChargeType(ChargeType.BOUNCE_CHARGE);
        req.setAmount(BigDecimal.valueOf(500));

        assertThatThrownBy(() -> loanService.addCharge("LN001", req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("closed");
    }
}