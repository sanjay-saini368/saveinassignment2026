package com.assement.finance.DTO;


import com.assement.finance.Enums.AllocationComponent;
import com.assement.finance.Enums.AllocationEntityType;
import com.assement.finance.Enums.RepaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class RepaymentResponse {

    private String referenceId;
    private String loanId;
    private BigDecimal amount;
    private LocalDateTime paymentDate;
    private RepaymentStatus status;
    private BigDecimal unallocatedAmount;
    private List<AllocationEntry> allocations;

    @Data
    @Builder
    public static class AllocationEntry {
        private AllocationEntityType entityType;
        private String entityId;
        private AllocationComponent component;
        private BigDecimal amount;
    }
}