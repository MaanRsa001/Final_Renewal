package com.insurance.renewal.controller;

import com.insurance.renewal.dto.HomePositionMasterDTO;
import com.insurance.renewal.dto.HomePositionSyncResultDTO;
import com.insurance.renewal.service.HomePositionSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/home-position-master")
@RequiredArgsConstructor
public class HomePositionMasterController {

    private final HomePositionSyncService homePositionSyncService;

    @GetMapping("/upcoming-expiry")
    public List<HomePositionMasterDTO> upcomingExpiry() {
        return homePositionSyncService.fetchUpcomingExpiry();
    }

    @PostMapping("/sync-to-policy-master")
    public HomePositionSyncResultDTO syncToPolicyMaster() {
        return homePositionSyncService.syncToPolicyMaster();
    }
}
