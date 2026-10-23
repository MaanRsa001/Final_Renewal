package com.insurance.renewal.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;

/** Used by Branch Manager to mark a policy renewed on branch-collected payment. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ManualRenewalRequestDTO {
    @NotNull
    private BigDecimal amountPaid;
    @NotNull
    private Integer collectedByUserId;
}
