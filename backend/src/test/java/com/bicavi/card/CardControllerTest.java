package com.bicavi.card;

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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CardController.class)
@Import(SecurityConfig.class)
class CardControllerTest {

    private static final Long USER = 7L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CardService service;

    private static RequestPostProcessor loggedUser() {
        return jwt().jwt(token -> token.subject(String.valueOf(USER)));
    }

    @Test
    void withoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/cards"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void listPassesLoggedUserIdFromTokenToService() throws Exception {
        when(service.list(USER)).thenReturn(List.of(new CardResponse(1L, "Nubank")));

        mockMvc.perform(get("/api/cards").with(loggedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Nubank"));
    }

    @Test
    void createReturns201() throws Exception {
        when(service.create(eq(USER), any())).thenReturn(new CardResponse(1L, "Nubank"));

        mockMvc.perform(post("/api/cards").with(loggedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Nubank"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Nubank"));
    }

    @Test
    void createWithBlankOrTooLongNameReturns400() throws Exception {
        mockMvc.perform(post("/api/cards").with(loggedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
        mockMvc.perform(post("/api/cards").with(loggedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + "x".repeat(51) + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void getUnknownIdReturns404() throws Exception {
        when(service.get(USER, 99L)).thenThrow(new NotFoundException("Cartão 99 não encontrado"));

        mockMvc.perform(get("/api/cards/99").with(loggedUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Cartão 99 não encontrado"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/cards/3").with(loggedUser()))
                .andExpect(status().isNoContent());
        verify(service).delete(USER, 3L);
    }
}
