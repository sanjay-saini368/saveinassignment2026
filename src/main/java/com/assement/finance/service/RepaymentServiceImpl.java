package com.assement.finance.service;

import com.assement.finance.DTO.ProcessRepaymentRequest;
import com.assement.finance.DTO.RepaymentResponse;
import com.assement.finance.DTO.ReversalRequest;
import com.assement.finance.Enums.ChargeStatus;
import com.assement.finance.Enums.EmiStatus;
import com.assement.finance.Enums.LoanStatus;
import com.assement.finance.Enums.RepaymentStatus;
import com.assement.finance.domain.*;
import com.assement.finance.exception.BusinessException;
import com.assement.finance.exception.LoanNotFoundException;
import com.assement.finance.repository.*;
import com.assement.finance.stretegy.AllocationContext;
import com.assement.finance.stretegy.AllocationStrategyFactory;
import com.assement.finance.stretegy.RepaymentAllocationStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepaymentServiceImpl implements RepaymentService {

    private final LoanRepository loanRepository;
    private final EmiScheduleRepository emiScheduleRepository;
    private final LoanChargeRepository loanChargeRepository;
    private final RepaymentRepository repaymentRepository;
    private final RepaymentAllocationRepository allocationRepository;
    private final AllocationStrategyFactory strategyFactory;

    /**
     * Process a repayment with idempotency guarantee.
     *
     * <p>Uses a pessimistic write lock on the Loan row to prevent
     * concurrent repayments from producing inconsistent allocation.
     */
    @Override
    @Transactional
    public RepaymentResponse processRepayment(ProcessRepaymentRequest request) {
        // --- Idempotency check ---
        if (repaymentRepository.existsByReferenceId(request.getReferenceId())) {
            log.info("Duplicate repayment detected for referenceId={}, returning existing record", request.getReferenceId());
            Repayment existing = repaymentRepository.findByReferenceId(request.getReferenceId()).get();
            return toRepaymentResponse(existing);
        }

        // --- Acquire pessimistic lock on loan to prevent concurrent repayments ---
        Loan loan = loanRepository.findByLoanIdWithLock(request.getLoanId())
                .orElseThrow(() -> new LoanNotFoundException(request.getLoanId()));

        if (LoanStatus.CLOSED.equals(loan.getStatus())) {
            throw new BusinessException("Loan is already closed: " + request.getLoanId());
        }

        log.info("Processing repayment {} of {} for loan {}", request.getReferenceId(), request.getAmount(), request.getLoanId());

        // --- Load unpaid EMIs & outstanding charges ---
        List<EmiSchedule> unpaidEmis = emiScheduleRepository.findUnpaidByLoanIdOrderByDueDate(loan.getLoanId());
        List<LoanCharge> outstandingCharges = loanChargeRepository.findOutstandingByLoanIdOrderByCreatedAt(loan.getLoanId());

        // --- Build allocation context ---
        AllocationContext context = AllocationContext.builder()
                .loanId(loan.getLoanId())
                .repaymentReferenceId(request.getReferenceId())
                .remaining(request.getAmount())
                .unpaidEmis(unpaidEmis)
                .outstandingCharges(outstandingCharges)
                .build();

        // --- Execute strategy ---
        RepaymentAllocationStrategy strategy = strategyFactory.resolve(loan.getAllocationStrategy());
        strategy.allocate(context);

        BigDecimal unallocated = context.getRemaining(); // Overpayment amount
        if (unallocated.compareTo(BigDecimal.ZERO) > 0) {
            log.info("Overpayment of {} for loan {} — will remain as credit", unallocated, loan.getLoanId());
        }

        // --- Persist repayment record ---
        Repayment repayment = Repayment.builder()
                .referenceId(request.getReferenceId())
                .loan(loan)
                .amount(request.getAmount())
                .paymentDate(request.getPaymentDate())
                .status(RepaymentStatus.SUCCESS)
                .build();

        // --- Attach ledger entries to the repayment ---
        List<RepaymentAllocation> entries = context.getLedgerEntries().stream()
                .map(e -> RepaymentAllocation.builder()
                        .repayment(repayment)
                        .loanId(e.getLoanId())
                        .entityType(e.getEntityType())
                        .entityId(e.getEntityId())
                        .component(e.getComponent())
                        .amount(e.getAmount())
                        .build())
                .collect(Collectors.toList());

        repayment.getAllocations().addAll(entries);
        repaymentRepository.save(repayment);

        // --- Persist updated EMI and charge state ---
        emiScheduleRepository.saveAll(unpaidEmis);
        loanChargeRepository.saveAll(outstandingCharges);

        // --- Update loan status if fully repaid ---
        updateLoanStatus(loan, unpaidEmis, outstandingCharges);
        loanRepository.save(loan);

        log.info("Repayment {} processed. {} allocations created. Unallocated: {}", request.getReferenceId(), entries.size(), unallocated);

        Repayment saved = repaymentRepository.findByReferenceId(request.getReferenceId()).orElse(repayment);
        return toRepaymentResponse(saved, unallocated, entries);
    }

    /**
     * Reverse a repayment by creating compensating negative ledger entries
     * and rolling back EMI / charge paid amounts.
     */
    @Override
    @Transactional
    public RepaymentResponse reverseRepayment(String referenceId, ReversalRequest request) {
        Repayment original = repaymentRepository.findByReferenceId(referenceId)
                .orElseThrow(() -> new BusinessException("Repayment not found: " + referenceId));

        if (original.isReversed()) {
            throw new BusinessException("Repayment already reversed: " + referenceId);
        }

        // --- Acquire lock on loan ---
        Loan loan = loanRepository.findByLoanIdWithLock(original.getLoan().getLoanId())
                .orElseThrow(() -> new LoanNotFoundException(original.getLoan().getLoanId()));

        log.info("Reversing repayment {} for loan {}", referenceId, loan.getLoanId());

        // --- Undo all allocations ---
        List<RepaymentAllocation> originalAllocations = allocationRepository.findByRepaymentReferenceId(referenceId);
        undoAllocations(originalAllocations, loan.getLoanId());

        // --- Mark original repayment as REVERSED ---
        original.setStatus(RepaymentStatus.REVERSED);
        original.setReversalReason(request.getReason());
        repaymentRepository.save(original);

        // --- Recalculate loan status ---
        List<EmiSchedule> allEmis = emiScheduleRepository.findByLoanLoanIdOrderByDueDateAsc(loan.getLoanId());
        List<LoanCharge> allCharges = loanChargeRepository.findByLoanLoanId(loan.getLoanId());
        loan.setStatus(LoanStatus.ACTIVE);
        loanRepository.save(loan);

        log.info("Reversal complete for repayment {}", referenceId);
        return toRepaymentResponse(original);
    }

    @Override
    @Transactional(readOnly = true)
    public RepaymentResponse getRepayment(String referenceId) {
        Repayment repayment = repaymentRepository.findByReferenceId(referenceId)
                .orElseThrow(() -> new BusinessException("Repayment not found: " + referenceId));
        return toRepaymentResponse(repayment);
    }

    // ---- Private Helpers ----

    private void undoAllocations(List<RepaymentAllocation> allocations, String loanId) {
        for (RepaymentAllocation alloc : allocations) {
            switch (alloc.getEntityType()) {
                case EMI -> {
                    EmiSchedule emi = emiScheduleRepository.findByEmiId(alloc.getEntityId())
                            .orElseThrow(() -> new BusinessException("EMI not found during reversal: " + alloc.getEntityId()));
                    switch (alloc.getComponent()) {
                        case INTEREST -> emi.setInterestPaid(emi.getInterestPaid().subtract(alloc.getAmount()));
                        case PRINCIPAL -> emi.setPrincipalPaid(emi.getPrincipalPaid().subtract(alloc.getAmount()));
                        default -> throw new BusinessException("Unexpected component for EMI: " + alloc.getComponent());
                    }
                    recalculateEmiStatus(emi);
                    emiScheduleRepository.save(emi);
                }
                case LOAN_CHARGE -> {
                    LoanCharge charge = loanChargeRepository.findByChargeId(alloc.getEntityId())
                            .orElseThrow(() -> new BusinessException("Charge not found during reversal: " + alloc.getEntityId()));
                    charge.setAmountPaid(charge.getAmountPaid().subtract(alloc.getAmount()));
                    recalculateChargeStatus(charge);
                    loanChargeRepository.save(charge);
                }
            }
        }
    }

    private void recalculateEmiStatus(EmiSchedule emi) {
        if (emi.isFullyPaid()) {
            emi.setStatus(EmiStatus.PAID);
        } else if (emi.getPrincipalPaid().compareTo(BigDecimal.ZERO) > 0
                || emi.getInterestPaid().compareTo(BigDecimal.ZERO) > 0) {
            emi.setStatus(EmiStatus.PAID);
        } else {
            emi.setStatus(emi.isOverdue()
                    ? EmiStatus.OVERDUE
                    : EmiStatus.PENDING);
        }
    }

    private void recalculateChargeStatus(LoanCharge charge) {
        if (charge.getAmountPaid().compareTo(BigDecimal.ZERO) == 0) {
            charge.setStatus(ChargeStatus.OUTSTANDING);
        } else if (charge.isFullyPaid()) {
            charge.setStatus(ChargeStatus.PAID);
        } else {
            charge.setStatus(ChargeStatus.PARTIAL);
        }
    }

    private void updateLoanStatus(Loan loan, List<EmiSchedule> emis, List<LoanCharge> charges) {
        boolean allEmisPaid = emis.stream().allMatch(EmiSchedule::isFullyPaid);
        boolean allChargesPaid = charges.stream().allMatch(LoanCharge::isFullyPaid);

        if (allEmisPaid && allChargesPaid) {
            loan.setStatus(LoanStatus.CLOSED);
            log.info("Loan {} is now CLOSED", loan.getLoanId());
        } else {
            boolean hasOverdue = emis.stream().anyMatch(EmiSchedule::isOverdue);
            loan.setStatus(hasOverdue ? LoanStatus.OVERDUE : LoanStatus.ACTIVE);
        }
    }

    private RepaymentResponse toRepaymentResponse(Repayment repayment) {
        List<RepaymentAllocation> allocations = allocationRepository.findByRepaymentReferenceId(repayment.getReferenceId());
        BigDecimal allocated = allocations.stream()
                .map(RepaymentAllocation::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal unallocated = repayment.getAmount().subtract(allocated);
        return toRepaymentResponse(repayment, unallocated.max(BigDecimal.ZERO), allocations);
    }

    private RepaymentResponse toRepaymentResponse(Repayment repayment, BigDecimal unallocated, List<RepaymentAllocation> allocations) {
        List<RepaymentResponse.AllocationEntry> entries = allocations.stream()
                .map(a -> RepaymentResponse.AllocationEntry.builder()
                        .entityType(a.getEntityType())
                        .entityId(a.getEntityId())
                        .component(a.getComponent())
                        .amount(a.getAmount())
                        .build())
                .collect(Collectors.toList());

        return RepaymentResponse.builder()
                .referenceId(repayment.getReferenceId())
                .loanId(repayment.getLoan().getLoanId())
                .amount(repayment.getAmount())
                .paymentDate(repayment.getPaymentDate())
                .status(repayment.getStatus())
                .unallocatedAmount(unallocated)
                .allocations(entries)
                .build();
    }
}