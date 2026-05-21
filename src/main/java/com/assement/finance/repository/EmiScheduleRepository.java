package com.assement.finance.repository;


import com.assement.finance.domain.EmiSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmiScheduleRepository extends JpaRepository<EmiSchedule, Long> {

    List<EmiSchedule> findByLoanLoanIdOrderByDueDateAsc(String loanId);

    Optional<EmiSchedule> findByEmiId(String emiId);

    @Query("SELECT e FROM EmiSchedule e WHERE e.loan.loanId = :loanId AND e.status != 'PAID' ORDER BY e.dueDate ASC")
    List<EmiSchedule> findUnpaidByLoanIdOrderByDueDate(@Param("loanId") String loanId);

    @Query("SELECT e FROM EmiSchedule e WHERE e.loan.loanId = :loanId AND e.dueDate < :today AND e.status != 'PAID'")
    List<EmiSchedule> findOverdueByLoanId(@Param("loanId") String loanId, @Param("today") LocalDate today);

    @Query("SELECT e FROM EmiSchedule e WHERE e.loan.loanId = :loanId ORDER BY e.dueDate ASC LIMIT 1")
    Optional<EmiSchedule> findNextDueEmiByLoanId(@Param("loanId") String loanId);
}