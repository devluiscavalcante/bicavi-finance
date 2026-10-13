package com.bicavi.transaction;

import com.bicavi.common.NotFoundException;
import com.bicavi.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.YearMonth;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvoiceController.class)
@Import(SecurityConfig.class)
class InvoiceControllerTest {

    private static final Long USER = 7L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService service;

    private static RequestPostProcessor loggedUser() {
        return jwt().jwt(token -> token.subject(String.valueOf(USER)));
    }

    @Test
    void parsesCardAndMonthFromThePath() throws Exception {
        when(service.setInvoicePaid(USER, 4L, YearMonth.of(2026, 11), true)).thenReturn(List.of());

        mockMvc.perform(patch("/api/cards/4/invoices/2026-11/payment").with(loggedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paid": true}
                                """))
                .andExpect(status().isOk());

        verify(service).setInvoicePaid(USER, 4L, YearMonth.of(2026, 11), true);
    }

    @Test
    void invalidMonthReturns400() throws Exception {
        mockMvc.perform(patch("/api/cards/4/invoices/2026-13/payment").with(loggedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paid": true}
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void unknownCardReturns404() throws Exception {
        when(service.setInvoicePaid(USER, 99L, YearMonth.of(2026, 11), true))
                .thenThrow(new NotFoundException("Cartão 99 não encontrado"));

        mockMvc.perform(patch("/api/cards/99/invoices/2026-11/payment").with(loggedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paid": true}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void withoutTokenReturns401() throws Exception {
        mockMvc.perform(patch("/api/cards/4/invoices/2026-11/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paid": true}
                                """))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
