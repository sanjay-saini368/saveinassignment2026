package com.assement.finance.domain;


import com.assement.finance.Enums.AllocationStrategy;
import com.assement.finance.Enums.LoanStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "loans", indexes = {
        @Index(name = "idx_loan_loan_id", columnList = "loan_id", unique = true),
        @Index(name = "idx_loan_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loan_id", nullable = false, unique = true, length = 50)
    private String loanId;

    @Column(name = "borrower_name", length = 255)
    private String borrowerName;

    @Column(name = "principal_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal principalAmount;

    @Column(name = "interest_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal interestAmount;

    @Column(name = "tenure_months", nullable = false)
    private Integer tenureMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LoanStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_strategy", nullable = false, length = 10)
    @Builder.Default
    private AllocationStrategy allocationStrategy = AllocationStrategy.CIP;

    @Version
    @Column(name = "version")
    private Long version; // Optimistic locking

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("dueDate ASC")
    @Builder.Default
    private List<EmiSchedule> emiSchedules = new ArrayList<>();

    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<LoanCharge> charges = new ArrayList<>();

    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Repayment> repayments = new ArrayList<>();

    // Convenience helpers
    public void addEmiSchedule(EmiSchedule emi) {
        emi.setLoan(this);
        emiSchedules.add(emi);
    }

    public void addCharge(LoanCharge charge) {
        charge.setLoan(this);
        charges.add(charge);
    }
}