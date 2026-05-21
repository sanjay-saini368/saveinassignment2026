package com.assement.finance.DTO;


import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EmiScheduleRequest {

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal principalDue;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal interestDue;

    @NotNull
    private LocalDate dueDate;
}