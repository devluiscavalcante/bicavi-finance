package com.bicavi.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Só o nome pode ser alterado. Trocar o tipo de uma categoria que já tem
// transações deixaria receitas classificadas como despesas (e vice-versa).
public record UpdateCategoryRequest(
        @NotBlank @Size(max = 50) String name
) {
}
