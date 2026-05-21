package com.assement.finance.stretegy;


import com.assement.finance.Enums.AllocationStrategy;

/**
 * Strategy interface for repayment allocation.
 *
 * <p>Each implementation defines the order in which
 * Charges, Interest, and Principal are satisfied.
 */
public interface RepaymentAllocationStrategy {

    AllocationStrategy getStrategy();

    /**
     * Execute allocation, mutating the context in-place.
     *
     * @param context mutable allocation context
     */
    void allocate(AllocationContext context);
}