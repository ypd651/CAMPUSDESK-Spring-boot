package com.technova.campusdesk.controller;

import com.technova.campusdesk.dto.SummaryResponse;
import com.technova.campusdesk.security.UserPrincipal;
import com.technova.campusdesk.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports", description = "Dashboard indicators")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Ticket counters; global for administrators, scoped for everyone else")
    public SummaryResponse summary(@AuthenticationPrincipal UserPrincipal actor) {
        return reportService.summary(actor);
    }
}
