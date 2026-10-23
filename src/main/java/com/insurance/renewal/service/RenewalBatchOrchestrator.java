package com.insurance.renewal.service;

import com.insurance.renewal.dto.HomePositionSyncResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Full renewal run (screen button and the 06:00 schedule):
 * 1. pull the policies expiring within 30 days from home_position_master, calculate the premium through
 *    EwayCommonApi /common/premium-calc and insert the new ones into policy_master  (HomePositionSyncService);
 * 2. run the renewal batch on policy_master and ask EwayCommonApi /post/notification/pushnotification
 *    to send the renewal mail for every processed policy  (RenewalBatchService).
 * Not @Transactional on purpose: step 1 commits row by row, so one bad policy cannot roll back the batch.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RenewalBatchOrchestrator {

    private final HomePositionSyncService homePositionSyncService;
    private final RenewalBatchService batchService;

    /** false = skip step 1 and run the batch on the rows already in policy_master. */
    @Value("${renewal.batch.pull-before-run:true}")
    private boolean pullBeforeRun;

    @Scheduled(cron = "0 0 6 * * *") // every day at 06:00
    public void runDailyBatch() {
        log.info("Renewal batch starting for {}", LocalDate.now());
        run(LocalDate.now());
    }

    public Map<String, Object> run(LocalDate runDate) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runDate", runDate.toString());

        if (pullBeforeRun) {
            try {
                HomePositionSyncResultDTO sync = homePositionSyncService.syncToPolicyMaster();
                out.put("pulledFromHomePosition", sync.getTotalFetched());
                out.put("insertedIntoPolicyMaster", sync.getInserted());
                out.put("alreadyInPolicyMaster", sync.getAlreadyExists());
                out.put("pullErrors", sync.getErrors());
            } catch (Exception ex) {
                log.error("Pull from home_position_master failed - running the batch on existing policy_master rows", ex);
                out.put("pullError", ex.getMessage());
            }
        }

        RenewalBatchService.BatchResult result = batchService.runBatch(runDate);
        out.put("policiesProcessed", result.processed());
        out.put("policiesSkipped", result.skipped());
        out.put("totalCount", result.total());
        return out;
    }
}
