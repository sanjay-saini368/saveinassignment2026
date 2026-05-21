package com.assement.finance.stretegy;

import com.assement.finance.Enums.AllocationStrategy;
import org.springframework.stereotype.Component;

/**
 * CIP Strategy: Charges → Interest → Principal
 *
 * This is the default strategy. It ensures outstanding loan-level charges
 * are cleared first before touching EMI components, which minimises
 * accrual of further penal charges.
 */
@Component
public class CipAllocationStrategy extends BaseAllocationStrategy {

    @Override
    public AllocationStrategy getStrategy() {
        return AllocationStrategy.CIP;
    }

    @Override
    public void allocate(AllocationContext context) {
        allocateToCharges(context);
        allocateToInterest(context);
        allocateToPrincipal(context);
    }
}