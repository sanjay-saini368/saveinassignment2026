package com.assement.finance.controller;

import com.assement.finance.DTO.*;
import com.assement.finance.service.LoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/loans")
@RequiredArgsConstructor
@Tag(name = "Loans", description = "Loan creation and summary APIs")
public class LoanController {

    private final LoanService loanService;

    @PostMapping
    @Operation(summary = "Create a new loan with EMI schedule")
    public ResponseEntity<LoanSummaryResponse> createLoan(@Valid @RequestBody CreateLoanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(loanService.createLoan(request));
    }

    @GetMapping("/{loanId}")
    @Operation(summary = "Get loan summary including outstanding amounts")
    public ResponseEntity<LoanSummaryResponse> getLoanSummary(@PathVariable String loanId) {
        return ResponseEntity.ok(loanService.getLoanSummary(loanId));
    }

    @GetMapping("/{loanId}/schedules")
    @Operation(summary = "Get EMI schedules for a loan")
    public ResponseEntity<List<EmiSummaryResponse>> getEmiSchedules(@PathVariable String loanId) {
        return ResponseEntity.ok(loanService.getEmiSchedules(loanId));
    }

    @PostMapping("/{loanId}/charges")
    @Operation(summary = "Add a loan-level charge")
    public ResponseEntity<ChargeResponse> addCharge(
            @PathVariable String loanId,
            @Valid @RequestBody AddChargeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(loanService.addCharge(loanId, request));
    }

    @GetMapping("/{loanId}/charges")
    @Operation(summary = "Get all charges for a loan")
    public ResponseEntity<List<ChargeResponse>> getCharges(@PathVariable String loanId) {
        return ResponseEntity.ok(loanService.getCharges(loanId));
    }
}