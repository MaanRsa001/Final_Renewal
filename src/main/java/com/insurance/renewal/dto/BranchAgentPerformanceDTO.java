package com.insurance.renewal.dto;

import lombok.*;

/** Agent-wise renewal performance rollup for the Branch Manager dashboard. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BranchAgentPerformanceDTO {
    private Integer agentId;
    private String agentName;
    private Long pendingCount;
    private Long completedCount;
    private Long businessLostCount;
}
