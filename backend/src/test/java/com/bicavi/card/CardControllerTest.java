package com.bicavi.card;

import com.bicavi.common.NotFoundException;
import com.bicavi.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CardController.class)
@Import(SecurityConfig.class)
class CardControllerTest {

    private static final Long USER = 7L;

    private static final CardResponse NUBANK = new CardResponse(1L, "Nubank", CardBrand.MASTERCARD,
            "Maria Silva", "3210", 11, 2029, CardColor.ROXO);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CardService service;

    private static RequestPostProcessor loggedUser() {
        return jwt().jwt(token -> token.subject(String.valueOf(USER)));
    }

    // Corpo válido de um cartão.
    private static String validBody() {
        return bodyWith(Map.of());
    }

    // O corpo válido com UM campo trocado: o teste mostra só o que está errado.
    // O valor vem já em JSON: "\"VISA\"" (texto, com aspas), "13" (número), "null".
    private static String bodyWith(String field, String jsonValue) {
        return bodyWith(Map.of(field, jsonValue));
    }

    private static String bodyWith(Map<String, String> changes) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("name", "\"Nubank\"");
        fields.put("brand", "\"MASTERCARD\"");
        fields.put("holderName", "\"Maria Silva\"");
        fields.put("lastFour", "\"3210\"");
        fields.put("expiryMonth", "11");
        fields.put("expiryYear", "2029");
        fields.put("color", "\"ROXO\"");
        fields.putAll(changes);
        return fields.entrySet().stream()
                .map(field -> "\"" + field.getKey() + "\": " + field.getValue())
                .collect(Collectors.joining(", ", "{", "}"));
    }

    @Test
    void withoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/cards"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void listPassesLoggedUserIdFromTokenToService() throws Exception {
        when(service.list(USER)).thenReturn(List.of(NUBANK));

        mockMvc.perform(get("/api/cards").with(loggedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Nubank"))
                .andExpect(jsonPath("$[0].brand").value("MASTERCARD"))
                .andExpect(jsonPath("$[0].holderName").value("Maria Silva"))
                .andExpect(jsonPath("$[0].lastFour").value("3210"))
                .andExpect(jsonPath("$[0].expiryMonth").value(11))
                .andExpect(jsonPath("$[0].expiryYear").value(2029))
                .andExpect(jsonPath("$[0].color").value("ROXO"));
    }

    // Cartão de antes da V12: só id e nome; o resto não vem preenchido.
    @Test
    void listShowsOldCardWithOnlyTheName() throws Exception {
        when(service.list(USER)).thenReturn(List.of(
                new CardResponse(2L, "Cartão antigo", null, null, null, null, null, null)));

        mockMvc.perform(get("/api/cards").with(loggedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Cartão antigo"))
                .andExpect(jsonPath("$[0].brand").doesNotExist())
                .andExpect(jsonPath("$[0].lastFour").doesNotExist());
    }

    @Test
    void createReturns201() throws Exception {
        CardRequest expected = new CardRequest("Nubank", CardBrand.MASTERCARD, "Maria Silva", "3210", 11, 2029,
                CardColor.ROXO);
        when(service.create(USER, expected)).thenReturn(NUBANK);

        mockMvc.perform(post("/api/cards").with(loggedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Nubank"))
                .andExpect(jsonPath("$.lastFour").value("3210"));
    }

    // Cada linha: campo, valor inválido (em JSON). O resto do corpo é válido,
    // então o erro só pode ser daquele campo.
    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource(quoteCharacter = '\'', textBlock = """
            name,        '"  "'
            name,        null
            brand,       null
            holderName,  '"  "'
            holderName,  null
            lastFour,    null
            lastFour,    '"123"'
            lastFour,    '"12345"'
            lastFour,    '"12a4"'
            lastFour,    '" 123"'
            lastFour,    '"4111111111111111"'
            expiryMonth, null
            expiryMonth, 0
            expiryMonth, 13
            expiryYear,  null
            expiryYear,  1999
            expiryYear,  2100
            expiryYear,  29
            color,       null
            """)
    void createWithInvalidFieldReturns400NamingTheField(String field, String jsonValue) throws Exception {
        mockMvc.perform(post("/api/cards").with(loggedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith(field, jsonValue)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Dados inválidos"))
                .andExpect(jsonPath("$.errors." + field).exists());

        verifyNoInteractions(service);
    }

    @Test
    void createWithTooLongNamesReturns400() throws Exception {
        mockMvc.perform(post("/api/cards").with(loggedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith(Map.of(
                                "name", "\"" + "x".repeat(51) + "\"",
                                "holderName", "\"" + "x".repeat(41) + "\""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.holderName").exists());

        verifyNoInteractions(service);
    }

    // Bandeira ou cor fora da lista: o JSON nem vira objeto Java (falha antes da validação).
    @Test
    void createWithUnknownBrandOrColorReturns400() throws Exception {
        mockMvc.perform(post("/api/cards").with(loggedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith("brand", "\"DINERS\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Corpo da requisição inválido"));
        mockMvc.perform(post("/api/cards").with(loggedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith("color", "\"VERDE\"")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    // O app antigo mandava só o nome. Agora todos os dados são obrigatórios.
    @Test
    void createWithOnlyTheNameReturns400ListingWhatIsMissing() throws Exception {
        mockMvc.perform(post("/api/cards").with(loggedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Nubank"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.brand").value("Escolha a bandeira"))
                .andExpect(jsonPath("$.errors.holderName").value("Informe o nome do titular"))
                .andExpect(jsonPath("$.errors.lastFour").value("Informe os 4 últimos dígitos"))
                .andExpect(jsonPath("$.errors.expiryMonth").value("Informe o mês da validade"))
                .andExpect(jsonPath("$.errors.expiryYear").value("Informe o ano da validade"))
                .andExpect(jsonPath("$.errors.color").value("Escolha a cor do cartão"))
                .andExpect(jsonPath("$.errors.name").doesNotExist());

        verifyNoInteractions(service);
    }

    @Test
    void updateReplacesTheCardAndReturnsIt() throws Exception {
        when(service.update(eq(USER), eq(1L), any())).thenReturn(NUBANK);

        mockMvc.perform(put("/api/cards/1").with(loggedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holderName").value("Maria Silva"));
    }

    // O PUT valida igual ao POST: não dá para "apagar" um dado mandando-o vazio.
    @Test
    void updateWithInvalidDataReturns400() throws Exception {
        mockMvc.perform(put("/api/cards/1").with(loggedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith("lastFour", "\"12\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.lastFour").value("Informe exatamente 4 dígitos"));

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
