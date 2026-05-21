package com.assement.finance.domain;

import com.assement.finance.Enums.EmiStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "emi_schedules", indexes = {
        @Index(name = "idx_emi_emi_id", columnList = "emi_id", unique = true),
        @Index(name = "idx_emi_loan_id", columnList = "loan_id"),
        @Index(name = "idx_emi_due_date", columnList = "due_date"),
        @Index(name = "idx_emi_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmiSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "emi_id", nullable = false, unique = true, length = 50)
    private String emiId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false, foreignKey = @ForeignKey(name = "fk_emi_loan"))
    private Loan loan;

    @Column(name = "emi_number", nullable = false)
    private Integer emiNumber;

    // Principal tracking
    @Column(name = "principal_due", nullable = false, precision = 15, scale = 2)
    private BigDecimal principalDue;

    @Column(name = "principal_paid", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal principalPaid = BigDecimal.ZERO;

    // Interest tracking
    @Column(name = "interest_due", nullable = false, precision = 15, scale = 2)
    private BigDecimal interestDue;

    @Column(name = "interest_paid", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal interestPaid = BigDecimal.ZERO;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private EmiStatus status = EmiStatus.PENDING;

    @Version
    @Column(name = "version")
    private Long version; // Optimistic locking

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Derived calculations
    public BigDecimal getPrincipalOutstanding() {
        return principalDue.subtract(principalPaid);
    }

    public BigDecimal getInterestOutstanding() {
        return interestDue.subtract(interestPaid);
    }

    public BigDecimal getTotalDue() {
        return principalDue.add(interestDue);
    }

    public BigDecimal getTotalOutstanding() {
        return getPrincipalOutstanding().add(getInterestOutstanding());
    }

    public boolean isFullyPaid() {
        return getPrincipalOutstanding().compareTo(BigDecimal.ZERO) == 0
                && getInterestOutstanding().compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isOverdue() {
        return dueDate.isBefore(LocalDate.now()) && !isFullyPaid();
    }
}