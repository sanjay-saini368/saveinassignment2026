package com.assement.finance.DTO;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReversalRequest {

    @Size(max = 500)
    private String reason;
}