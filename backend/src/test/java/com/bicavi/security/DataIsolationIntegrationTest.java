package com.bicavi.security;

import com.bicavi.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Teste de ponta a ponta contra IDOR (acessar dados de outro usuário pelo id).
// Sobe a aplicação inteira, com banco real (Testcontainers), cadastro e login
// de verdade e tokens assinados de verdade. Nada é mockado.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DataIsolationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // Relógio REAL da aplicação. As datas dos testes são relativas a "hoje":
    // datas fixas virariam uma bomba-relógio quando o mês fechasse (PeriodPolicy).
    @Autowired
    private Clock clock;

    private String today;  // "2026-10-07"
    private String month;  // "2026-10"
    private String aliceToken;
    private String bobToken;
    private long aliceCategoryId;
    private long aliceTransactionId;

    @BeforeEach
    void aliceHasDataAndBobExists() throws Exception {
        today = LocalDate.now(clock).toString();
        month = YearMonth.now(clock).toString();
        aliceToken = registerAndLogin("alice");
        bobToken = registerAndLogin("bob");

        aliceCategoryId = createAndGetId(aliceToken, "/api/categories", """
                {"name": "Mercado", "type": "EXPENSE"}
                """);
        aliceTransactionId = createAndGetId(aliceToken, "/api/transactions", """
                {"amount": 350.00, "type": "EXPENSE", "paymentMethod": "PIX", "categoryId": %d, "occurredOn": "%s"}
                """.formatted(aliceCategoryId, today));
    }

    @Test
    void bobCannotReadUpdateOrDeleteAlicesCategory() throws Exception {
        String path = "/api/categories/" + aliceCategoryId;

        // 404 e não 403: para o Bob, a categoria da Alice simplesmente não existe.
        mockMvc.perform(get(path).header("Authorization", bearer(bobToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(path).header("Authorization", bearer(bobToken))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name": "Hackeada"}
                                """))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(path).header("Authorization", bearer(bobToken)))
                .andExpect(status().isNotFound());

        // E a categoria da Alice continua intacta.
        mockMvc.perform(get(path).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mercado"));
    }

    @Test
    void bobCannotReadUpdateOrDeleteAlicesTransaction() throws Exception {
        String path = "/api/transactions/" + aliceTransactionId;

        mockMvc.perform(get(path).header("Authorization", bearer(bobToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(path).header("Authorization", bearer(bobToken))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"amount": 0.01, "type": "EXPENSE", "paymentMethod": "PIX", "occurredOn": "%s"}
                                """.formatted(today)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(path).header("Authorization", bearer(bobToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(path).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(350.00));
    }

    @Test
    void bobCannotUseAlicesCategoryInHisTransaction() throws Exception {
        mockMvc.perform(post("/api/transactions").header("Authorization", bearer(bobToken))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"amount": 10, "type": "EXPENSE", "paymentMethod": "PIX", "categoryId": %d, "occurredOn": "%s"}
                                """.formatted(aliceCategoryId, today)))
                .andExpect(status().isBadRequest())
                // Mesma mensagem de uma categoria inexistente: não confirma que ela existe.
                .andExpect(jsonPath("$.detail").value("Categoria " + aliceCategoryId + " não existe"));
    }

    @Test
    void listsAndReportsShowOnlyOwnData() throws Exception {
        mockMvc.perform(get("/api/categories").header("Authorization", bearer(bobToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/transactions").param("month", month).header("Authorization", bearer(bobToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/reports/monthly-summary").param("month", month).header("Authorization", bearer(bobToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExpense").value(0.00));

        mockMvc.perform(get("/api/reports/monthly-summary").param("month", month).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExpense").value(350.00));
    }

    @Test
    void bobCanHaveACategoryWithTheSameNameAsAlices() throws Exception {
        mockMvc.perform(post("/api/categories").header("Authorization", bearer(bobToken))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name": "Mercado", "type": "EXPENSE"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void protectedEndpointsRequireToken() throws Exception {
        mockMvc.perform(get("/api/categories")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/transactions").param("month", month)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reports/monthly-summary").param("month", month)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/categories/" + aliceCategoryId)).andExpect(status().isUnauthorized());
    }

    // ===== helpers =====

    // E-mail único por teste: o banco do container é compartilhado entre os testes desta classe.
    private String registerAndLogin(String name) throws Exception {
        String email = name + "-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "senha-forte-123", "name": "%s"}
                        """.formatted(email, name)))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "senha-forte-123"}
                        """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private long createAndGetId(String token, String path, String json) throws Exception {
        String body = mockMvc.perform(post(path).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
