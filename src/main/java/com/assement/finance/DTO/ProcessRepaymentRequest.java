package com.assement.finance.DTO;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProcessRepaymentRequest {

    @NotBlank(message = "Loan ID is required")
    private String loanId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Repayment amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Payment date is required")
    private LocalDateTime paymentDate;

    @NotBlank(message = "Reference ID is required")
    @Size(max = 100)
    private String referenceId;
}