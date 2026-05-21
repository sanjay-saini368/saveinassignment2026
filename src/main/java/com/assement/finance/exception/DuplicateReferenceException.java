package com.assement.finance.exception;

public class DuplicateReferenceException extends RuntimeException {
    public DuplicateReferenceException(String referenceId) {
        super("Repayment with referenceId already processed: " + referenceId);
    }
}