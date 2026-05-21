package com.assement.finance.DTO;


import com.assement.finance.Enums.AllocationStrategy;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateLoanRequest {

    @NotBlank(message = "Loan ID is required")
    private String loanId;

    private String borrowerName;

    @NotNull(message = "Principal amount is required")
    @DecimalMin(value = "1.00", message = "Principal must be positive")
    private BigDecimal principalAmount;

    @NotNull(message = "Interest amount is required")
    @DecimalMin(value = "0.00")
    private BigDecimal interestAmount;

    @NotNull(message = "Tenure is required")
    @Min(value = 1, message = "Tenure must be at least 1 month")
    private Integer tenureMonths;

    private AllocationStrategy allocationStrategy = AllocationStrategy.CIP;

//    @NotEmpty(message = "At least one EMI schedule is required")
//    @Valid
    private List<EmiScheduleRequest> emiSchedules;
}