package com.bicavi.common;

import com.bicavi.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// A rede de segurança do GlobalExceptionHandler: erro inesperado vira 500 genérico,
// e os erros que o Spring já sabe tratar (404, 405) continuam com o status certo.
@WebMvcTest(GlobalExceptionHandlerTest.BrokenController.class)
@Import({SecurityConfig.class, GlobalExceptionHandlerTest.BrokenController.class})
class GlobalExceptionHandlerTest {

    // Controller que só existe neste teste, para simular um bug de verdade.
    @RestController
    static class BrokenController {

        @GetMapping("/api/test/bug")
        String bug() {
            throw new IllegalStateException("detalhe interno: tabela X, linha 42");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor loggedUser() {
        return jwt().jwt(token -> token.subject("7"));
    }

    @Test
    void unexpectedErrorReturns500ProblemDetailWithoutInternalDetails() throws Exception {
        mockMvc.perform(get("/api/test/bug").with(loggedUser()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("Erro inesperado no servidor. Tente novamente."))
                // A mensagem da exceção fica no log, nunca na resposta.
                .andExpect(content().string(not(containsString("tabela X"))));
    }

    @Test
    void unknownRouteStays404() throws Exception {
        mockMvc.perform(get("/api/nao-existe").with(loggedUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void wrongHttpMethodStays405WithAllowHeader() throws Exception {
        mockMvc.perform(delete("/api/test/bug").with(loggedUser()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"));
    }
}
