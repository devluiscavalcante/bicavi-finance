package com.bicavi.report;

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
    public MonthlySummaryResponse monthlySummary(@RequestParam YearMonth month) {
        return service.monthlySummary(month);
    }
}
