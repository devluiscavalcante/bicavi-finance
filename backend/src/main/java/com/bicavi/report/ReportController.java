package com.bicavi.report;

import com.bicavi.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    // GET /api/reports/monthly-summary?month=2026-10
    @GetMapping("/monthly-summary")
    public MonthlySummaryResponse monthlySummary(@AuthenticationPrincipal Jwt jwt,
                                                 @RequestParam YearMonth month) {
        return service.monthlySummary(CurrentUser.id(jwt), month);
    }
}
