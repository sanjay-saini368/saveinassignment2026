package com.assement.finance.repository;


import com.assement.finance.domain.LoanCharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LoanChargeRepository extends JpaRepository<LoanCharge, Long> {

    List<LoanCharge> findByLoanLoanId(String loanId);

    Optional<LoanCharge> findByChargeId(String chargeId);

    @Query("SELECT c FROM LoanCharge c WHERE c.loan.loanId = :loanId AND c.status IN ('OUTSTANDING', 'PARTIAL') ORDER BY c.createdAt ASC")
    List<LoanCharge> findOutstandingByLoanIdOrderByCreatedAt(@Param("loanId") String loanId);
}