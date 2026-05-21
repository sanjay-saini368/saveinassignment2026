package com.assement.finance.DTO;

import com.assement.finance.Enums.EmiStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class EmiSummaryResponse {

    private String emiId;
    private Integer emiNumber;
    private LocalDate dueDate;
    private EmiStatus status;

    // Principal
    private BigDecimal principalDue;
    private BigDecimal principalPaid;
    private BigDecimal principalOutstanding;

    // Interest
    private BigDecimal interestDue;
    private BigDecimal interestPaid;
    private BigDecimal interestOutstanding;

    // Totals
    private BigDecimal totalDue;
    private BigDecimal totalOutstanding;
}