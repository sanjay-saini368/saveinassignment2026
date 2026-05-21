package com.assement.finance.service;



import com.assement.finance.DTO.*;

import java.util.List;

public interface LoanService {

    LoanSummaryResponse createLoan(CreateLoanRequest request);

    LoanSummaryResponse getLoanSummary(String loanId);

    List<EmiSummaryResponse> getEmiSchedules(String loanId);

    ChargeResponse addCharge(String loanId, AddChargeRequest request);

    List<ChargeResponse> getCharges(String loanId);
}