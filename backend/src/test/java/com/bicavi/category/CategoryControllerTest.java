package com.bicavi.category;

import com.bicavi.common.ConflictException;
import com.bicavi.common.NotFoundException;
import com.bicavi.transaction.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @WebMvcTest sobe só a camada web (controller, validação, tratamento de erros).
// O service é substituído por um mock: aqui testamos HTTP, não regras de negócio.
@WebMvcTest(CategoryController.class)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService service;

    @Test
    void listReturns200WithJsonArray() throws Exception {
        when(service.list()).thenReturn(List.of(new CategoryResponse(1L, "Mercado", TransactionType.EXPENSE)));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Mercado"))
                .andExpect(jsonPath("$[0].type").value("EXPENSE"));
    }

    @Test
    void createReturns201() throws Exception {
        when(service.create(any())).thenReturn(new CategoryResponse(1L, "Mercado", TransactionType.EXPENSE));

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Mercado", "type": "EXPENSE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void createWithBlankNameReturns400AndDoesNotCallService() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "  ", "type": "EXPENSE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());

        verifyNoInteractions(service);
    }

    @Test
    void createWithUnknownTypeReturns400InProblemDetailFormat() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Mercado", "type": "OUTRO"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Corpo da requisição inválido"));

        verifyNoInteractions(service);
    }

    @Test
    void createWithDuplicateNameReturns409() throws Exception {
        when(service.create(any())).thenThrow(new ConflictException("Já existe"));

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Mercado", "type": "EXPENSE"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void getUnknownIdReturns404() throws Exception {
        when(service.get(99L)).thenThrow(new NotFoundException("Categoria 99 não encontrada"));

        mockMvc.perform(get("/api/categories/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Categoria 99 não encontrada"));
    }
}
