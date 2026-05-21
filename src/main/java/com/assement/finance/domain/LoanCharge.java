package com.assement.finance.domain;

import com.assement.finance.Enums.ChargeStatus;
import com.assement.finance.Enums.ChargeType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "loan_charges", indexes = {
        @Index(name = "idx_charge_charge_id", columnList = "charge_id", unique = true),
        @Index(name = "idx_charge_loan_id", columnList = "loan_id"),
        @Index(name = "idx_charge_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "charge_id", nullable = false, unique = true, length = 50)
    private String chargeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false, foreignKey = @ForeignKey(name = "fk_charge_loan"))
    private Loan loan;

    @Enumerated(EnumType.STRING)
    @Column(name = "charge_type", nullable = false, length = 30)
    private ChargeType chargeType;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "amount_paid", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ChargeStatus status = ChargeStatus.OUTSTANDING;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Version
    @Column(name = "version")
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Derived
    public BigDecimal getOutstandingAmount() {
        return amount.subtract(amountPaid);
    }

    public boolean isFullyPaid() {
        return getOutstandingAmount().compareTo(BigDecimal.ZERO) == 0;
    }
}