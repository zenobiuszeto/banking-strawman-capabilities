package com.banking.platform.account.controller;

import com.banking.platform.account.model.dto.DashboardResponse;
import com.banking.platform.account.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<DashboardResponse> getDashboard(@PathVariable UUID customerId) {
        log.info("Received request to get dashboard for customer: {}", customerId);

        DashboardResponse response = dashboardService.getDashboard(customerId);

        return ResponseEntity.ok(response);
    }
}
