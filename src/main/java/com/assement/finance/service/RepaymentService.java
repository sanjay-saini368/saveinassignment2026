package com.assement.finance.service;


import com.assement.finance.DTO.ProcessRepaymentRequest;
import com.assement.finance.DTO.RepaymentResponse;
import com.assement.finance.DTO.ReversalRequest;

public interface RepaymentService {

    RepaymentResponse processRepayment(ProcessRepaymentRequest request);

    RepaymentResponse reverseRepayment(String referenceId, ReversalRequest request);

    RepaymentResponse getRepayment(String referenceId);
}