package com.assement.finance.DTO;

import com.assement.finance.Enums.AllocationStrategy;
import com.assement.finance.Enums.LoanStatus;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class LoanSummaryResponse {

    private String loanId;
    private String borrowerName;
    private LoanStatus status;
    private AllocationStrategy allocationStrategy;

    // Totals
    private BigDecimal totalPrincipal;
    private BigDecimal totalInterest;
    private BigDecimal totalCharges;

    // Outstanding
    private BigDecimal principalOutstanding;
    private BigDecimal interestOutstanding;
    private BigDecimal chargesOutstanding;
    private BigDecimal totalOutstanding;

    // Overdue
    private BigDecimal totalOverdueAmount;

    // Next EMI
    private String nextEmiId;
    private LocalDate nextEmiDueDate;
    private BigDecimal nextEmiAmount;
}