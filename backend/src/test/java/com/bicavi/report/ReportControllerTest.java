package com.bicavi.report;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService service;

    @Test
    void returnsMonthlySummaryAsJson() throws Exception {
        when(service.monthlySummary(YearMonth.of(2026, 10))).thenReturn(new MonthlySummaryResponse(
                YearMonth.of(2026, 10),
                new BigDecimal("5000.00"),
                new BigDecimal("470.50"),
                new BigDecimal("4529.50"),
                List.of(new CategoryTotal(1L, "Mercado", new BigDecimal("350.50")))));

        mockMvc.perform(get("/api/reports/monthly-summary").param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.balance").value(4529.50))
                .andExpect(jsonPath("$.expensesByCategory[0].categoryName").value("Mercado"));
    }

    @Test
    void missingMonthReturns400() throws Exception {
        mockMvc.perform(get("/api/reports/monthly-summary"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
