package com.assement.finance.service;

import com.assement.finance.DTO.*;
import com.assement.finance.Enums.ChargeStatus;
import com.assement.finance.Enums.EmiStatus;
import com.assement.finance.Enums.LoanStatus;
import com.assement.finance.domain.EmiSchedule;
import com.assement.finance.domain.Loan;
import com.assement.finance.domain.LoanCharge;
import com.assement.finance.exception.BusinessException;
import com.assement.finance.exception.LoanNotFoundException;
import com.assement.finance.repository.EmiScheduleRepository;
import com.assement.finance.repository.LoanChargeRepository;
import com.assement.finance.repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanServiceImpl implements LoanService {

    private final LoanRepository loanRepository;
    private final EmiScheduleRepository emiScheduleRepository;
    private final LoanChargeRepository loanChargeRepository;

    @Override
    @Transactional
    public LoanSummaryResponse createLoan(CreateLoanRequest request) {
        if (loanRepository.existsByLoanId(request.getLoanId())) {
            throw new BusinessException("Loan with ID already exists: " + request.getLoanId());
        }

        Loan loan = Loan.builder()
                .loanId(request.getLoanId())
                .borrowerName(request.getBorrowerName())
                .principalAmount(request.getPrincipalAmount())
                .interestAmount(request.getInterestAmount())
                .tenureMonths(request.getTenureMonths())
                .status(LoanStatus.ACTIVE)
                .allocationStrategy(request.getAllocationStrategy())
                .build();

        AtomicInteger counter = new AtomicInteger(1);
        for (EmiScheduleRequest emiReq : request.getEmiSchedules()) {
            int emiNo = counter.getAndIncrement();
            EmiSchedule emi = EmiSchedule.builder()
                    .emiId(request.getLoanId() + "-EMI-" + emiNo)
                    .emiNumber(emiNo)
                    .principalDue(emiReq.getPrincipalDue())
                    .interestDue(emiReq.getInterestDue())
                    .dueDate(emiReq.getDueDate())
                    .status(EmiStatus.PENDING)
                    .build();
            loan.addEmiSchedule(emi);
        }

        Loan saved = loanRepository.save(loan);
        log.info("Created loan {} with {} EMIs", saved.getLoanId(), saved.getEmiSchedules().size());
        return buildLoanSummary(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public LoanSummaryResponse getLoanSummary(String loanId) {
        Loan loan = loanRepository.findByLoanId(loanId)
                .orElseThrow(() -> new LoanNotFoundException(loanId));
        return buildLoanSummary(loan);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmiSummaryResponse> getEmiSchedules(String loanId) {
        if (!loanRepository.existsByLoanId(loanId)) {
            throw new LoanNotFoundException(loanId);
        }
        return emiScheduleRepository.findByLoanLoanIdOrderByDueDateAsc(loanId)
                .stream()
                .map(this::toEmiSummary)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ChargeResponse addCharge(String loanId, AddChargeRequest request) {
        Loan loan = loanRepository.findByLoanId(loanId)
                .orElseThrow(() -> new LoanNotFoundException(loanId));

        if (LoanStatus.CLOSED.equals(loan.getStatus())) {
            throw new BusinessException("Cannot add charges to a closed loan: " + loanId);
        }

        String chargeId = "CHG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LoanCharge charge = LoanCharge.builder()
                .chargeId(chargeId)
                .chargeType(request.getChargeType())
                .amount(request.getAmount())
                .status(ChargeStatus.OUTSTANDING)
                .remarks(request.getRemarks())
                .build();
        loan.addCharge(charge);

        loanRepository.save(loan);
        log.info("Added charge {} ({}) of {} to loan {}", chargeId, request.getChargeType(), request.getAmount(), loanId);
        return toChargeResponse(charge);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChargeResponse> getCharges(String loanId) {
        if (!loanRepository.existsByLoanId(loanId)) {
            throw new LoanNotFoundException(loanId);
        }
        return loanChargeRepository.findByLoanLoanId(loanId)
                .stream()
                .map(this::toChargeResponse)
                .collect(Collectors.toList());
    }

    // ---- Mapping Helpers ----

    private LoanSummaryResponse buildLoanSummary(Loan loan) {
        List<EmiSchedule> emis = emiScheduleRepository.findByLoanLoanIdOrderByDueDateAsc(loan.getLoanId());
        List<LoanCharge> charges = loanChargeRepository.findByLoanLoanId(loan.getLoanId());

        BigDecimal principalOutstanding = emis.stream()
                .map(EmiSchedule::getPrincipalOutstanding)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal interestOutstanding = emis.stream()
                .map(EmiSchedule::getInterestOutstanding)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCharges = charges.stream()
                .map(LoanCharge::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal chargesOutstanding = charges.stream()
                .map(LoanCharge::getOutstandingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal overdueAmount = emis.stream()
                .filter(EmiSchedule::isOverdue)
                .map(EmiSchedule::getTotalOutstanding)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        EmiSchedule nextEmi = emis.stream()
                .filter(e -> !EmiStatus.PAID.equals(e.getStatus()))
                .findFirst()
                .orElse(null);

        return LoanSummaryResponse.builder()
                .loanId(loan.getLoanId())
                .borrowerName(loan.getBorrowerName())
                .status(loan.getStatus())
                .allocationStrategy(loan.getAllocationStrategy())
                .totalPrincipal(loan.getPrincipalAmount())
                .totalInterest(loan.getInterestAmount())
                .totalCharges(totalCharges)
                .principalOutstanding(principalOutstanding)
                .interestOutstanding(interestOutstanding)
                .chargesOutstanding(chargesOutstanding)
                .totalOutstanding(principalOutstanding.add(interestOutstanding).add(chargesOutstanding))
                .totalOverdueAmount(overdueAmount)
                .nextEmiId(nextEmi != null ? nextEmi.getEmiId() : null)
                .nextEmiDueDate(nextEmi != null ? nextEmi.getDueDate() : null)
                .nextEmiAmount(nextEmi != null ? nextEmi.getTotalOutstanding() : null)
                .build();
    }

    private EmiSummaryResponse toEmiSummary(EmiSchedule emi) {
        return EmiSummaryResponse.builder()
                .emiId(emi.getEmiId())
                .emiNumber(emi.getEmiNumber())
                .dueDate(emi.getDueDate())
                .status(emi.getStatus())
                .principalDue(emi.getPrincipalDue())
                .principalPaid(emi.getPrincipalPaid())
                .principalOutstanding(emi.getPrincipalOutstanding())
                .interestDue(emi.getInterestDue())
                .interestPaid(emi.getInterestPaid())
                .interestOutstanding(emi.getInterestOutstanding())
                .totalDue(emi.getTotalDue())
                .totalOutstanding(emi.getTotalOutstanding())
                .build();
    }

    private ChargeResponse toChargeResponse(LoanCharge charge) {
        return ChargeResponse.builder()
                .chargeId(charge.getChargeId())
                .chargeType(charge.getChargeType())
                .amount(charge.getAmount())
                .amountPaid(charge.getAmountPaid())
                .outstandingAmount(charge.getOutstandingAmount())
                .status(charge.getStatus())
                .remarks(charge.getRemarks())
                .createdAt(charge.getCreatedAt())
                .build();
    }
}