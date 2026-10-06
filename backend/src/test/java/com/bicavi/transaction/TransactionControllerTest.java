package com.bicavi.transaction;

import com.bicavi.common.BusinessRuleException;
import com.bicavi.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@Import(SecurityConfig.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService service;

    @Test
    void listParsesMonthAndReturnsJson() throws Exception {
        when(service.list(YearMonth.of(2026, 10), 3L)).thenReturn(List.of(new TransactionResponse(
                1L, new BigDecimal("35.90"), TransactionType.EXPENSE, 3L, "Mercado", "Feira", LocalDate.of(2026, 10, 6))));

        mockMvc.perform(get("/api/transactions").param("month", "2026-10").param("categoryId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(35.90))
                .andExpect(jsonPath("$[0].categoryName").value("Mercado"))
                .andExpect(jsonPath("$[0].occurredOn").value("2026-10-06"));

        verify(service).list(YearMonth.of(2026, 10), 3L);
    }

    @Test
    void listWithoutMonthReturns400() throws Exception {
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Parâmetro obrigatório ausente: month"));
        verifyNoInteractions(service);
    }

    @Test
    void listWithInvalidMonthReturns400() throws Exception {
        mockMvc.perform(get("/api/transactions").param("month", "2026-13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Valor inválido para o parâmetro: month"));
        verifyNoInteractions(service);
    }

    @Test
    void createReturns201() throws Exception {
        when(service.create(any())).thenReturn(new TransactionResponse(
                1L, new BigDecimal("35.90"), TransactionType.EXPENSE, null, null, null, LocalDate.of(2026, 10, 6)));

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 35.90, "type": "EXPENSE", "occurredOn": "2026-10-06"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void createWithThreeDecimalsReturns400() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10.555, "type": "EXPENSE", "occurredOn": "2026-10-06"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
        verifyNoInteractions(service);
    }

    @Test
    void businessRuleViolationReturns400WithMessage() throws Exception {
        when(service.create(any())).thenThrow(new BusinessRuleException("Categoria 99 não existe"));

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10, "type": "EXPENSE", "categoryId": 99, "occurredOn": "2026-10-06"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Categoria 99 não existe"));
    }
}
