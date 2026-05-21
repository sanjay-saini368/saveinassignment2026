package com.assement.finance.repository;

import com.assement.finance.domain.Repayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RepaymentRepository extends JpaRepository<Repayment, Long> {

    Optional<Repayment> findByReferenceId(String referenceId);

    boolean existsByReferenceId(String referenceId);
}