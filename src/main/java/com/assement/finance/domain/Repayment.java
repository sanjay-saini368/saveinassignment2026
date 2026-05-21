package com.assement.finance.domain;

import com.assement.finance.Enums.RepaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "repayments", indexes = {
        @Index(name = "idx_repayment_ref_id", columnList = "reference_id", unique = true),
        @Index(name = "idx_repayment_loan_id", columnList = "loan_id"),
        @Index(name = "idx_repayment_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Repayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_id", nullable = false, unique = true, length = 100)
    private String referenceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false, foreignKey = @ForeignKey(name = "fk_repayment_loan"))
    private Loan loan;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDateTime paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private RepaymentStatus status = RepaymentStatus.SUCCESS;

    /**
     * Reference to the original repayment this reversal is linked to.
     * Null for original repayments.
     */
    @Column(name = "reversed_repayment_id", length = 100)
    private String reversedRepaymentId;

    @Column(name = "reversal_reason", length = 500)
    private String reversalReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "repayment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RepaymentAllocation> allocations = new ArrayList<>();

    public boolean isReversed() {
        return RepaymentStatus.REVERSED.equals(status);
    }
}