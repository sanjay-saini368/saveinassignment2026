package com.assement.finance.stretegy;

import com.assement.finance.Enums.AllocationStrategy;
import org.springframework.stereotype.Component;

/**
 * IPC Strategy: Interest → Principal → Charges
 */
@Component
public class IpcAllocationStrategy extends BaseAllocationStrategy {

    @Override
    public AllocationStrategy getStrategy() {
        return AllocationStrategy.IPC;
    }

    @Override
    public void allocate(AllocationContext context) {
        allocateToInterest(context);
        allocateToPrincipal(context);
        allocateToCharges(context);
    }
}