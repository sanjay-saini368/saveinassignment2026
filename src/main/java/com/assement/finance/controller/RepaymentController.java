package com.assement.finance.controller;


import com.assement.finance.DTO.ProcessRepaymentRequest;
import com.assement.finance.DTO.RepaymentResponse;
import com.assement.finance.DTO.ReversalRequest;
import com.assement.finance.service.RepaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/repayments")
@RequiredArgsConstructor
@Tag(name = "Repayments", description = "Repayment processing and reversal APIs")
public class RepaymentController {

    private final RepaymentService repaymentService;

    @PostMapping
    @Operation(summary = "Process a repayment against a loan (idempotent)")
    public ResponseEntity<RepaymentResponse> processRepayment(@Valid @RequestBody ProcessRepaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(repaymentService.processRepayment(request));
    }

    @PostMapping("/{referenceId}/reverse")
    @Operation(summary = "Reverse a processed repayment")
    public ResponseEntity<RepaymentResponse> reverseRepayment(
            @PathVariable String referenceId,
            @RequestBody(required = false) ReversalRequest request) {
        return ResponseEntity.ok(repaymentService.reverseRepayment(referenceId, request != null ? request : new ReversalRequest()));
    }

    @GetMapping("/{referenceId}")
    @Operation(summary = "Get repayment details and allocation ledger")
    public ResponseEntity<RepaymentResponse> getRepayment(@PathVariable String referenceId) {
        return ResponseEntity.ok(repaymentService.getRepayment(referenceId));
    }
}