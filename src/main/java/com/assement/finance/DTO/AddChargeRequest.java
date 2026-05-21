package com.assement.finance.DTO;


import com.assement.finance.Enums.ChargeType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.annotations.BatchSize;


import java.math.BigDecimal;

@Data
public class AddChargeRequest {

    @NotNull(message = "Charge type is required")
    private ChargeType chargeType;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Charge amount must be positive")
    private BigDecimal amount;

//    @BatchSize(max = 500)
    private String remarks;
}