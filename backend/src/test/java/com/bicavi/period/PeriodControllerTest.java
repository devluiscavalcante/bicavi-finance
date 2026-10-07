package com.bicavi.period;

import com.bicavi.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.YearMonth;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PeriodController.class)
@Import(SecurityConfig.class)
class PeriodControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PeriodPolicy policy;

    @Test
    void returnsLimitsAsYearMonthStrings() throws Exception {
        when(policy.firstEditableMonth()).thenReturn(YearMonth.of(2026, 10));
        when(policy.oldestVisibleMonth()).thenReturn(YearMonth.of(2026, 4));

        mockMvc.perform(get("/api/period").with(jwt().jwt(token -> token.subject("7"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstEditableMonth").value("2026-10"))
                .andExpect(jsonPath("$.oldestVisibleMonth").value("2026-04"));
    }

    @Test
    void requiresToken() throws Exception {
        mockMvc.perform(get("/api/period")).andExpect(status().isUnauthorized());
    }
}
