package com.assement.finance.repository;

import com.assement.finance.domain.RepaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RepaymentAllocationRepository extends JpaRepository<RepaymentAllocation, Long> {

    List<RepaymentAllocation> findByRepaymentReferenceId(String referenceId);

    List<RepaymentAllocation> findByLoanId(String loanId);
}