package com.assement.finance.DTO;

import com.assement.finance.Enums.ChargeStatus;
import com.assement.finance.Enums.ChargeType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ChargeResponse {

    private String chargeId;
    private ChargeType chargeType;
    private BigDecimal amount;
    private BigDecimal amountPaid;
    private BigDecimal outstandingAmount;
    private ChargeStatus status;
    private String remarks;
    private LocalDateTime createdAt;
}