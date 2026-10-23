package com.insurance.renewal.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UnderwriterActionRequestDTO {
    @NotNull
    private Integer userId;
    @NotNull
    private String actionTaken; // APPROVED, REJECTED, MODIFIED
    private BigDecimal revisedLoadingDiscountPct; // only if MODIFIED
    private String remarks;
}
