package com.insurance.renewal.dto;

import lombok.*;

import java.util.List;

/** Summary returned after fetching / syncing home_position_master rows into policy_master. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HomePositionSyncResultDTO {

    private int totalFetched;
    private int inserted;
    private int alreadyExists;
    private int failed;

    @Builder.Default
    private List<String> errors = List.of();
}
