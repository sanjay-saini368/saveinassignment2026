package com.assement.finance.exception;

public class LoanNotFoundException extends RuntimeException {
    public LoanNotFoundException(String loanId) {
        super("Loan not found: " + loanId);
    }
}