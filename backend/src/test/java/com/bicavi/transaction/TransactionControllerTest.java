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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@Import(SecurityConfig.class)
class TransactionControllerTest {

    private static final Long USER = 7L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService service;

    private static RequestPostProcessor loggedUser() {
        return jwt().jwt(token -> token.subject(String.valueOf(USER)));
    }

    @Test
    void withoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/transactions").param("month", "2026-10"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void listParsesMonthAndPassesLoggedUser() throws Exception {
        when(service.list(USER, YearMonth.of(2026, 10), 3L)).thenReturn(List.of(new TransactionResponse(
                1L, new BigDecimal("35.90"), TransactionType.EXPENSE, PaymentMethod.PIX, 3L, "Mercado", "Feira", LocalDate.of(2026, 10, 6))));

        mockMvc.perform(get("/api/transactions").with(loggedUser()).param("month", "2026-10").param("categoryId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(35.90))
                .andExpect(jsonPath("$[0].categoryName").value("Mercado"))
                .andExpect(jsonPath("$[0].paymentMethod").value("PIX"))
                .andExpect(jsonPath("$[0].occurredOn").value("2026-10-06"));

        verify(service).list(USER, YearMonth.of(2026, 10), 3L);
    }

    @Test
    void listWithoutMonthReturns400() throws Exception {
        mockMvc.perform(get("/api/transactions").with(loggedUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Parâmetro obrigatório ausente: month"));
        verifyNoInteractions(service);
    }

    @Test
    void listWithInvalidMonthReturns400() throws Exception {
        mockMvc.perform(get("/api/transactions").with(loggedUser()).param("month", "2026-13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Valor inválido para o parâmetro: month"));
        verifyNoInteractions(service);
    }

    @Test
    void createReturns201() throws Exception {
        when(service.create(eq(USER), any())).thenReturn(new TransactionResponse(
                1L, new BigDecimal("35.90"), TransactionType.EXPENSE, PaymentMethod.PIX, null, null, null, LocalDate.of(2026, 10, 6)));

        mockMvc.perform(post("/api/transactions").with(loggedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 35.90, "type": "EXPENSE", "paymentMethod": "PIX", "occurredOn": "2026-10-06"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));

        verify(service).create(USER, new TransactionRequest(
                new BigDecimal("35.90"), TransactionType.EXPENSE, PaymentMethod.PIX, null, null, LocalDate.of(2026, 10, 6)));
    }

    @Test
    void createInstallmentsReturns201WithAllParts() throws Exception {
        when(service.createInstallments(eq(USER), any())).thenReturn(List.of(
                new TransactionResponse(1L, new BigDecimal("50.00"), TransactionType.EXPENSE, PaymentMethod.CREDITO,
                        null, null, "TV (1/2)", LocalDate.of(2026, 11, 10)),
                new TransactionResponse(2L, new BigDecimal("50.00"), TransactionType.EXPENSE, PaymentMethod.CREDITO,
                        null, null, "TV (2/2)", LocalDate.of(2026, 12, 10))));

        mockMvc.perform(post("/api/transactions/installments").with(loggedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"totalAmount": 100, "installments": 2, "paymentMethod": "CREDITO",
                                 "description": "TV", "firstDate": "2026-11-10"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].description").value("TV (2/2)"));

        verify(service).createInstallments(USER, new InstallmentRequest(
                new BigDecimal("100"), 2, PaymentMethod.CREDITO, null, "TV", LocalDate.of(2026, 11, 10)));
    }

    @Test
    void createInstallmentsOutsideTwoToTwentyFourReturns400() throws Exception {
        for (int installments : new int[] {1, 25}) {
            mockMvc.perform(post("/api/transactions/installments").with(loggedUser())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"totalAmount": 100, "installments": %d, "paymentMethod": "CREDITO",
                                     "firstDate": "2026-11-10"}
                                    """.formatted(installments)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.installments").exists());
        }
        verifyNoInteractions(service);
    }

    @Test
    void createWithUnknownPaymentMethodReturns400() throws Exception {
        mockMvc.perform(post("/api/transactions").with(loggedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10, "type": "EXPENSE", "paymentMethod": "CHEQUE", "occurredOn": "2026-10-06"}
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void createWithThreeDecimalsReturns400() throws Exception {
        mockMvc.perform(post("/api/transactions").with(loggedUser())
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
        when(service.create(eq(USER), any())).thenThrow(new BusinessRuleException("Categoria 99 não existe"));

        mockMvc.perform(post("/api/transactions").with(loggedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10, "type": "EXPENSE", "categoryId": 99, "occurredOn": "2026-10-06"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Categoria 99 não existe"));
    }
}
