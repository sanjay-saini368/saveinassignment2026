package com.assement.finance.stretegy;

import com.assement.finance.Enums.AllocationStrategy;
import org.springframework.stereotype.Component;

/**
 * PIC Strategy: Principal → Interest → Charges
 */
@Component
public class PicAllocationStrategy extends BaseAllocationStrategy {

    @Override
    public AllocationStrategy getStrategy() {
        return AllocationStrategy.PIC;
    }

    @Override
    public void allocate(AllocationContext context) {
        allocateToPrincipal(context);
        allocateToInterest(context);
        allocateToCharges(context);
    }
}