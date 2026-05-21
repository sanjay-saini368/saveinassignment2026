package com.assement.finance.domain;


import com.assement.finance.Enums.AllocationComponent;
import com.assement.finance.Enums.AllocationEntityType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable ledger entry for every rupee allocated from a repayment.
 *
 * <p>Each record tracks:
 * <ul>
 *   <li>Which repayment this allocation belongs to</li>
 *   <li>The entity (EMI or LoanCharge) it was applied to</li>
 *   <li>The component (PRINCIPAL / INTEREST / CHARGE)</li>
 *   <li>The amount allocated</li>
 * </ul>
 * Records are never updated; reversals create compensating negative entries.
 */
@Entity
@Immutable
@Table(name = "repayment_allocations", indexes = {
        @Index(name = "idx_alloc_repayment_id", columnList = "repayment_id"),
        @Index(name = "idx_alloc_entity", columnList = "entity_type, entity_id"),
        @Index(name = "idx_alloc_loan_id", columnList = "loan_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepaymentAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repayment_id", nullable = false, foreignKey = @ForeignKey(name = "fk_alloc_repayment"))
    private Repayment repayment;

    @Column(name = "loan_id", nullable = false, length = 50)
    private String loanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 20)
    private AllocationEntityType entityType;

    /**
     * ID of the EMI (emiId) or LoanCharge (chargeId) being adjusted.
     */
    @Column(name = "entity_id", nullable = false, length = 50)
    private String entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "component", nullable = false, length = 15)
    private AllocationComponent component;

    /**
     * Positive for original allocation; negative for reversal.
     */
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}