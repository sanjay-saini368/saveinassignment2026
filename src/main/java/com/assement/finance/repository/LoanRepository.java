package com.assement.finance.repository;

import com.assement.finance.domain.Loan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    Optional<Loan> findByLoanId(String loanId);

    boolean existsByLoanId(String loanId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Loan l WHERE l.loanId = :loanId")
    Optional<Loan> findByLoanIdWithLock(@Param("loanId") String loanId);
}